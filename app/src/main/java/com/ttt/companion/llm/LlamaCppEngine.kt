package com.ttt.companion.llm

import android.content.ContentResolver
import com.ttt.companion.model.CharacterProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

class LlamaCppEngine(contentResolver: ContentResolver) : LlmEngine {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _rawEvents = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    override val events = _rawEvents.map { event ->
        when (event) {
            is LlamaHelper.LLMEvent.Loaded -> LlmEngine.Event.Loaded
            is LlamaHelper.LLMEvent.Started -> LlmEngine.Event.Started
            is LlamaHelper.LLMEvent.Ongoing -> LlmEngine.Event.Ongoing(event.word)
            is LlamaHelper.LLMEvent.Done -> {
                val tps = if (event.duration > 0) (event.tokenCount.toFloat() / (event.duration.toFloat() / 1000f)) else 0f
                LlmEngine.Event.Done(LlmEngine.Metrics(
                    totalTime = event.duration,
                    tokenCount = event.tokenCount,
                    tokensPerSec = tps
                ))
            }
            is LlamaHelper.LLMEvent.Error -> LlmEngine.Event.Error(event.message)
        }
    }

    override val engineName: String = "Llama.cpp"
    override val computeUnit: String = "CPU"

    private val helper = LlamaHelper(contentResolver, scope, _rawEvents)
    private var loadedModelPath: String? = null

    override suspend fun loadModel(profile: CharacterProfile, contextSize: Int): LlmService.LoadState {
        if (loadedModelPath == profile.modelPath) {
            return LlmService.LoadState.Ready
        }

        return try {
            val modelFile = File(profile.modelPath)
            if (!modelFile.exists()) return LlmService.LoadState.Error("Model file not found")
            
            val modelUri = "file://${modelFile.absolutePath}"
            val deferred = CompletableDeferred<LlmService.LoadState>()

            val job = scope.launch {
                _rawEvents.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Loaded -> {
                            loadedModelPath = profile.modelPath
                            deferred.complete(LlmService.LoadState.Ready)
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            deferred.complete(LlmService.LoadState.Error(event.message))
                        }
                        else -> {}
                    }
                }
            }

            withContext(Dispatchers.IO) {
                helper.load(path = modelUri, contextLength = contextSize) {}
            }

            val result = deferred.await()
            job.cancel()
            result
        } catch (e: Exception) {
            LlmService.LoadState.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun predict(
        prompt: String,
        tempOverride: Float?,
        stopWords: List<String>
    ) {
        // llama.cpp simple wrapper might not support mid-stream stop/temp change easily
        // but we'll pass the logic through if the underlying library supports it.
        helper.predict(prompt = prompt)
    }

    override fun unload() {
        helper.release()
        loadedModelPath = null
    }

    override suspend fun stop() {
        helper.stopPrediction()
    }
}
