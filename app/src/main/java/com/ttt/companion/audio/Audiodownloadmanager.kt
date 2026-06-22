package com.ttt.companion.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a list of [DownloadFile]s into [targetDir] inside [Context.filesDir].
 * Supports byte-range resumption via the Range header.
 *
 * @param onProgress called on the **calling** coroutine dispatcher with a
 *                   human-readable label, 0-100 percent, and MB numbers.
 */
class AudioDownloadManager(private val context: Context) {

    /**
     * Returns true only when every file in [files] exists and is non-empty.
     */
    fun areFilesReady(subDir: String, files: List<DownloadFile>): Boolean {
        val dir = File(context.filesDir, subDir)
        return files.all { f ->
            val file = File(dir, f.filename)
            file.exists() && file.length() > 0L
        }
    }

    /**
     * Download all [files] into [subDir] (relative to filesDir).
     * Calls [onProgress] after each buffer write so the UI stays responsive.
     *
     * @param phaseLabel shown in the UI, e.g. "STT (Whisper)"
     */
    suspend fun downloadAll(
        subDir: String,
        files: List<DownloadFile>,
        phaseLabel: String,
        onProgress: suspend (label: String, pct: Int, mbReceived: Float, mbTotal: Float) -> Unit
    ) {
        val dir = File(context.filesDir, subDir).also { it.mkdirs() }

        files.forEachIndexed { idx, f ->
            val target   = File(dir, f.filename)
            val temp     = File(dir, "${f.filename}.part")
            val existing = if (temp.exists()) temp.length() else 0L

            // Already have this file — skip
            if (target.exists() && target.length() > 0L) {
                Log.d("AudioDL", "[$phaseLabel] ${f.filename} already present — skipping")
                return@forEachIndexed
            }

            Log.d("AudioDL", "[$phaseLabel] Downloading ${f.filename} (resume from ${existing}B)")

            withContext(Dispatchers.IO) {
                var currentUrl = f.url
                var conn: HttpURLConnection? = null
                var redirects = 0
                val maxRedirects = 5

                while (redirects < maxRedirects) {
                    val url = URL(currentUrl)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout    = 45_000
                        instanceFollowRedirects = false // Manual handle for reliability
                        setRequestProperty("User-Agent", "TTTCompanion/1.0")
                        if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                    }

                    val status = conn.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == 307 || status == 308) {

                        val location = conn.getHeaderField("Location")
                        // Fix: handle relative redirect paths
                        currentUrl = if (location.startsWith("/")) {
                            "${url.protocol}://${url.host}$location"
                        } else {
                            location
                        }
                        redirects++
                        conn.disconnect()
                        continue
                    }
                    break
                }

                val finalConn = conn ?: throw Exception("Failed to connect to ${f.url}")
                val status = finalConn.responseCode
                val contentType = finalConn.contentType ?: "unknown"

                Log.d("AudioDL", "[$phaseLabel] HTTP $status, Type: $contentType, Length: ${finalConn.contentLengthLong}")

                if (status !in 200..299) {
                    throw Exception("HTTP $status for ${f.url}")
                }

                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw Exception("Received HTML instead of binary for ${f.filename} (possibly a 404/redirect error page)")
                }

                val contentLen  = finalConn.contentLengthLong
                val totalBytes  = if (contentLen > 0) contentLen + existing else -1L
                var bytesWritten = existing
                var lastUpdateMillis = 0L

                finalConn.inputStream.use { input ->
                    // Append to temp file if resuming, otherwise overwrite
                    val fos = FileOutputStream(temp, existing > 0)
                    fos.use { output ->
                        val buf = ByteArray(65536) // 64KB buffer
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            bytesWritten += read

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateMillis > 200L) {
                                lastUpdateMillis = currentTime
                                val pct = if (totalBytes > 0)
                                    ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                else
                                    ((idx * 100) / files.size)
                                val label = "$phaseLabel — ${f.filename} (${idx + 1}/${files.size})"
                                onProgress(label, pct, bytesWritten / 1_048_576f, totalBytes / 1_048_576f)
                            }
                        }
                    }
                }

                // Final progress update for this file
                val finalPct = if (totalBytes > 0)
                    ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 100)
                else
                    100
                val finalLabel = "$phaseLabel — ${f.filename} (${idx + 1}/${files.size})"
                onProgress(finalLabel, finalPct, bytesWritten / 1_048_576f, totalBytes / 1_048_576f)

                temp.renameTo(target)
                Log.i("AudioDL", "[$phaseLabel] ${f.filename} → saved (${target.length()} bytes)")
            }
        }
    }
}