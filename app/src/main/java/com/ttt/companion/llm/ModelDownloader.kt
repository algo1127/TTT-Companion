package com.ttt.companion.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {

    fun getModelFile(variant: ModelConfig.ModelVariant): File {
        val dir = File(context.filesDir, variant.subDir).also { it.mkdirs() }
        return File(dir, variant.filename)
    }

    /** True if the model file is already on disk and non-empty */
    fun isModelReady(variant: ModelConfig.ModelVariant): Boolean {
        val file = getModelFile(variant)
        return file.exists() && file.length() > 0L
    }

    /**
     * Download the model file with progress reporting.
     * Resumes partial downloads if the file already exists (uses Range header).
     * [onProgress] is called on the main thread — safe to update UI directly.
     */
    suspend fun download(variant: ModelConfig.ModelVariant, onProgress: (DownloadState) -> Unit) {
        if (isModelReady(variant)) {
            onProgress(DownloadState.AlreadyHave)
            return
        }

        withContext(Dispatchers.IO) {
            val modelFile = getModelFile(variant)
            val tempFile = File(modelFile.parentFile, "${variant.filename}.part")
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            try {
                val url = URL(variant.url)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout    = 30_000
                    setRequestProperty("User-Agent", "TTTCompanion/1.0")
                    // Resume support
                    if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
                }

                val totalBytes  = conn.contentLengthLong + existingBytes
                var bytesWritten = existingBytes
                var lastUpdateMillis = 0L

                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(65536) // 64KB buffer
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesWritten += read

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateMillis > 200L) {
                                lastUpdateMillis = currentTime
                                val pct = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0
                                withContext(Dispatchers.Main) {
                                    onProgress(DownloadState.Downloading(
                                        phase       = SetupPhase.LLM,
                                        label       = variant.filename,
                                        progressPct = pct,
                                        mbReceived  = bytesWritten / 1_048_576f,
                                        mbTotal     = totalBytes   / 1_048_576f
                                    ))
                                }
                            }
                        }
                        // Final progress update for this file
                        val finalPct = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 100
                        withContext(Dispatchers.Main) {
                            onProgress(DownloadState.Downloading(
                                phase       = SetupPhase.LLM,
                                label       = variant.filename,
                                progressPct = finalPct,
                                mbReceived  = bytesWritten / 1_048_576f,
                                mbTotal     = totalBytes   / 1_048_576f
                            ))
                        }
                    }
                }

                // Rename .part → final file only when complete
                tempFile.renameTo(modelFile)
                withContext(Dispatchers.Main) { onProgress(DownloadState.Done) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onProgress(DownloadState.Failed(e.message ?: "Download failed"))
                }
            }
        }
    }
}
