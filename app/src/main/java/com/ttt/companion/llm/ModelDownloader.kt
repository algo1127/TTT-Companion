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

    fun getMmprojFile(variant: ModelConfig.ModelVariant): File? {
        val filename = variant.mmprojFilename ?: return null
        val dir = File(context.filesDir, variant.subDir).also { it.mkdirs() }
        return File(dir, filename)
    }

    /** True if the model file (and mmproj if needed) is already on disk and non-empty */
    fun isModelReady(variant: ModelConfig.ModelVariant): Boolean {
        val mainFile = getModelFile(variant)
        val mainReady = mainFile.exists() && mainFile.length() > 0L
        
        val mmprojFile = getMmprojFile(variant)
        val mmprojReady = if (mmprojFile != null) {
            mmprojFile.exists() && mmprojFile.length() > 0L
        } else true
        
        return mainReady && mmprojReady
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

        // 1. Download Main Model
        val mainFile = getModelFile(variant)
        if (mainFile.length() == 0L) {
            try {
                downloadInternal(variant.url, mainFile, variant.filename, onProgress)
            } catch (e: Exception) {
                return // downloadInternal already reported failure to onProgress
            }
        }

        // 2. Download MMPROJ if specified
        val mmprojUrl = variant.mmprojUrl
        val mmprojFile = getMmprojFile(variant)
        if (mmprojUrl != null && mmprojFile != null && mmprojFile.length() == 0L) {
            try {
                downloadInternal(mmprojUrl, mmprojFile, variant.mmprojFilename!!, onProgress)
            } catch (e: Exception) {
                return // downloadInternal already reported failure to onProgress
            }
        }

        onProgress(DownloadState.Done)
    }

    private suspend fun downloadInternal(
        fileUrl: String, 
        targetFile: File, 
        displayLabel: String,
        onProgress: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        try {
            val url = URL(fileUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                setRequestProperty("User-Agent", "TTTCompanion/1.0")
                if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
            }

            val totalBytes  = conn.contentLengthLong + existingBytes
            var bytesWritten = existingBytes
            var lastUpdateMillis = 0L

            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(65536)
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
                                    label       = displayLabel,
                                    progressPct = pct,
                                    mbReceived  = bytesWritten / 1_048_576f,
                                    mbTotal     = totalBytes   / 1_048_576f
                                ))
                            }
                        }
                    }
                }
            }
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onProgress(DownloadState.Failed(e.message ?: "Download failed"))
            }
            throw e // Rethrow to stop the sequence in download()
        }
    }
}
