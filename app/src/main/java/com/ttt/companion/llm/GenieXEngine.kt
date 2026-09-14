package com.ttt.companion.llm

import android.content.Context
import android.util.Log
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.SamplerConfig
import com.ttt.companion.model.CharacterProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class GenieXEngine(private val context: Context) : LlmEngine {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _events = MutableSharedFlow<LlmEngine.Event>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    override val engineName: String = "GenieX"
    override val computeUnit: String 
        get() = "GPU" // Currently forced to GPU in createInput

    private var llmWrapper: LlmWrapper? = null
    private var loadedModelPath: String? = null
    private var currentProfile: CharacterProfile? = null

    override suspend fun loadModel(profile: CharacterProfile, contextSize: Int): LlmService.LoadState {
        if (loadedModelPath == profile.modelPath) {
            return LlmService.LoadState.Ready
        }

        unload()

        return try {
            // Initialize SDK if not already done
            val initDeferred = CompletableDeferred<Boolean>()
            GenieXSdk.getInstance().init(context, object : GenieXSdk.InitCallback {
                override fun onSuccess() { initDeferred.complete(true) }
                override fun onFailure(reason: String) { initDeferred.completeExceptionally(Exception(reason)) }
            })
            initDeferred.await()

            val modelFile = File(profile.modelPath)
            if (!modelFile.exists()) return LlmService.LoadState.Error("Model file not found")

            val modelConfig = ModelConfig(
                nCtx = contextSize,
                nGpuLayers = -1 
            )
            val createInput = LlmCreateInput(
                model_path = modelFile.absolutePath,
                config = modelConfig,
                runtime_id = "llama_cpp",
                compute_unit = "gpu" // Use Adreno GPU for better stability with GGUF
            )

            val result = LlmWrapper.builder()
                .llmCreateInput(createInput)
                .build()

            val wrapper = result.getOrNull() ?: return LlmService.LoadState.Error("Failed to initialize GenieX")
            
            llmWrapper = wrapper
            loadedModelPath = profile.modelPath
            currentProfile = profile
            
            _events.emit(LlmEngine.Event.Loaded)
            LlmService.LoadState.Ready
        } catch (e: Exception) {
            Log.e("GenieXEngine", "Load error", e)
            LlmService.LoadState.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun predict(
        prompt: String,
        tempOverride: Float?,
        stopWords: List<String>
    ) {
        val wrapper = llmWrapper ?: throw Exception("GenieX not initialized")
        val profile = currentProfile ?: throw Exception("Profile not set")

        val samplerConfig = SamplerConfig(
            temperature = tempOverride ?: profile.temperature,
            repetitionPenalty = 1.1f, // Standard penalty
            presencePenalty = profile.presencePenalty,
            topP = 0.95f
        )
        
        val genConfig = GenerationConfig(
            maxTokens = profile.maxTokens,
            samplerConfig = samplerConfig,
            stopWords = if (stopWords.isNotEmpty()) stopWords.toTypedArray() else null,
            stopCount = stopWords.size
        )

        scope.launch {
            try {
                _events.emit(LlmEngine.Event.Started)
                wrapper.generateStreamFlow(prompt, genConfig).collect { result ->
                    when (result) {
                        is LlmStreamResult.Token -> {
                            // Log.d("GenieXEngine", "Token: ${result.text}")
                            _events.emit(LlmEngine.Event.Ongoing(result.text))
                        }
                        is LlmStreamResult.Completed -> {
                            Log.d("GenieXEngine", "Prediction completed")
                            val metrics = LlmEngine.Metrics(
                                ttft = result.profile.ttftMs.toLong(),
                                totalTime = (result.profile.promptTimeMs + result.profile.decodeTimeMs).toLong(),
                                tokenCount = result.profile.generatedTokens.toInt(),
                                tokensPerSec = result.profile.decodingSpeed.toFloat()
                            )
                            _events.emit(LlmEngine.Event.Done(metrics))
                        }
                        is LlmStreamResult.Error -> {
                            _events.emit(LlmEngine.Event.Error(result.throwable.message ?: "Unknown error"))
                        }
                    }
                }
            } catch (e: Exception) {
                _events.emit(LlmEngine.Event.Error(e.message ?: "Prediction error"))
            }
        }
    }

    override fun unload() {
        llmWrapper?.destroy()
        llmWrapper = null
        loadedModelPath = null
        currentProfile = null
    }

    override suspend fun stop() {
        llmWrapper?.stopStream()
    }
}
