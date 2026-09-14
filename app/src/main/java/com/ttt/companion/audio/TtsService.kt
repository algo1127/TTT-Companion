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
    private var currentTrack: AudioTrack? = null
    private var stopFlag = false

    sealed class LoadState {
        data object Idle    : LoadState()
        data object Loading : LoadState()
        data object Ready   : LoadState()
        data class Error(val message: String) : LoadState()
    }

    /**
     * Load the Kokoro ONNX model, optionally creating a blended voice.
     */
    suspend fun init(
        referenceWavPath: String, 
        lang: String = "en-us",
        blend: BlendConfig? = null
    ): LoadState = withContext(Dispatchers.IO) {
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
                if (f.length() == 0L) {
                    Log.e(TAG, "TTS file is empty: ${f.absolutePath}")
                    f.delete()
                    return@withContext LoadState.Error("Empty: ${f.name}")
                }
            }

            // Extract espeak-ng-data if not already present
            if (!dataDir.exists()) {
                Log.d(TAG, "Extracting espeak-ng-data.tar.bz2...")
                untar(dataTarFile, modelDir)
            }

            // Handle Voice Blending if requested
            var activeVoicesPath = voicesFile.absolutePath
            if (blend != null) {
                try {
                    val customVoicesFile = File(modelDir, "voices_custom.bin")
                    createBlendedVoices(voicesFile, customVoicesFile, blend)
                    activeVoicesPath = customVoicesFile.absolutePath
                    Log.i(TAG, "Custom blended voice created at SID ${blend.customSid}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to blend voices, using original", e)
                }
            }

            // Map language to lexicon
            val lexiconFile = when (lang.lowercase()) {
                "en-us" -> File(modelDir, AudioConfig.TTS_LEXICON_EN_US)
                "en-gb" -> File(modelDir, AudioConfig.TTS_LEXICON_EN_GB)
                "zh"    -> File(modelDir, AudioConfig.TTS_LEXICON_ZH)
                else    -> null
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = modelFile.absolutePath,
                        voices = activeVoicesPath,
                        tokens = tokensFile.absolutePath,
                        dataDir = dataDir.absolutePath,
                        lexicon = lexiconFile?.absolutePath ?: "",
                        lang = lang
                    ),
                    numThreads = 6,
                    debug      = false,
                    provider   = "cpu"
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

    private fun createBlendedVoices(source: File, target: File, blend: BlendConfig) {
        val originalBytes = source.readBytes().copyOf() // Copy to be safe
        val speakerCount = 53
        val vectorSize = originalBytes.size / speakerCount 
        
        val offsetA = blend.voiceASid * vectorSize
        val offsetB = blend.voiceBSid * vectorSize
        val offsetTarget = blend.customSid * vectorSize // Usually 0
        
        if (offsetA + vectorSize > originalBytes.size || 
            offsetB + vectorSize > originalBytes.size ||
            offsetTarget + vectorSize > originalBytes.size) {
            throw Exception("Voice ID out of range for blending")
        }

        val ratioA = blend.ratio
        val ratioB = 1.0f - ratioA

        // Perform vector interpolation in float space
        val floatCount = vectorSize / 4
        for (i in 0 until floatCount) {
            val byteIdx = i * 4
            
            val valA = java.nio.ByteBuffer.wrap(originalBytes, offsetA + byteIdx, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).float
            val valB = java.nio.ByteBuffer.wrap(originalBytes, offsetB + byteIdx, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).float
            
            val mixed = (valA * ratioA) + (valB * ratioB)
            
            java.nio.ByteBuffer.wrap(originalBytes, offsetTarget + byteIdx, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(mixed)
        }

        // Save the modified file (same size as original)
        target.writeBytes(originalBytes)
    }

    data class BlendConfig(
        val voiceASid: Int,
        val voiceBSid: Int,
        val ratio: Float,
        val customSid: Int // Total original voices
    )

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

    suspend fun speak(text: String, voiceId: Int = 0, speed: Float = 1.0f, pitch: Float = 1.0f) = withContext(Dispatchers.IO) {
        val engine = tts ?: run {
            Log.e(TAG, "speak() called before init()")
            return@withContext
        }

        try {
            stopFlag = false
            Log.d(TAG, "Generating TTS for: \"$text\" (voice=$voiceId, speed=$speed, pitch=$pitch)")
            val audio = engine.generate(text = text, sid = voiceId, speed = speed)
            
            if (stopFlag) {
                Log.d(TAG, "TTS generation cancelled by user")
                return@withContext
            }

            Log.d(TAG, "TTS done ÔÇö ${audio.samples.size} samples @ ${audio.sampleRate} Hz")
            playPcm(audio.samples, audio.sampleRate, pitch = pitch)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error", e)
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int, pitch: Float = 1.0f) {
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

        if (pitch != 1.0f) {
            try {
                val params = track.playbackParams
                params.pitch = pitch
                track.playbackParams = params
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set pitch: ${e.message}")
            }
        }

        currentTrack = track
        track.play()
        
        // Write in chunks to allow for quicker interruption
        val chunkSize = 4096
        var written = 0
        while (written < samples.size && !stopFlag) {
            val toWrite = minOf(chunkSize, samples.size - written)
            track.write(samples, written, toWrite, AudioTrack.WRITE_BLOCKING)
            written += toWrite
        }
        
        // Wait for the track to finish playing remaining buffer
        val frames = samples.size
        while (track.playbackHeadPosition < frames && !stopFlag) {
            try {
                Thread.sleep(10)
            } catch (_: Exception) { break }
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) break
        }

        track.stop()
        track.release()
        currentTrack = null
        Log.d(TAG, "TTS playback ${if (stopFlag) "interrupted" else "complete"}")
    }

    fun stop() {
        Log.i(TAG, "Stop requested for TTS")
        stopFlag = true
        try {
            currentTrack?.let {
                it.pause()
                it.flush()
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        currentTrack = null
    }

    fun release() {
        tts?.free()
        tts = null
        Log.d(TAG, "TTS released")
    }

    fun isReady() = tts != null
}
