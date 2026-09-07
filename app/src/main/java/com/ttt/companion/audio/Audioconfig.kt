package com.ttt.companion.audio

/**
 * Single source of truth for audio model identity (STT + TTS).
 */
object AudioConfig {

    // --- STT — Whisper tiny.en INT8 -----------------------------------------

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

    // --- TTS — Kokoro v1.0 --------------------------------------------------

    const val TTS_DIR           = "models/kokoro-multi-v1.0"
    const val TTS_MODEL_FILE    = "model.onnx"
    const val TTS_VOICES_FILE   = "voices.bin"
    const val TTS_TOKENS_FILE   = "tokens.txt"
    const val TTS_DATA_TAR_BZ2  = "espeak-ng-data.tar.bz2"
    const val TTS_DATA_DIR      = "espeak-ng-data"
    const val TTS_LEXICON_EN_US = "lexicon-us-en.txt"
    const val TTS_LEXICON_EN_GB = "lexicon-gb-en.txt"
    const val TTS_LEXICON_ZH    = "lexicon-zh.txt"
    const val TTS_DISPLAY_SIZE  = "~415 MB"

    // Reference sample rate for Kokoro (24kHz)
    const val TTS_REF_SAMPLE_RATE = 24_000

    private const val KOKORO_HF = "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/resolve/main"
    private const val DATA_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"

    val TTS_FILES = listOf(
        DownloadFile(TTS_MODEL_FILE,   "$KOKORO_HF/model.onnx"),
        DownloadFile(TTS_VOICES_FILE,  "$KOKORO_HF/voices.bin"),
        DownloadFile(TTS_TOKENS_FILE,  "$KOKORO_HF/tokens.txt"),
        DownloadFile(TTS_LEXICON_EN_US, "$KOKORO_HF/lexicon-us-en.txt"),
        DownloadFile(TTS_LEXICON_EN_GB, "$KOKORO_HF/lexicon-gb-en.txt"),
        DownloadFile(TTS_LEXICON_ZH,    "$KOKORO_HF/lexicon-zh.txt"),
        DownloadFile(TTS_DATA_TAR_BZ2, DATA_URL)
    )

    // --- Voice sample -------------------------------------------------------

    const val VOICE_SAMPLE_ASSET = "voice_samples/aria_reference.wav"
    const val VOICE_SAMPLE_DIR   = "characters/aria"
    const val VOICE_SAMPLE_FILE  = "voice_reference.wav"
}

/** A file that needs to be downloaded: (local filename, remote URL). */
data class DownloadFile(val filename: String, val url: String)
