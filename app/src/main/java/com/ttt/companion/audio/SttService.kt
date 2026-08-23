package com.ttt.companion.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline speech-to-text using sherpa-onnx + Whisper tiny.en INT8.
 */
class SttService(private val context: Context) {

    private val TAG = "SttService"

    private var recognizer: OfflineRecognizer? = null

    sealed class LoadState {
        data object Idle    : LoadState()
        data object Loading : LoadState()
        data object Ready   : LoadState()
        data class Error(val message: String) : LoadState()
    }

    /** Load Whisper models from filesDir. Call on IO thread. Returns [LoadState.Ready] on success. */
    suspend fun init(): LoadState = withContext(Dispatchers.IO) {
        try {
            val dir     = File(context.filesDir, AudioConfig.STT_DIR)
            val encoder = File(dir, AudioConfig.STT_ENCODER_FILE)
            val decoder = File(dir, AudioConfig.STT_DECODER_FILE)
            val tokens  = File(dir, AudioConfig.STT_TOKENS_FILE)

            for (f in listOf(encoder, decoder, tokens)) {
                if (!f.exists()) {
                    Log.e(TAG, "Missing STT model file: ${f.absolutePath}")
                    return@withContext LoadState.Error("Missing: ${f.name}")
                }
                if (f.length() < 1024) { // Less than 1KB is almost certainly an error/HTML page
                    Log.e(TAG, "STT file is suspiciously small (${f.length()} bytes): ${f.absolutePath}")
                    f.delete() // Delete so it can be re-downloaded
                    return@withContext LoadState.Error("Corrupted: ${f.name}")
                }
                Log.d(TAG, "STT File: ${f.name}, size=${f.length()} bytes")
            }

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = AudioConfig.STT_SAMPLE_RATE,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder      = encoder.absolutePath,
                        decoder      = decoder.absolutePath,
                        language     = "en",
                        task         = "transcribe",
                        tailPaddings = -1   // -1 = auto
                    ),
                    tokens     = tokens.absolutePath,
                    numThreads = 2,
                    debug      = false,
                    provider   = "cpu"
                )
            )

            recognizer = OfflineRecognizer(config = config)
            Log.i(TAG, "Whisper STT loaded successfully")
            LoadState.Ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Whisper", e)
            LoadState.Error(e.message ?: "Unknown STT init error")
        }
    }

    /**
     * Transcribe [samples] (FloatArray normalised to [-1,1], 16 kHz mono).
     * Returns the recognised text, or an empty string on failure.
     */
    suspend fun transcribe(samples: FloatArray, sampleRate: Int = AudioConfig.STT_SAMPLE_RATE): String =
        withContext(Dispatchers.IO) {
            val r = recognizer ?: run {
                Log.e(TAG, "transcribe() called before init()")
                return@withContext ""
            }
            try {
                val stream = r.createStream()
                stream.acceptWaveform(samples = samples, sampleRate = sampleRate)
                r.decode(stream)
                val text = r.getResult(stream).text.trim()
                stream.release()
                Log.i(TAG, "STT result: \"$text\"")
                text
            } catch (e: Exception) {
                Log.e(TAG, "STT transcription error", e)
                ""
            }
        }

    fun release() {
        recognizer?.release()
        recognizer = null
        Log.d(TAG, "STT released")
    }

    fun isReady() = recognizer != null
}