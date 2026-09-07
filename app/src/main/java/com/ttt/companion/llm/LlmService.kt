package com.ttt.companion.llm

import android.content.Context
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        Log.d("LlmService", "Attempting to load model from: ${profile.modelPath} (ctx=$contextSize)")
        if (loadedModelPath == profile.modelPath) {
            Log.d("LlmService", "Model already loaded at this path.")
            // However, if the context size changed, we might want to reload. 
            // For now, let's just return Ready to be safe.
            return LoadState.Ready
        }

        return try {
            val modelFile = File(profile.modelPath)
            if (!modelFile.exists()) return LoadState.Error("Model file not found")
            if (modelFile.length() < 100_000_000L) {
                modelFile.delete()
                return LoadState.Error("Model file corrupted")
            }

            val modelUri = "file://${modelFile.absolutePath}"
            val deferred = CompletableDeferred<LoadState>()

            val job = scope.launch {
                llmFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Loaded -> {
                            loadedModelPath = profile.modelPath
                            deferred.complete(LoadState.Ready)
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            if (event.message.contains("GGUF", ignoreCase = true)) modelFile.delete()
                            deferred.complete(LoadState.Error(event.message))
                        }
                        else -> {}
                    }
                }
            }

            withContext(Dispatchers.IO) {
                helper.load(path = modelUri, contextLength = contextSize) {}
            }

            val result = withTimeoutOrNull(999_000) { deferred.await() } ?: LoadState.Error("Model loading timed out")
            job.cancel()
            result
        } catch (e: Exception) {
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
        if (loadedModelPath == null) throw Exception("Model was not loaded yet")

        val prompt = buildString {
            append("<|im_start|>system\n")
            append(systemPrompt.trim())
            append("\n<|im_end|>\n")
            history.takeLast(10).forEach { msg ->
                val role = if (msg.role == "user") "user" else "assistant"
                append("<|im_start|>$role\n")
                append(msg.content.trim())
                append("\n<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }

        val result = StringBuilder()
        try {
            helper.predict(prompt = prompt)
            val finishedDeferred = CompletableDeferred<Unit>()
            val chatJob = scope.launch {
                llmFlow.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> result.append(event.word)
                        is LlamaHelper.LLMEvent.Done -> finishedDeferred.complete(Unit)
                        is LlamaHelper.LLMEvent.Error -> finishedDeferred.completeExceptionally(Exception(event.message))
                        else -> {}
                    }
                }
            }
            finishedDeferred.await()
            chatJob.cancel()
        } catch (e: Exception) { throw e }

        return result.toString()
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }
}
