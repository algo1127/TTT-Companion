package com.ttt.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.llm.LlmService
import com.ttt.companion.llm.ModelDownloader
import com.ttt.companion.model.ChatMessage
import com.ttt.companion.model.defaultCharacter
import org.nehuatl.llamacpp.LlamaHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val downloader = ModelDownloader(app)
    private val llm        = LlmService(app)
    val character          = defaultCharacter(app.filesDir)

    // ── Download state ───────────────────────────────────────
    private val _downloadState = MutableStateFlow<DownloadState>(
        if (downloader.isModelReady()) DownloadState.Done else DownloadState.Idle
    )
    val downloadState = _downloadState.asStateFlow()

    // ── Chat state ───────────────────────────────────────────
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _modelState = MutableStateFlow<LlmService.LoadState>(LlmService.LoadState.Idle)
    val modelState = _modelState.asStateFlow()

    init {
        // Already downloaded on a previous launch — load right away
        if (downloader.isModelReady()) loadModel()
    }

    /** Called by SetupScreen's Download button */
    fun startDownload() {
        viewModelScope.launch {
            downloader.download { state ->
                _downloadState.value = state
                if (state == DownloadState.Done || state == DownloadState.AlreadyHave) {
                    loadModel()
                }
            }
        }
    }

    private fun loadModel() {
        viewModelScope.launch {
            _modelState.value = LlmService.LoadState.Loading
            _modelState.value = llm.loadModel(character)
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        if (_isLoading.value) return

        val userMsg = ChatMessage(role = "user", content = userText.trim())
        val updatedHistory = _messages.value + userMsg
        _messages.value = updatedHistory
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val response = llm.chat(
                    history = updatedHistory,
                    systemPrompt = character.systemPrompt
                )
                val assistantMsg = ChatMessage(role = "assistant", content = response)
                _messages.value = updatedHistory + assistantMsg
            } catch (e: Exception) {
                val errMsg = ChatMessage(role = "assistant", content = "[Error: ${e.message}]")
                _messages.value = updatedHistory + errMsg
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        llm.unload()
        super.onCleared()
    }
}