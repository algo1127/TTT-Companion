package com.ttt.companion.llm

/** Which sequential download phase we are currently in. */
enum class SetupPhase(val displayName: String) {
    LLM("LLM (Qwen 3.5-2B)"),
    STT("Speech recognition (Whisper)"),
    TTS("Voice synthesis (XTTS-v2)")
}

sealed class DownloadState {
    data object Idle        : DownloadState()
    data object AlreadyHave : DownloadState()

    data class Downloading(
        val phase       : SetupPhase = SetupPhase.LLM,
        val label       : String     = "",
        val progressPct : Int,
        val mbReceived  : Float,
        val mbTotal     : Float
    ) : DownloadState()

    data object Done : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}