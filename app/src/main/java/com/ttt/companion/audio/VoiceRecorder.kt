package com.ttt.companion.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records from the microphone into a temporary WAV file at [AudioConfig.STT_SAMPLE_RATE].
 *
 * Usage pattern (hold-to-talk):
 *   - Call [startRecording] when the mic button is pressed → suspends and fills the file
 *   - Set [stopFlag] = true when the button is released → coroutine returns the WAV [File]
 */
class VoiceRecorder(private val context: Context) {

    private val TAG = "VoiceRecorder"

    /** Set to true from any thread to stop the ongoing recording. */
    @Volatile var stopFlag = false

    private val sampleRate   = AudioConfig.STT_SAMPLE_RATE
    private val channelCfg   = AudioFormat.CHANNEL_IN_MONO
    private val encoding     = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize   = AudioRecord.getMinBufferSize(sampleRate, channelCfg, encoding)
        .coerceAtLeast(4096)

    /** The temporary WAV written during the last recording. */
    private val wavFile: File
        get() = File(context.cacheDir, "voice_input.wav")

    /**
     * Blocks until [stopFlag] is true, then returns the WAV file path.
     * Must be called from a coroutine; it runs entirely on [Dispatchers.IO].
     * Returns null if permission was not granted.
     */
    suspend fun startRecording(): File? = withContext(Dispatchers.IO) @androidx.annotation.RequiresPermission(
        android.Manifest.permission.RECORD_AUDIO
    ) {
        if (!hasMicPermission()) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return@withContext null
        }

        stopFlag = false
        val outputStream = java.io.ByteArrayOutputStream()
        val readBuffer = ShortArray(bufferSize / 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelCfg, encoding, bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            recorder.release()
            return@withContext null
        }

        try {
            recorder.startRecording()
            Log.d(TAG, "Recording started")

            val byteBuffer = ByteBuffer.allocate(readBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

            while (isActive && !stopFlag) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    byteBuffer.clear()
                    for (i in 0 until read) {
                        byteBuffer.putShort(readBuffer[i])
                    }
                    outputStream.write(byteBuffer.array(), 0, read * 2)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording error", e)
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) { /* ignore */ }
            recorder.release()
            Log.d(TAG, "Recording stopped — ${outputStream.size() / 2} samples")
        }

        writeWavData(outputStream.toByteArray())
        wavFile
    }

    private fun writeWavData(pcmBytes: ByteArray) {
        FileOutputStream(wavFile).use { fos ->
            fos.write(wavHeader(pcmBytes.size))
            fos.write(pcmBytes)
        }
    }

    private fun wavHeader(pcmByteCount: Int): ByteArray {
        val totalDataLen   = pcmByteCount + 36
        val byteRate       = sampleRate * 2           // 16-bit mono
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)          // subchunk1 size (PCM)
            putShort(1)         // PCM format
            putShort(1)         // mono
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2)         // block align
            putShort(16)        // bits per sample
            put("data".toByteArray())
            putInt(pcmByteCount)
        }.array()
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Read a WAV file (PCM 16-bit, any sample rate) and return it as a FloatArray
     * normalised to [-1, 1].  Skips the 44-byte WAV header.
     * Also returns the actual sample rate from the WAV header.
     */
    fun readWavAsFloat(file: File): Pair<FloatArray, Int> {
        RandomAccessFile(file, "r").use { raf ->
            // Parse sample rate from WAV header offset 24
            raf.seek(24)
            val buf4 = ByteArray(4)
            raf.read(buf4)
            val fileSampleRate = ByteBuffer.wrap(buf4).order(ByteOrder.LITTLE_ENDIAN).int

            // Skip to data
            raf.seek(44)
            val pcmBytes = ByteArray((raf.length() - 44).toInt())
            raf.read(pcmBytes)
            val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(pcmBytes.size / 2) {
                bb.short / 32768.0f
            }
            return Pair(floats, fileSampleRate)
        }
    }
}