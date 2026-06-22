package com.ttt.companion.llm

/**
 * Single source of truth for model identity.
 * Change the URL here when upgrading models — nothing else needs to change.
 */
object ModelConfig {
    const val MODEL_ID      = "qwen3.5-4b"
    const val MODEL_DIR     = "models/qwen3.5-4b"  // relative to filesDir

    // !! Replace with the real HuggingFace URL for your chosen quantization !!
    // Pattern: https://huggingface.co/<user>/<repo>/resolve/main/<file.gguf>
    const val DOWNLOAD_URL  = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf"

    // Expected filename after download
    const val MODEL_FILENAME = "qwen3.5-4b-q4_k_m.gguf"

    // Approximate size shown to user before download (~1.5 GB for Q4)
    const val DISPLAY_SIZE  = "~2.7 GB"
}