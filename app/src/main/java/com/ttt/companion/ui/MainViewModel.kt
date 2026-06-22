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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import com.ttt.companion.vrm.VrmAssetHelper

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

    private val _vrmUrl = MutableStateFlow<String?>(null)
    val vrmUrl = _vrmUrl.asStateFlow()

    private val _vrmActive = MutableStateFlow(true)
    val vrmActive = _vrmActive.asStateFlow()

    private val _vrmLoading = MutableStateFlow(true)
    val vrmLoading = _vrmLoading.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    val character = defaultCharacter(app.filesDir)

    // Build the full system prompt.
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
        // 3. Load memory asynchronously
        viewModelScope.launch {
            val memoryBlock = memoryManager.buildMemoryBlock(character.id)
            fullSystemPrompt = character.systemPrompt +
                "\n\n" + com.ttt.companion.tools.ToolDefinitions.SYSTEM_PROMPT_ADDITION +
                "\n\n" + memoryBlock
        }
    }

    fun startVrm() {
        if (!vrmActive.value || _vrmUrl.value != null) return
        viewModelScope.launch {
            Log.d(TAG, "Starting VRM load sequence...")
            val url = VrmAssetHelper.ensureVrm(getApplication(), character.id)
            _vrmUrl.value = url
        }
    }

    private fun allModelsReady(): Boolean =
        downloader.isModelReady() &&
                audioDl.areFilesReady(AudioConfig.STT_DIR, AudioConfig.STT_FILES) &&
                audioDl.areFilesReady(AudioConfig.TTS_DIR, AudioConfig.TTS_FILES)

    // ── Download sequence ────────────────────────────────────────────────────

    fun startDownload() {
        viewModelScope.launch {
            if (!downloader.isModelReady()) {
                downloader.download { state -> _downloadState.value = state }
                if (_downloadState.value is DownloadState.Failed) return@launch
            }
            if (!audioDl.areFilesReady(AudioConfig.STT_DIR, AudioConfig.STT_FILES)) {
                try {
                    audioDl.downloadAll(AudioConfig.STT_DIR, AudioConfig.STT_FILES, SetupPhase.STT.displayName) { l, p, r, t ->
                        _downloadState.value = DownloadState.Downloading(SetupPhase.STT, l, p, r, t)
                    }
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Failed("STT: ${e.message}"); return@launch
                }
            }
            if (!audioDl.areFilesReady(AudioConfig.TTS_DIR, AudioConfig.TTS_FILES)) {
                try {
                    audioDl.downloadAll(AudioConfig.TTS_DIR, AudioConfig.TTS_FILES, SetupPhase.TTS.displayName) { l, p, r, t ->
                        _downloadState.value = DownloadState.Downloading(SetupPhase.TTS, l, p, r, t)
                    }
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Failed("TTS: ${e.message}"); return@launch
                }
            }
            copyVoiceSampleIfNeeded()
            _downloadState.value = DownloadState.Done
            loadAllModels()
        }
    }

    // ── Model loading ─────────────────────────────────────────────────────────

    private fun loadAllModels() {
        viewModelScope.launch(Dispatchers.IO) {
            copyVoiceSampleIfNeeded()
            _modelState.value = LlmService.LoadState.Loading
            val llmResult = llm.loadModel(character)
            withContext(Dispatchers.Main) { _modelState.value = llmResult }
            if (llmResult is LlmService.LoadState.Error) return@launch

            kotlinx.coroutines.delay(500)
            Log.d(TAG, "Initializing STT...")
            val sttResult = sttService.init()
            withContext(Dispatchers.Main) { _sttReady.value = sttResult is SttService.LoadState.Ready }

            Log.d(TAG, "Initializing TTS...")
            val ttsResult = ttsService.init(character.voiceSamplePath)
            withContext(Dispatchers.Main) { _ttsReady.value = ttsResult is TtsService.LoadState.Ready }
        }
    }

    private fun copyVoiceSampleIfNeeded() {
        val assetPath = AudioConfig.VOICE_SAMPLE_ASSET
        val dest = File(character.voiceSamplePath)

        // FOR NOW: Always refresh from assets on launch
        Log.i(TAG, "Refreshing voice sample from APK...")
        dest.parentFile?.mkdirs()
        try {
            getApplication<Application>().assets
                .open(assetPath)
                .use { i -> dest.outputStream().use { i.copyTo(it) } }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh voice sample: ${e.message}")
        }
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return
        val userMsg = ChatMessage(role = "user", content = userText.trim())
        val updatedHistory = _messages.value + userMsg
        _messages.value = updatedHistory
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val rawResponse = llm.chat(updatedHistory, fullSystemPrompt)
                val parseResult = com.ttt.companion.tools.ToolCallParser.parse(rawResponse)
                parseResult.toolCall?.let { toolRouter.execute(it) }

                val response = parseResult.cleanedResponse
                val assistantMsg = ChatMessage(role = "assistant", content = response)
                _messages.value = updatedHistory + assistantMsg

                if (_ttsReady.value) {
                    _audioState.value = AudioState.Speaking
                    _isSpeaking.value = true
                    startLipSync()
                    try {
                        ttsService.speak(response)
                    } finally {
                        _isSpeaking.value = false
                        _audioState.value = AudioState.Idle
                    }
                }
            } catch (e: Exception) {
                _messages.value = updatedHistory + ChatMessage(role = "assistant", content = "[Error: ${e.message}]")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Voice input ───────────────────────────────────────────────────────────

    fun startRecording() {
        if (_audioState.value != AudioState.Idle) return
        _audioState.value = AudioState.Recording
        _isSpeaking.value = true
        viewModelScope.launch {
            val wavFile = voiceRecorder.startRecording()
            if (wavFile == null) {
                _audioState.value = AudioState.Error("Mic permission required")
                _isSpeaking.value = false; return@launch
            }
            _audioState.value = AudioState.Transcribing
            val (samples, sr) = voiceRecorder.readWavAsFloat(wavFile)
            val text = sttService.transcribe(samples, sr)
            _audioState.value = AudioState.Idle
            _isSpeaking.value = false
            if (text.isNotBlank()) sendMessage(text)
        }
    }

    fun stopRecording() { voiceRecorder.stopFlag = true }

    fun toggleMic() {
        if (_isSpeaking.value || _audioState.value == AudioState.Recording) {
            stopLipSync(); stopRecording()
        } else {
            startRecording()
        }
    }

    // ── VRM / Lip sync ────────────────────────────────────────────────────────

    private var vrmLoadedOnce = false

    fun skipVrm() {
        if (vrmLoadedOnce) return
        Log.i(TAG, "⏩ Skipping VRM loading, starting LLM immediately")
        vrmLoadedOnce = true
        _vrmActive.value = false
        _vrmUrl.value = null
        _vrmLoading.value = false
        if (allModelsReady()) {
            loadAllModels()
        }
    }

    fun onVrmLoaded() {
        if (vrmLoadedOnce) return
        vrmLoadedOnce = true
        _vrmLoading.value = false
        Log.i(TAG, "✅ VRM loaded successfully")
        // Ahora que la GPU está estable y el 3D se ve, cargamos la IA de forma segura
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // Aumentado a 2s para evitar caídas de frames iniciales
            if (allModelsReady()) {
                loadAllModels()
            }
        }
    }
    fun onVrmError(msg: String) {
        Log.e(TAG, "❌ VRM error: $msg")
        // If VRM fails, we still want the AI to work
        skipVrm()
    }

    fun startLipSync() {
        // SceneView will handle morph targets internally or via frame update
    }

    fun stopLipSync() { _isSpeaking.value = false }

    fun endSession() {
        viewModelScope.launch {
            memoryManager.summarizeAndSave(character.id, _messages.value, llm)
        }
    }

    override fun onCleared() {
        llm.unload(); sttService.release(); ttsService.release()
        super.onCleared()
    }
}
