package com.ttt.companion.memory

import android.content.Context
import android.util.Log
import com.ttt.companion.llm.LlamaCppEngine
import com.ttt.companion.llm.LlmEngine
import com.ttt.companion.llm.LlmService
import com.ttt.companion.llm.ModelConfig
import com.ttt.companion.llm.ModelDownloader
import com.ttt.companion.model.CharacterProfile
import com.ttt.companion.model.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The specialist service that runs a small 0.5B model on the CPU
 * to organize, clean, and format memories.
 */
class MemoryArchitect(private val context: Context) {
    private val TAG = "MemoryArchitect"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val engine = LlamaCppEngine(context.contentResolver)
    private val downloader = ModelDownloader(context)
    
    private var isLoaded = false

    private val architectPrompt = """
        <|im_start|>system
        You are a memory architect. Your job is to extract concise, atomic facts from the conversation.
        RULES:
        1. Extract only permanent facts (preferences, names, locations, relationships).
        2. Remove all conversational filler, jokes, or sarcasm.
        3. Remove the user's name if mentioned; use "User" instead.
        4. Format each fact inside <fact></fact> XML tags.
        5. If a new fact contradicts an old one, output it clearly.
        6. Output ONLY the XML tags. No preamble.
        <|im_end|>
    """.trimIndent()

    suspend fun process(history: List<ChatMessage>): List<String> = withContext(Dispatchers.IO) {
        if (!ensureLoaded()) return@withContext emptyList()

        val prompt = buildString {
            append(architectPrompt)
            history.takeLast(10).forEach { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                append("<|im_start|>$role\n${msg.content}\n<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }

        val result = runInference(prompt)
        parseFacts(result)
    }

    suspend fun formatManualMemory(text: String): String = withContext(Dispatchers.IO) {
        if (!ensureLoaded()) return@withContext text

        val prompt = """
            $architectPrompt
            <|im_start|>user
            Format this manual memory entry properly: $text
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()

        val result = runInference(prompt)
        val facts = parseFacts(result)
        facts.firstOrNull() ?: text
    }

    private suspend fun ensureLoaded(): Boolean {
        if (isLoaded) return true
        
        val variant = ModelConfig.LLM_VARIANTS.find { it.isSpecialist } ?: return false
        val modelFile = downloader.getModelFile(variant)
        if (!modelFile.exists()) return false

        val profile = CharacterProfile(
            id = "architect",
            name = "Architect",
            systemPrompt = architectPrompt,
            modelPath = modelFile.absolutePath,
            maxTokens = 256,
            temperature = 0.1f // Very low for deterministic formatting
        )

        val state = engine.loadModel(profile, 1024)
        isLoaded = state is LlmService.LoadState.Ready
        return isLoaded
    }

    private suspend fun runInference(prompt: String): String {
        android.util.Log.d(TAG, "Architect Prompt: $prompt")
        val result = StringBuilder()
        val deferred = CompletableDeferred<String>()
        
        val job = scope.launch {
            engine.events.collect { event ->
                when (event) {
                    is LlmEngine.Event.Ongoing -> result.append(event.word)
                    is LlmEngine.Event.Done -> deferred.complete(result.toString())
                    is LlmEngine.Event.Error -> deferred.completeExceptionally(Exception(event.message))
                    else -> {}
                }
            }
        }

        try {
            engine.predict(prompt, stopWords = listOf("<|im_end|>", "</fact>\n\n"))
            return deferred.await()
        } finally {
            job.cancel()
        }
    }

    private fun parseFacts(text: String): List<String> {
        val regex = Regex("<fact>(.*?)</fact>", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(text).map { it.groupValues[1].trim() }.toList()
    }
    
    fun unload() {
        engine.unload()
        isLoaded = false
    }
}
