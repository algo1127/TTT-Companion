package com.ttt.companion.llm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ttt.companion.model.CharacterProfile
import com.ttt.companion.model.ChatMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

class LlmService(context: Context) {

    companion object {
        const val RESPONSE_RESERVE = 512
        // Heuristic: 1 token is roughly 4 characters
        private const val CHARS_PER_TOKEN = 4
    }

    private var activeContextSize: Int = 2048
    private val maxPromptChars: Int get() = (activeContextSize - RESPONSE_RESERVE) * CHARS_PER_TOKEN

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Using replay = 1 to ensure we don't miss the Loaded event if it happens very fast
    val llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val helper = LlamaHelper(context.contentResolver, scope, llmFlow)
    private var loadedModelPath: String? = null

    sealed class LoadState {
        data object Idle    : LoadState()
        data object Loading : LoadState()
        data object Ready   : LoadState()
        data class Error(val message: String) : LoadState()
    }

    suspend fun loadModel(profile: CharacterProfile, contextSize: Int = 2048): LoadState {
        Log.d("LlmService", "Attempting to load model from: ${profile.modelPath}")
        if (loadedModelPath == profile.modelPath) {
            Log.d("LlmService", "Model already loaded at this path.")
            return LoadState.Ready
        }

        return try {
            val modelFile = File(profile.modelPath)
            if (!modelFile.exists()) {
                Log.e("LlmService", "Model file does not exist at ${profile.modelPath}")
                return LoadState.Error("Model file not found")
            }

            // A 2B model Q4_K_M should be ~1.5GB. If it's less than 100MB, it's definitely corrupted/LFS pointer.
            if (modelFile.length() < 100_000_000L) {
                Log.e("LlmService", "Model file is suspiciously small (${modelFile.length()} bytes). Deleting.")
                modelFile.delete()
                return LoadState.Error("Model file corrupted (too small)")
            }

            // Critical Fix: Explicitly ensure the URI is exactly what the library expects
            // "file://" + path is the standard way to pass local file URIs to ContentResolver
            val modelUri = "file://${modelFile.absolutePath}"

            val deferred = CompletableDeferred<LoadState>()

            val job = scope.launch {
                llmFlow.collect { event ->
                    Log.d("LlmService", "Received LLM Event: $event")
                    when (event) {
                        is LlamaHelper.LLMEvent.Loaded -> {
                            Log.i("LlmService", "Model LOADED event received")
                            loadedModelPath = profile.modelPath
                            deferred.complete(LoadState.Ready)
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            Log.e("LlmService", "Model error event: ${event.message}")
                            if (event.message.contains("GGUF", ignoreCase = true)) {
                                Log.w("LlmService", "GGUF format error. Deleting model file.")
                                File(profile.modelPath).delete()
                            }
                            deferred.complete(LoadState.Error(event.message))
                        }
                        else -> {}
                    }
                }
            }

            Log.d("LlmService", "Calling helper.load with URI: $modelUri (ctx=$contextSize)")
            activeContextSize = contextSize
            withContext(Dispatchers.IO) {
                // Total context length assigned to the engine
                helper.load(
                    path          = modelUri,
                    contextLength = contextSize
                ) { /* callback is also called but we use flow */ }
            }

            // Wait for the LOADED event - giving it 3 minutes for the 1.5GB model
            val result = withTimeoutOrNull(999_000) {
                deferred.await()
            } ?: LoadState.Error("Model loading timed out after 180s")

            job.cancel()
            Log.d("LlmService", "Load result: $result")
            result
        } catch (e: Exception) {
            Log.e("LlmService", "Exception during loadModel", e)
            LoadState.Error(e.message ?: "Unknown error")
        }
    }

    fun unload() {
        helper.release()
        loadedModelPath = null
    }

    suspend fun chat(
        history: List<ChatMessage>,
        systemPrompt: String
    ): String {
        if (loadedModelPath == null) {
            Log.e("LlmService", "chat() called but model not loaded!")
            throw Exception("Model was not loaded yet")
        }

        // --- Context Overflow Protection ---
        // We use character-based heuristic to ensure the prompt doesn't exceed 
        // the available space in the context window.
        
        var historyToInclude = history.takeLast(10)
        
        fun buildPrompt(hist: List<ChatMessage>): String = buildString {
            append("<|im_start|>system\n")
            append(systemPrompt.trim())
            append("\n<|im_end|>\n")

            hist.forEach { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                append("<|im_start|>$role\n")
                append(msg.content.trim())
                append("\n<|im_end|>\n")
            }

            append("<|im_start|>assistant\n")
        }

        var prompt = buildPrompt(historyToInclude)
        
        // If too long, aggressively trim history messages
        while (prompt.length > maxPromptChars && historyToInclude.size > 1) {
            historyToInclude = historyToInclude.drop(1)
            prompt = buildPrompt(historyToInclude)
            Log.d("LlmService", "Trimming history to prevent overflow. Remaining: ${historyToInclude.size}")
        }

        // Final safety check: if still too long (e.g. system prompt is massive), 
        // we must truncate the prompt itself or it WILL crash the native engine.
        if (prompt.length > maxPromptChars) {
            Log.w("LlmService", "Prompt still exceeds safety limit after trimming history. Hard truncating.")
            prompt = prompt.takeLast(maxPromptChars)
        }

        Log.d("LlmService", "Starting prediction with prompt length: ${prompt.length} chars")
        val result = StringBuilder()

        try {
            helper.predict(prompt = prompt)

            val finishedDeferred = CompletableDeferred<Unit>()
            val chatJob = scope.launch {
                llmFlow.collect { event ->
                    Log.v("LlmService", "Prediction event: $event")
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> result.append(event.word)
                        is LlamaHelper.LLMEvent.Done -> finishedDeferred.complete(Unit)
                        is LlamaHelper.LLMEvent.Error -> finishedDeferred.completeExceptionally(Exception("Inference error: ${event.message}"))
                        else -> {}
                    }
                }
            }
            finishedDeferred.await()
            chatJob.cancel()
        } catch (e: Exception) {
            Log.e("LlmService", "Exception during chat prediction", e)
            throw e
        }

        val finalResponse = result.toString()
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
        Log.i("LlmService", "Prediction complete. Response: $finalResponse")
        return finalResponse
    }
}