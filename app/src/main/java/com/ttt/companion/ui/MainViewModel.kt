package com.ttt.companion.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ttt.companion.audio.AudioConfig
import com.ttt.companion.audio.AudioDownloadManager
import com.ttt.companion.audio.SttService
import com.ttt.companion.audio.TtsService
import com.ttt.companion.audio.VoiceRecorder
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.llm.LlmService
import com.ttt.companion.llm.ModelDownloader
import com.ttt.companion.llm.SetupPhase
import com.ttt.companion.model.ChatMessage
import com.ttt.companion.model.defaultCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "MainViewModel"

    private val downloader    = ModelDownloader(app)
    private val audioDl       = AudioDownloadManager(app)
    private val llm           = LlmService(app)
    private val sttService    = SttService(app)
    private val ttsService    = TtsService(app)
    private val voiceRecorder = VoiceRecorder(app)
    private val memoryManager = com.ttt.companion.memory.MemoryManager(app)
    private val toolRouter    = com.ttt.companion.tools.ToolRouter(app)

    val character = defaultCharacter(app.filesDir)

    // Build the full system prompt. Start with tools immediately so they are always available.
    private var fullSystemPrompt: String = character.systemPrompt +
            "\n\n" + com.ttt.companion.tools.ToolDefinitions.SYSTEM_PROMPT_ADDITION

    // ── Setup / download state ────────────────────────────────────────────────

    private val _downloadState = MutableStateFlow<DownloadState>(
        if (allModelsReady()) DownloadState.Done else DownloadState.Idle
    )
    val downloadState = _downloadState.asStateFlow()

    // ── LLM state ─────────────────────────────────────────────────────────────

    private val _messages  = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _modelState = MutableStateFlow<LlmService.LoadState>(LlmService.LoadState.Idle)
    val modelState = _modelState.asStateFlow()

    // ── Audio state ───────────────────────────────────────────────────────────

    sealed class AudioState {
        data object Idle         : AudioState()
        data object Recording    : AudioState()
        data object Transcribing : AudioState()
        data object Speaking     : AudioState()
        data class  Error(val msg: String) : AudioState()
    }

    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState = _audioState.asStateFlow()

    private val _sttReady = MutableStateFlow(false)
    val sttReady = _sttReady.asStateFlow()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady = _ttsReady.asStateFlow()

    val hasMicPermission: Boolean
        get() = voiceRecorder.hasMicPermission()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        if (allModelsReady()) {
            loadAllModels()
        }

        // Load memory block asynchronously and append to system prompt
        viewModelScope.launch {
            val memoryBlock = memoryManager.buildMemoryBlock(character.id)
            fullSystemPrompt = character.systemPrompt +
                "\n\n" + com.ttt.companion.tools.ToolDefinitions.SYSTEM_PROMPT_ADDITION +
                "\n\n" + memoryBlock
        }
    }

    private fun allModelsReady(): Boolean =
        downloader.isModelReady() &&
                audioDl.areFilesReady(AudioConfig.STT_DIR, AudioConfig.STT_FILES) &&
                audioDl.areFilesReady(AudioConfig.TTS_DIR, AudioConfig.TTS_FILES)

    // ── Download sequence: LLM → STT → TTS ───────────────────────────────────

    fun startDownload() {
        viewModelScope.launch {
            // Phase 1 — LLM
            if (!downloader.isModelReady()) {
                downloader.download { state -> _downloadState.value = state }
                if (_downloadState.value is DownloadState.Failed) return@launch
            }

            // Phase 2 — STT
            if (!audioDl.areFilesReady(AudioConfig.STT_DIR, AudioConfig.STT_FILES)) {
                try {
                    audioDl.downloadAll(
                        subDir     = AudioConfig.STT_DIR,
                        files      = AudioConfig.STT_FILES,
                        phaseLabel = SetupPhase.STT.displayName
                    ) { label, pct, mbR, mbT ->
                        _downloadState.value = DownloadState.Downloading(
                            phase = SetupPhase.STT, label = label,
                            progressPct = pct, mbReceived = mbR, mbTotal = mbT
                        )
                    }
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Failed("STT: ${e.message}")
                    return@launch
                }
            }

            // Phase 3 — TTS
            if (!audioDl.areFilesReady(AudioConfig.TTS_DIR, AudioConfig.TTS_FILES)) {
                try {
                    audioDl.downloadAll(
                        subDir     = AudioConfig.TTS_DIR,
                        files      = AudioConfig.TTS_FILES,
                        phaseLabel = SetupPhase.TTS.displayName
                    ) { label, pct, mbR, mbT ->
                        _downloadState.value = DownloadState.Downloading(
                            phase = SetupPhase.TTS, label = label,
                            progressPct = pct, mbReceived = mbR, mbTotal = mbT
                        )
                    }
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Failed("TTS: ${e.message}")
                    return@launch
                }
            }

            copyVoiceSampleIfNeeded()
            _downloadState.value = DownloadState.Done
            loadAllModels()
        }
    }

    // ── Model loading ─────────────────────────────────────────────────────────

    private fun loadAllModels() {
        viewModelScope.launch {
            copyVoiceSampleIfNeeded()

            // LLM (blocking — must be first to avoid OOM on weaker devices)
            _modelState.value = LlmService.LoadState.Loading
            val llmResult = llm.loadModel(character)
            _modelState.value = llmResult
            if (llmResult is LlmService.LoadState.Error) return@launch

            // STT — Load sequentially to isolate issues and reduce peak RAM
            Log.d(TAG, "Initializing STT...")
            val sttResult = sttService.init()
            _sttReady.value = sttResult is SttService.LoadState.Ready
            if (sttResult is SttService.LoadState.Error) {
                Log.e(TAG, "STT Init Error: ${sttResult.message}")
            }

            // TTS — Heavy; load last
            Log.d(TAG, "Initializing TTS...")
            val ttsResult = ttsService.init(character.voiceSamplePath)
            _ttsReady.value = ttsResult is TtsService.LoadState.Ready
            if (ttsResult is TtsService.LoadState.Error) {
                Log.e(TAG, "TTS Init Error: ${ttsResult.message}")
            }
        }
    }

    private fun copyVoiceSampleIfNeeded() {
        val dest = File(character.voiceSamplePath)
        if (dest.exists()) return
        dest.parentFile?.mkdirs()
        try {
            getApplication<Application>().assets
                .open(AudioConfig.VOICE_SAMPLE_ASSET)
                .use { i -> dest.outputStream().use { i.copyTo(it) } }
            Log.i(TAG, "Voice sample copied to ${dest.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Voice sample not found in assets (using default voice): ${e.message}")
        }
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return
        val userMsg        = ChatMessage(role = "user", content = userText.trim())
        val updatedHistory = _messages.value + userMsg
        _messages.value    = updatedHistory
        _isLoading.value   = true

        viewModelScope.launch {
            try {
                val rawResponse = llm.chat(
                    history = updatedHistory,
                    systemPrompt = fullSystemPrompt
                )

                // Check for a tool call
                val parseResult = com.ttt.companion.tools.ToolCallParser.parse(rawResponse)
                parseResult.toolCall?.let { call ->
                    toolRouter.execute(call) // fire and forget
                }

                val response = parseResult.cleanedResponse
                val assistantMsg = ChatMessage(role = "assistant", content = response)
                _messages.value  = updatedHistory + assistantMsg

                // Speak via TTS if ready
                if (_ttsReady.value) {
                    _audioState.value = AudioState.Speaking
                    ttsService.speak(response)
                    _audioState.value = AudioState.Idle
                }
            } catch (e: Exception) {
                _messages.value = updatedHistory + ChatMessage(
                    role = "assistant", content = "[Error: ${e.message}]"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Voice input ───────────────────────────────────────────────────────────

    /** Called on mic button PRESS. Suspends until stopRecording() is called. */
    fun startRecording() {
        if (_audioState.value != AudioState.Idle) return
        _audioState.value = AudioState.Recording
        viewModelScope.launch {
            val wavFile = voiceRecorder.startRecording()
            if (wavFile == null) {
                _audioState.value = AudioState.Error("Microphone permission required")
                return@launch
            }
            _audioState.value = AudioState.Transcribing
            val (samples, sr) = voiceRecorder.readWavAsFloat(wavFile)
            val text          = sttService.transcribe(samples, sr)
            _audioState.value = AudioState.Idle
            if (text.isNotBlank()) sendMessage(text)
        }
    }

    /** Called on mic button RELEASE. */
    fun stopRecording() {
        voiceRecorder.stopFlag = true
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun endSession() {
        viewModelScope.launch {
            memoryManager.summarizeAndSave(character.id, _messages.value, llm)
        }
    }

    override fun onCleared() {
        llm.unload()
        sttService.release()
        ttsService.release()
        super.onCleared()
    }
}