package com.ttt.companion.audio

/**
 * Single source of truth for audio model identity (STT + TTS).
 *
 * STT  → sherpa-onnx Whisper tiny.en INT8  (~87 MB, two ONNX files + tokens)
 * TTS  → sherpa-onnx XTTS-v2 ONNX         (~1.6 GB, two ONNX files + vocab)
 *
 * All paths are relative to Context.filesDir.
 */
object AudioConfig {

    // ─── STT — Whisper tiny.en INT8 ─────────────────────────────────────────

    const val STT_DIR          = "models/whisper-tiny-en"
    const val STT_ENCODER_FILE = "encoder.int8.onnx"
    const val STT_DECODER_FILE = "decoder.int8.onnx"
    const val STT_TOKENS_FILE  = "tokens.txt"
    const val STT_DISPLAY_SIZE = "~87 MB"
    const val STT_SAMPLE_RATE  = 16_000

    private const val STT_HF = "https://huggingface.co/csukuangfj/" +
            "sherpa-onnx-whisper-tiny.en/resolve/main"

    val STT_FILES = listOf(
        DownloadFile(STT_ENCODER_FILE, "$STT_HF/tiny.en-encoder.int8.onnx"),
        DownloadFile(STT_DECODER_FILE, "$STT_HF/tiny.en-decoder.int8.onnx"),
        DownloadFile(STT_TOKENS_FILE,  "$STT_HF/tiny.en-tokens.txt")
    )

    // ─── TTS — XTTS-v2 ONNX (Coqui, via sherpa-onnx) ───────────────────────
    //
    // The sherpa-onnx team exports XTTS-v2 to ONNX and hosts it on HuggingFace.
    // These are the two ONNX shards + the vocab file needed by OfflineTts.
    //
    // !! VERIFY before first build — confirm repo name & file list with:
    //    https://huggingface.co/csukuangfj?search=xtts
    //

    const val TTS_DIR           = "models/kokoro-en-v019"
    const val TTS_MODEL_FILE    = "model.onnx"
    const val TTS_VOICES_FILE   = "voices.bin"
    const val TTS_TOKENS_FILE   = "tokens.txt"
    const val TTS_DATA_TAR_BZ2  = "espeak-ng-data.tar.bz2"
    const val TTS_DATA_DIR      = "espeak-ng-data"
    const val TTS_DISPLAY_SIZE  = "~350 MB"

    // Reference sample rate for Kokoro (24kHz)
    const val TTS_REF_SAMPLE_RATE = 24_000

    private const val KOKORO_HF = "https://huggingface.co/csukuangfj/kokoro-en-v0_19/resolve/main"
    private const val DATA_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"

    val TTS_FILES = listOf(
        DownloadFile(TTS_MODEL_FILE,   "$KOKORO_HF/model.onnx"),
        DownloadFile(TTS_VOICES_FILE,  "$KOKORO_HF/voices.bin"),
        DownloadFile(TTS_TOKENS_FILE,  "$KOKORO_HF/tokens.txt"),
        DownloadFile(TTS_DATA_TAR_BZ2, DATA_URL)
    )

    // ─── Voice sample ────────────────────────────────────────────────────────
    //
    // Bundled in assets/voice_samples/aria_reference.wav (commit a clean 5-10s
    // mono 22050 Hz WAV of the desired voice).  Copied to filesDir on first run.

    const val VOICE_SAMPLE_ASSET = "voice_samples/aria_reference.wav"
    const val VOICE_SAMPLE_DIR   = "characters/aria"
    const val VOICE_SAMPLE_FILE  = "voice_reference.wav"
}

/** A file that needs to be downloaded: (local filename, remote URL). */
data class DownloadFile(val filename: String, val url: String)