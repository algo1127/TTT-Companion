package com.ttt.companion.llm

sealed class DownloadState {
    data object Idle : DownloadState()
    data object AlreadyHave : DownloadState()
    data class Downloading(val progressPct: Int, val mbReceived: Float, val mbTotal: Float) : DownloadState()
    data object Done : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}