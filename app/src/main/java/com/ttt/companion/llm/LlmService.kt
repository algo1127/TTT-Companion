package com.ttt.companion.llm

import android.content.Context
import android.util.Log
import com.ttt.companion.model.CharacterProfile
import com.ttt.companion.model.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LlmService(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var engine: LlmEngine
    private val vectorMemory = com.ttt.companion.memory.VectorMemoryManager(context)

    val engineName: String get() = engine.engineName
    val computeUnit: String get() = engine.computeUnit

    private var lastProfile: CharacterProfile? = null
    private var lastContextSize: Int = 2048

    init {
        engine = getEngine(forceCpu = false)
    }

    private fun getEngine(forceCpu: Boolean): LlmEngine {
        val isSnapdragon = DeviceUtils.isSnapdragonDevice()
        val isGenieXAvailable = DeviceUtils.isGenieXAvailable()

        return if (isSnapdragon && isGenieXAvailable && !forceCpu) {
            Log.i("LlmService", "Initializing Snapdragon GenieX Engine")
            GenieXEngine(context)
        } else {
            Log.i("LlmService", "Initializing Standard Llama.cpp Engine (Forced CPU: $forceCpu)")
            LlamaCppEngine(context.contentResolver)
        }
    }

    sealed class LoadState {
        data object Idle    : LoadState()
        data object Loading : LoadState()
        data object Ready   : LoadState()
        data class Error(val message: String) : LoadState()
    }

    suspend fun loadModel(profile: CharacterProfile, contextSize: Int = 2048): LoadState {
        Log.d("LlmService", "Loading model via engine: ${engine::class.simpleName}")
        lastProfile = profile
        lastContextSize = contextSize
        return engine.loadModel(profile, contextSize)
    }

    fun unload() {
        engine.unload()
        // We keep lastProfile so we can reload it after the Architect finishes
    }

    suspend fun reloadLastModel(): LoadState {
        val profile = lastProfile ?: return LoadState.Idle
        Log.i("LlmService", "Reloading last model: ${profile.name}")
        return engine.loadModel(profile, lastContextSize)
    }

    fun stop() {
        scope.launch {
            engine.stop()
        }
    }

    data class ChatResult(
        val text: String,
        val stats: com.ttt.companion.model.PerformanceStats? = null,
        val sentenceCount: Int = 0
    )

    enum class NudgeType { NO_THINK, DONE }

    suspend fun chat(
        history: List<ChatMessage>,
        systemPrompt: String,
        characterId: String,
        userName: String = "User",
        forceCpu: Boolean = false,
        forceReasoning: Boolean = false,
        reasoningThreshold: Int = 150,
        nudgeType: NudgeType = NudgeType.NO_THINK,
        useOfficialNudge: Boolean = false,
        onSentenceComplete: (suspend (String) -> Unit)? = null
    ): ChatResult {
        // Long-Term Memory Retrieval
        val memoryEnabled = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)
            .getBoolean("long_term_memory_enabled", false)
        
        val relevantMemory = if (memoryEnabled) {
            vectorMemory.findRelevant(characterId, history.lastOrNull { it.role == "user" }?.content ?: "")
        } else ""

        // Engine Swapping Logic
        val currentIsCpu = engine is LlamaCppEngine
        if (forceCpu && !currentIsCpu) {
            Log.i("LlmService", "Swapping to CPU engine for background task...")
            engine.unload()
            engine = getEngine(forceCpu = true)
            lastProfile?.let { engine.loadModel(it, lastContextSize) }
        } else if (!forceCpu && currentIsCpu && DeviceUtils.isSnapdragonDevice() && DeviceUtils.isGenieXAvailable()) {
            Log.i("LlmService", "Swapping back to GPU engine for active chat...")
            engine.unload()
            engine = getEngine(forceCpu = false)
            lastProfile?.let { engine.loadModel(it, lastContextSize) }
        }

        // Dynamic Tool Injection
        val lastUserMessage = history.lastOrNull { it.role == "user" }?.content ?: ""
        val detectedTools = com.ttt.companion.tools.IntentClassifier.classify(lastUserMessage)
        val dynamicTools = com.ttt.companion.tools.ToolDefinitions.getDynamicPrompt(detectedTools)
        
        // Vector Memory Retrieval
        // Note: Already retrieved at the start of chat() and stored in relevantMemory
        
        val prompt = buildString {
            // 1. CHAT HISTORY (Moving up to stabilize core prompt cache)
            val historySubset = history.takeLast(10)
            historySubset.forEachIndexed { index, msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                var cleanedContent = msg.content
                    .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                    .trim()
                
                // If official nudge is enabled, append keyword to the LAST user message
                if (useOfficialNudge && index == historySubset.size - 1 && msg.role == "user") {
                    cleanedContent += if (forceReasoning) " /think" else " /no_think"
                }

                append("<|im_start|>$role\n")
                append(cleanedContent)
                append("\n<|im_end|>\n")
            }

            // 2. SYSTEM CORE
            append("<|im_start|>system\n")
            append(systemPrompt.trim())

            append("\n\n<user_info>\nYou are talking to $userName. Address them by this name. Never call them 'human', 'user', or 'mortal'.\n</user_info>")
            
            // XML Tagging for strict context isolation
            if (relevantMemory.isNotEmpty()) {
                append("\n<memory_recall>\n")
                append(relevantMemory.trim())
                append("\n</memory_recall>")
            }
            if (dynamicTools.isNotEmpty()) {
                append("\n<available_tools>\n")
                append(dynamicTools.trim())
                append("\n</available_tools>")
            }
            
            append("\n<|im_end|>\n")
            
            // 3. GENERATION START
            append("<|im_start|>assistant\n")
            if (forceReasoning) {
                // Let the model decide, or force it if it's a reasoning model
                append("<think>")
            } else if (lastProfile?.skipThinking != true && !useOfficialNudge) {
                // Only pre-fill "no-think" if the model actually HAS a thinking mode
                val nudge = if (nudgeType == NudgeType.NO_THINK) "/no_think" else "Done."
                append("<think>\n$nudge</think>\n")
            }
        }
        
        Log.d("LlmService", "Prompt (Tools: ${detectedTools.joinToString()}): $prompt")

        val result = StringBuilder()
        var currentThinkingState = false
        if (forceReasoning) {
            result.append("<think>")
            currentThinkingState = true
        } else if (lastProfile?.skipThinking != true && !useOfficialNudge) {
            val nudge = if (nudgeType == NudgeType.NO_THINK) "/no_think" else "Done."
            result.append("<think>\n$nudge</think>\n")
            currentThinkingState = false
        }
        
        val finishedDeferred = CompletableDeferred<ChatResult>()

        val chatJob = scope.launch {
            var tokenCount = 0
            var inThinkingBlock = currentThinkingState
            var watchdogTriggered = false
            var currentSentence = StringBuilder()
            var totalSentences = 0

            engine.events.collect { event ->
                when (event) {
                    is LlmEngine.Event.Ongoing -> {
                        tokenCount++
                        result.append(event.word)
                        currentSentence.append(event.word)
                        
                        // Check for sentence completion (Pipelining)
                        if (onSentenceComplete != null && !inThinkingBlock) {
                            val word = event.word
                            if (word.contains(".") || word.contains("!") || word.contains("?")) {
                                val sentence = currentSentence.toString().trim()
                                if (sentence.isNotEmpty()) {
                                    totalSentences++
                                    // SEQUENTIAL: Call directly (since onSentenceComplete is suspend)
                                    // to preserve order. The implementer (MainViewModel) must 
                                    // handle backgrounding the actual synthesis.
                                    onSentenceComplete(sentence)
                                    currentSentence = StringBuilder()
                                }
                            }
                        }

                        if (event.word.contains("<think>")) {
                            inThinkingBlock = true
                        }

                        // Reasoning Watchdog (The "Bouncer")
                        if (inThinkingBlock && !watchdogTriggered && tokenCount > reasoningThreshold) {
                            Log.w("LlmService", "Reasoning threshold reached ($reasoningThreshold). Forcing Cool Down...")
                            watchdogTriggered = true
                            engine.stop()
                        }
                        
                        if (event.word.contains("</think>")) {
                            inThinkingBlock = false
                        }
                    }
                    is LlmEngine.Event.Done -> {
                        if (watchdogTriggered) return@collect
                        
                        val finalResult = result.toString()
                        val stats = event.metrics?.let {
                            com.ttt.companion.model.PerformanceStats(
                                ttft = it.ttft,
                                totalTime = it.totalTime,
                                tokenCount = it.tokenCount,
                                tokensPerSec = it.tokensPerSec,
                                engine = engineName,
                                backend = computeUnit
                            )
                        }

                        val cleaned = finalResult
                            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("\\(\\d+ms\\)$"), "")
                            .trim()
                        
                        if (cleaned.isEmpty() && finalResult.contains("</think>")) {
                            finishedDeferred.complete(ChatResult("... (I'm a bit lost, could you say that again?)", stats, 0))
                        } else {
                            finishedDeferred.complete(ChatResult(cleaned, stats, totalSentences))
                        }
                    }
                    is LlmEngine.Event.Error -> {
                        if (!watchdogTriggered) {
                            finishedDeferred.completeExceptionally(Exception(event.message))
                        }
                    }
                    else -> {}
                }
            }
        }

        return try {
            val activity = context as? android.app.Activity
            activity?.window?.setSustainedPerformanceMode(true)
            
            val useCache = context.getSharedPreferences("experimental_prefs", Context.MODE_PRIVATE)
                .getBoolean("use_system_prompt_cache", true)

            val stopWords = mutableListOf("<|im_end|>", "<|endoftext|>", "###")
            if (!forceReasoning) {
                stopWords.add("<think>")
            }
            
            engine.predict(
                prompt = prompt,
                stopWords = stopWords,
                useCache = useCache
            )
            
            val initialResult = finishedDeferred.await()
            activity?.window?.setSustainedPerformanceMode(false)
            initialResult
        } catch (e: Exception) {
            if (result.contains("<think>") && !result.contains("</think>")) {
                 resumeAfterWatchdog(result)
            } else {
                 throw e
            }
        } finally {
            chatJob.cancel()
        }
    }

    private suspend fun resumeAfterWatchdog(
        partialResult: StringBuilder, 
    ): ChatResult {
        Log.i("LlmService", "Resuming after watchdog interruption...")
        val resumedPrompt = partialResult.toString() + "\n... wrap up. </think>\n"
        val result = StringBuilder()
        val finishedDeferred = CompletableDeferred<ChatResult>()
        
        val chatJob = scope.launch {
            engine.events.collect { event ->
                if (event is LlmEngine.Event.Ongoing) result.append(event.word)
                if (event is LlmEngine.Event.Done) {
                    val final = partialResult.toString() + "\n... wrap up. </think>\n" + result.toString()
                    val cleaned = final.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
                    finishedDeferred.complete(ChatResult(cleaned))
                }
            }
        }
        
        engine.predict(resumedPrompt, tempOverride = 0.1f)
        val finalResult = finishedDeferred.await()
        chatJob.cancel()
        return finalResult
    }
}
