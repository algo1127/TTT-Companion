package com.ttt.companion.llm

import android.content.Context
import android.os.PowerManager
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
        lastProfile = null
    }

    data class ChatResult(
        val text: String,
        val stats: com.ttt.companion.model.PerformanceStats? = null
    )

    suspend fun chat(
        history: List<ChatMessage>,
        systemPrompt: String,
        characterId: String,
        userName: String = "User",
        forceCpu: Boolean = false
    ): ChatResult {
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
        val relevantMemory = vectorMemory.findRelevant(characterId, lastUserMessage)
        
        val prompt = buildString {
            append("<|im_start|>system\n")
            append(systemPrompt.trim())
            append("\n\n<user_info>\nYou are talking to $userName.\n</user_info>")
            
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
            
            // CONVERSATION HISTORY
            history.takeLast(10).forEach { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                append("<|im_start|>$role\n")
                append(msg.content.trim())
                append("\n<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
        
        Log.d("LlmService", "Prompt (Tools: ${detectedTools.joinToString()}): $prompt")

        val result = StringBuilder()
        val finishedDeferred = CompletableDeferred<ChatResult>()

        val chatJob = scope.launch {
            engine.events.collect { event ->
                when (event) {
                    is LlmEngine.Event.Ongoing -> {
                        result.append(event.word)
                    }
                    is LlmEngine.Event.Done -> {
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

                        // Strictly remove performance stats and thinking tags from the text content
                        val cleaned = finalResult
                            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
                            .replace(Regex("\\(\\d+ms\\)$"), "") // Remove the trailing (XXXXms)
                            .trim()
                        
                        if (cleaned.isEmpty() && finalResult.contains("</think>")) {
                            finishedDeferred.complete(ChatResult("... (I'm a bit lost, could you say that again?)", stats))
                        } else {
                            finishedDeferred.complete(ChatResult(cleaned, stats))
                        }
                    }
                    is LlmEngine.Event.Error -> finishedDeferred.completeExceptionally(Exception(event.message))
                    else -> {}
                }
            }
        }

        return try {
            val activity = context as? android.app.Activity
            activity?.window?.setSustainedPerformanceMode(true)
            
            engine.predict(prompt)
            val response = finishedDeferred.await()
            
            activity?.window?.setSustainedPerformanceMode(false)
            response
        } finally {
            chatJob.cancel()
        }
    }
}
