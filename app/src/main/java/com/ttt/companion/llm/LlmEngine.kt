package com.ttt.companion.llm

import com.ttt.companion.model.CharacterProfile
import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    data class Metrics(
        val ttft: Long = 0,
        val totalTime: Long = 0,
        val tokenCount: Int = 0,
        val tokensPerSec: Float = 0f
    )

    sealed class Event {
        data object Loaded : Event()
        data class Ongoing(val word: String) : Event()
        data class Done(val metrics: Metrics? = null) : Event()
        data class Error(val message: String) : Event()
        data object Started : Event()
    }

    val events: Flow<Event>
    val engineName: String
    val computeUnit: String

    suspend fun loadModel(profile: CharacterProfile, contextSize: Int): LlmService.LoadState
    suspend fun predict(
        prompt: String,
        tempOverride: Float? = null,
        stopWords: List<String> = emptyList(),
        useCache: Boolean = true
    )
    fun unload()
    suspend fun stop()
}
