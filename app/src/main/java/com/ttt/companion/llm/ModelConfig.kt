package com.ttt.companion.llm

/**
 * Single source of truth for model identity.
 * Now supports multiple LLM variants.
 */
object ModelConfig {

    data class ModelVariant(
        val id: String,
        val displayName: String,
        val description: String,
        val sizeLabel: String,
        val url: String,
        val filename: String,
        val subDir: String,
        val isReasoning: Boolean,
        val isRecommended: Boolean = true
    )

    val LLM_VARIANTS = listOf(
        ModelVariant(
            id = "qwen3-4b-instruct",
            displayName = "Qwen 3 4B Instruct",
            description = "High performance, stable, and efficient. No internal monologue.",
            sizeLabel = "~2.7 GB",
            url = "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
            filename = "qwen3-4b-instruct-q4_k_m.gguf",
            subDir = "models/qwen3-4b-instruct",
            isReasoning = false
        ),
        ModelVariant(
            id = "qwen2.5-3b-instruct",
            displayName = "Qwen 2.5 3B Instruct",
            description = "Lightweight, very fast, and extremely stable.",
            sizeLabel = "~1.9 GB",
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-3b-instruct-q4_k_m.gguf",
            subDir = "models/qwen2.5-3b-instruct",
            isReasoning = false
        ),
        ModelVariant(
            id = "qwen3.5-4b-reasoning",
            displayName = "Qwen 3.5 4B (Reasoning)",
            description = "[EXPERIMENTAL] Uses a thinking chain. Highly prone to glitches and slow inference.",
            sizeLabel = "~2.7 GB",
            url = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            filename = "qwen3.5-4b-reasoning-q4_k_m.gguf",
            subDir = "models/qwen3.5-4b-reasoning",
            isReasoning = true,
            isRecommended = false
        )
    )

    fun getLlmVariant(id: String): ModelVariant =
        LLM_VARIANTS.find { it.id == id } ?: LLM_VARIANTS.first()

    val DEFAULT_LLM = LLM_VARIANTS.first()

    // Legacy fields for backward compatibility during refactor
    const val MODEL_DIR = "models/qwen3.5-4b"
    const val MODEL_FILENAME = "qwen3.5-4b-q4_k_m.gguf"
}
