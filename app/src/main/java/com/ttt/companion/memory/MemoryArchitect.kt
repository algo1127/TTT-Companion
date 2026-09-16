package com.ttt.companion.memory

import android.content.Context
import com.ttt.companion.llm.ModelConfig
import com.ttt.companion.llm.ModelDownloader
import com.ttt.companion.model.CharacterProfile
import com.ttt.companion.model.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

/**
 * The specialist service using the raw LlamaHelper library directly.
 * Designed to be loaded and unloaded strictly in sequence with the main model.
 */
class MemoryArchitect(private val context: Context) {
    private val TAG = "MemoryArchitect"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _rawEvents = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
    private val helper = LlamaHelper(context.contentResolver, scope, _rawEvents)
    private val downloader = ModelDownloader(context)
    
    private var isLoaded = false

    private val architectPrompt = "Instructions: Extract a short, declarative fact from the input. Rewrite it as 'User [fact]'. Remove questions, greetings, and conversational filler. No names. No extra text. No quotes.\nExample Input: I love blue cars.\nExample Output: User likes blue cars.\nExample Input: Did you know I live in New York City?\nExample Output: User lives in New York City.\n\n"

    suspend fun process(history: List<ChatMessage>): List<String> = withContext(Dispatchers.IO) {
        if (!ensureLoaded()) return@withContext emptyList()

        val chatBlock = history.takeLast(6).joinToString("\n") { msg ->
            "${if (msg.role == "user") "Input" else "Context"}: ${msg.content}"
        }

        val prompt = "${architectPrompt}Input: $chatBlock\nOutput: User "
        val raw = runInference(prompt)
        val result = if (raw.startsWith("User ")) raw else "User $raw"
        
        unload()
        listOf(result.trim())
    }

    suspend fun formatManualMemory(text: String): String = withContext(Dispatchers.IO) {
        if (!ensureLoaded()) return@withContext text

        val prompt = "${architectPrompt}Input: $text\nOutput: User "
        val raw = runInference(prompt)
        val result = if (raw.startsWith("User ")) raw else "User $raw"
        
        unload()
        result.trim()
    }

    private suspend fun ensureLoaded(): Boolean {
        if (isLoaded) return true
        
        val variant = ModelConfig.LLM_VARIANTS.find { it.isSpecialist } ?: return false
        val modelFile = downloader.getModelFile(variant)
        if (!modelFile.exists()) return false

        val deferred = CompletableDeferred<Boolean>()
        val job = scope.launch {
            _rawEvents.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Loaded -> deferred.complete(true)
                    is LlamaHelper.LLMEvent.Error -> {
                        android.util.Log.e(TAG, "Architect Load Error: ${event.message}")
                        deferred.complete(false)
                    }
                    else -> {}
                }
            }
        }

        withContext(Dispatchers.IO) {
            helper.load(
                path = "file://${modelFile.absolutePath}", 
                contextLength = 512
            ) {}
        }

        isLoaded = deferred.await()
        job.cancel()
        return isLoaded
    }

    private suspend fun runInference(prompt: String): String {
        val result = StringBuilder()
        val deferred = CompletableDeferred<String>()
        val stops = listOf("\n", "Input:", "Instructions:", "<|", "</fact>")
        
        val job = scope.launch {
            _rawEvents.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        result.append(event.word)
                        if (stops.any { result.contains(it) }) {
                            helper.stopPrediction()
                        }
                    }
                    is LlamaHelper.LLMEvent.Done -> deferred.complete(result.toString())
                    is LlamaHelper.LLMEvent.Error -> deferred.completeExceptionally(Exception(event.message))
                    else -> {}
                }
            }
        }

        try {
            helper.predict(prompt)
            val final = deferred.await()
            android.util.Log.d("MemoryArchitect", "Architect Raw Output: $final")
            
            // Clean up common 0.5B artifacts and strip tags/quotes if they leaked in
            return final.split("\n")[0]
                .replace(Regex("<fact>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</fact>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\\d+\\.\\s*"), "") // Strip "1. "
                .replace(Regex("(?i)^fact:\\s*"), "") // Strip "Fact: "
                .replace("\"", "") // Strip double quotes
                .replace("'", "")  // Strip single quotes
                .trim()
        } finally {
            job.cancel()
        }
    }

    private fun parseFacts(text: String): List<String> {
        // Since runInference now returns the cleaned fact directly, 
        // we just wrap it in a list.
        return if (text.isNotBlank()) listOf(text) else emptyList()
    }

    fun unload() {
        helper.release()
        isLoaded = false
    }
}
