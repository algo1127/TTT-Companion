package com.ttt.companion.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File

/**
 * Text-to-speech using sherpa-onnx with the Kokoro v0.19 model.
 */
class TtsService(private val context: Context) {

    private val TAG = "TtsService"

    private var tts: OfflineTts? = null

    sealed class LoadState {
        data object Idle    : LoadState()
        data object Loading : LoadState()
        data object Ready   : LoadState()
        data class Error(val message: String) : LoadState()
    }

    /**
     * Load the Kokoro ONNX model.
     */
    suspend fun init(referenceWavPath: String): LoadState = withContext(Dispatchers.IO) {
        try {
            val modelDir    = File(context.filesDir, AudioConfig.TTS_DIR)
            val modelFile   = File(modelDir, AudioConfig.TTS_MODEL_FILE)
            val voicesFile  = File(modelDir, AudioConfig.TTS_VOICES_FILE)
            val tokensFile  = File(modelDir, AudioConfig.TTS_TOKENS_FILE)
            val dataTarFile = File(modelDir, AudioConfig.TTS_DATA_TAR_BZ2)
            val dataDir     = File(modelDir, AudioConfig.TTS_DATA_DIR)

            for (f in listOf(modelFile, voicesFile, tokensFile, dataTarFile)) {
                if (!f.exists()) {
                    Log.e(TAG, "Missing TTS model file: ${f.absolutePath}")
                    return@withContext LoadState.Error("Missing: ${f.name}")
                }
                if (f.length() < 1024) {
                    Log.e(TAG, "TTS file is suspiciously small (${f.length()} bytes): ${f.absolutePath}")
                    f.delete()
                    return@withContext LoadState.Error("Corrupted: ${f.name}")
                }
                Log.d(TAG, "TTS File: ${f.name}, size=${f.length()} bytes")
            }

            // Extract espeak-ng-data if not already present
            if (!dataDir.exists()) {
                Log.d(TAG, "Extracting espeak-ng-data.tar.bz2...")
                untar(dataTarFile, modelDir)
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = modelFile.absolutePath,
                        voices = voicesFile.absolutePath,
                        tokens = tokensFile.absolutePath,
                        dataDir = dataDir.absolutePath
                    ),
                    numThreads = 6,
                    debug      = false,
                    provider   = "gpu"
                ),
                maxNumSentences = 1
            )

            tts = OfflineTts(null, config)
            Log.i(TAG, "Kokoro TTS loaded successfully")

            LoadState.Ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TTS", e)
            LoadState.Error(e.message ?: "Unknown TTS init error")
        }
    }

    private fun untar(tarBz2File: File, targetDir: File) {
        tarBz2File.inputStream().use { fis ->
            BZip2CompressorInputStream(fis).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val file = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { fos ->
                                tarIn.copyTo(fos)
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }

    suspend fun speak(text: String, speed: Float = 1.0f) = withContext(Dispatchers.IO) {
        val engine = tts ?: run {
            Log.e(TAG, "speak() called before init()")
            return@withContext
        }

        try {
            Log.d(TAG, "Generating TTS for: \"$text\"")
            val audio = engine.generate(text = text, sid = 0, speed = speed)
            Log.d(TAG, "TTS done — ${audio.samples.size} samples @ ${audio.sampleRate} Hz")
            playPcm(audio.samples, audio.sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error", e)
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(samples.size * 4)
            .build()

        track.play()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        
        // Wait for the track to finish playing
        val frames = samples.size
        while (track.playbackHeadPosition < frames) {
            try {
                Thread.sleep(10)
            } catch (_: Exception) { break }
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) break
        }

        track.stop()
        track.release()
        Log.d(TAG, "TTS playback complete")
    }

    fun release() {
        tts?.free()
        tts = null
        Log.d(TAG, "TTS released")
    }

    fun isReady() = tts != null
}