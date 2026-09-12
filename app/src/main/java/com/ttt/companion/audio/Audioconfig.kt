package com.ttt.companion.audio

/**
 * Single source of truth for audio model identity (STT + TTS).
 * Now supports multiple Whisper variants and checksum verification.
 */
object AudioConfig {

    // --- STT Whisper Registry -----------------------------------------------

    data class WhisperVariant(
        val id: String,
        val displayName: String,
        val description: String,
        val sizeLabel: String,
        val isMultilingual: Boolean,
        val subDir: String,
        val files: List<DownloadFile>
    )

    private const val HF_BASE = "https://huggingface.co/csukuangfj"
    const val STT_SAMPLE_RATE  = 16_000

    val WHISPER_VARIANTS = listOf(
        WhisperVariant(
            id = "tiny.en",
            displayName = "Whisper Tiny (EN)",
            description = "Fastest, optimized for English. Great for most phones.",
            sizeLabel = "87 MB",
            isMultilingual = false,
            subDir = "models/whisper-tiny-en",
            files = listOf(
                DownloadFile("encoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx"),
                DownloadFile("decoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx"),
                DownloadFile("tokens.txt",  "$HF_BASE/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-tokens.txt")
            )
        ),
        WhisperVariant(
            id = "base.en",
            displayName = "Whisper Base (EN)",
            description = "Better accuracy than Tiny while remaining very fast.",
            sizeLabel = "145 MB",
            isMultilingual = false,
            subDir = "models/whisper-base-en",
            files = listOf(
                DownloadFile("encoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-base.en/resolve/main/base.en-encoder.int8.onnx"),
                DownloadFile("decoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-base.en/resolve/main/base.en-decoder.int8.onnx"),
                DownloadFile("tokens.txt",  "$HF_BASE/sherpa-onnx-whisper-base.en/resolve/main/base.en-tokens.txt")
            )
        ),
        WhisperVariant(
            id = "small",
            displayName = "Whisper Small",
            description = "High fidelity. Supports multiple languages.",
            sizeLabel = "480 MB",
            isMultilingual = true,
            subDir = "models/whisper-small",
            files = listOf(
                DownloadFile("encoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-small/resolve/main/small-encoder.int8.onnx"),
                DownloadFile("decoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-small/resolve/main/small-decoder.int8.onnx"),
                DownloadFile("tokens.txt",  "$HF_BASE/sherpa-onnx-whisper-small/resolve/main/small-tokens.txt")
            )
        ),
        WhisperVariant(
            id = "medium",
            displayName = "Whisper Medium",
            description = "Professional accuracy. Recommended for powerful devices.",
            sizeLabel = "1.5 GB",
            isMultilingual = true,
            subDir = "models/whisper-medium",
            files = listOf(
                DownloadFile("encoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-medium/resolve/main/medium-encoder.int8.onnx"),
                DownloadFile("decoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-medium/resolve/main/medium-decoder.int8.onnx"),
                DownloadFile("tokens.txt",  "$HF_BASE/sherpa-onnx-whisper-medium/resolve/main/medium-tokens.txt")
            )
        ),
        WhisperVariant(
            id = "turbo",
            displayName = "Whisper Turbo",
            description = "Large-v3 quality with Medium-level speed. Best for high-end phones.",
            sizeLabel = "1.6 GB",
            isMultilingual = true,
            subDir = "models/whisper-turbo",
            files = listOf(
                DownloadFile("encoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-turbo/resolve/main/turbo-encoder.int8.onnx"),
                DownloadFile("decoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-turbo/resolve/main/turbo-decoder.int8.onnx"),
                DownloadFile("tokens.txt",  "$HF_BASE/sherpa-onnx-whisper-turbo/resolve/main/turbo-tokens.txt")
            )
        ),
        WhisperVariant(
            id = "large-v3",
            displayName = "Whisper Large-v3",
            description = "State-of-the-art accuracy. Very heavy on RAM and GPU.",
            sizeLabel = "3.0 GB",
            isMultilingual = true,
            subDir = "models/whisper-large-v3",
            files = listOf(
                DownloadFile("encoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-large-v3/resolve/main/large-v3-encoder.int8.onnx"),
                DownloadFile("decoder.int8.onnx", "$HF_BASE/sherpa-onnx-whisper-large-v3/resolve/main/large-v3-decoder.int8.onnx"),
                DownloadFile("tokens.txt",  "$HF_BASE/sherpa-onnx-whisper-large-v3/resolve/main/large-v3-tokens.txt")
            )
        )
    )

    fun getWhisperVariant(id: String): WhisperVariant = 
        WHISPER_VARIANTS.find { it.id == id } ?: WHISPER_VARIANTS.first()

    // --- Legacy Constants (Fallback) ----------------------------------------
    val DEFAULT_WHISPER = WHISPER_VARIANTS.first()
    val STT_DIR = DEFAULT_WHISPER.subDir
    val STT_FILES = DEFAULT_WHISPER.files

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

    const val TTS_REF_SAMPLE_RATE = 24_000

    private const val KOKORO_HF = "$HF_BASE/kokoro-multi-lang-v1_0/resolve/main"
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

/** A file that needs to be downloaded: (local filename, remote URL, optional sha256). */
data class DownloadFile(val filename: String, val url: String, val sha256: String? = null)
