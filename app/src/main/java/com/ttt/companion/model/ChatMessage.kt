package com.ttt.companion.model

data class ChatMessage(
    val role: String,    // "user" | "assistant" | "system"
    val content: String,
    val performanceStats: PerformanceStats? = null,
    val hasImage: Boolean = false
)

data class PerformanceStats(
    val ttft: Long,         // ms
    val totalTime: Long,    // ms
    val tokenCount: Int,
    val tokensPerSec: Float,
    val engine: String,
    val backend: String
)
