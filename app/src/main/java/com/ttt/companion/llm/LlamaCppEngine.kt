package com.ttt.companion.llm

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
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

class LlamaCppEngine(private val contentResolver: ContentResolver, private val downloader: ModelDownloader) : LlmEngine {
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
            
            // Resolve mmproj path if available in ModelConfig
            val variant = ModelConfig.LLM_VARIANTS.find { it.subDir in profile.modelPath }
            val mmprojFile = if (variant != null) downloader.getMmprojFile(variant) else null
            val mmprojPath = if (mmprojFile != null && mmprojFile.exists()) "file://${mmprojFile.absolutePath}" else null
            
            if (mmprojPath != null) {
                Log.i("LlamaCppEngine", "Loading multimodal model with mmproj: $mmprojPath")
            }

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
                helper.load(
                    path = modelUri, 
                    contextLength = contextSize,
                    mmprojPath = mmprojPath
                ) {}
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
        stopWords: List<String>,
        useCache: Boolean,
        imagePath: String?,
        maxImageDim: Int
    ) {
        // Resolve content URI to actual file and ensure it has a proper file:// scheme
        // for the ContentResolver inside LlamaHelper.
        val resolvedPath = if (imagePath?.startsWith("content://") == true) {
            copyUriToTempFile(imagePath, maxImageDim)
        } else imagePath

        val finalUri = if (resolvedPath != null && !resolvedPath.contains("://")) {
            "file://$resolvedPath"
        } else resolvedPath

        helper.predict(prompt = prompt, imagePath = finalUri)
    }

    private fun copyUriToTempFile(uriStr: String, maxDim: Int): String? {
        return try {
            val uri = Uri.parse(uriStr)

            // 1. Determine original dimensions to prevent OOM
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }

            // 2. Calculate optimal sample size (power of 2)
            var inSampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxDim && halfWidth / inSampleSize >= maxDim) {
                    inSampleSize *= 2
                }
            }

            // 3. Decode the sampled bitmap
            val decodeOptions = android.graphics.BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val bitmap = contentResolver.openInputStream(uri)?.use { 
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions) 
            } ?: return null

            // 4. Exact scale to fit within maxDim while preserving aspect ratio
            val currentMax = Math.max(bitmap.width, bitmap.height)
            val finalBitmap = if (currentMax > maxDim) {
                val scale = maxDim.toFloat() / currentMax
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap, 
                    (bitmap.width * scale).toInt(), 
                    (bitmap.height * scale).toInt(), 
                    true
                )
            } else bitmap

            // 5. Persist to cache as JPEG
            val tempFile = File(downloader.getModelFile(ModelConfig.DEFAULT_LLM).parentFile?.parentFile, "cache/vision_temp.jpg")
            tempFile.parentFile?.mkdirs()
            tempFile.outputStream().use { output ->
                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
            }
            
            // Cleanup
            if (finalBitmap != bitmap) finalBitmap.recycle()
            bitmap.recycle()
            
            Log.d("LlamaCppEngine", "Resized image saved to: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e("LlamaCppEngine", "Failed to resize/copy image URI", e)
            null
        }
    }

    override fun unload() {
        helper.release()
        loadedModelPath = null
    }

    override suspend fun stop() {
        helper.stopPrediction()
    }
}
