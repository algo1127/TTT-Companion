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

    private val _idleAnimUrl = MutableStateFlow<String?>(null)
    val idleAnimUrl = _idleAnimUrl.asStateFlow()

    private val _vrmActive = MutableStateFlow(true)
    val vrmActive = _vrmActive.asStateFlow()

    private val _vrmLoading = MutableStateFlow(true)
    val vrmLoading = _vrmLoading.asStateFlow()

    private val _isCameraLocked = MutableStateFlow(
        app.getSharedPreferences("vrm_prefs", Application.MODE_PRIVATE)
            .getBoolean("cam_locked", false)
    )
    val isCameraLocked = _isCameraLocked.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    enum class AppMode { UNSET, MODE_2D, MODE_3D }
    private val _appMode = MutableStateFlow(AppMode.UNSET)
    val appMode = _appMode.asStateFlow()

    private val _useLegacyTestScreen = MutableStateFlow(
        app.getSharedPreferences("experimental_prefs", Application.MODE_PRIVATE)
            .getBoolean("use_legacy_test", false)
    )
    val useLegacyTestScreen = _useLegacyTestScreen.asStateFlow()

    enum class Screen { VRM, SETTINGS, PROFILE, CHARACTER, TEST }
    private val _currentScreen = MutableStateFlow(Screen.VRM)
    val currentScreen = _currentScreen.asStateFlow()

    val character = defaultCharacter(app.filesDir)

    private val _customName = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getString("char_name", character.name) ?: character.name
    )
    val customName = _customName.asStateFlow()

    private val _customPrompt = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getString("char_prompt", getPromptBody(character.systemPrompt)) ?: getPromptBody(character.systemPrompt)
    )
    val customPrompt = _customPrompt.asStateFlow()

    private val _customVoiceId = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getInt("char_voice_id", character.ttsVoiceId)
    )
    val customVoiceId = _customVoiceId.asStateFlow()

    private val _customVoiceLang = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getString("char_voice_lang", character.ttsLang) ?: character.ttsLang
    )
    val customVoiceLang = _customVoiceLang.asStateFlow()

    private val _ttsSpeed = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getFloat("char_tts_speed", 1.1f)
    )
    val ttsSpeed = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getFloat("char_tts_pitch", 1.0f)
    )
    val ttsPitch = _ttsPitch.asStateFlow()

    private val _customContextSize = MutableStateFlow(
        app.getSharedPreferences("llm_prefs", Application.MODE_PRIVATE)
            .getInt("llm_context_size", 4096)
    )
    val customContextSize = _customContextSize.asStateFlow()

    // --- Voice Lab (Blending) ---
    private val _useBlending = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getBoolean("use_blending", false)
    )
    val useBlending = _useBlending.asStateFlow()

    private val _voiceA = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getInt("blend_voice_a", 37) // Alpha
    )
    val voiceA = _voiceA.asStateFlow()

    private val _voiceB = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getInt("blend_voice_b", 2) // Bella
    )
    val voiceB = _voiceB.asStateFlow()

    private val _blendRatio = MutableStateFlow(
        app.getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .getFloat("blend_ratio", 0.6f)
    )
    val blendRatio = _blendRatio.asStateFlow()

    // Build the full system prompt dynamically.
    private var fullSystemPrompt: String = buildFullPrompt(_customName.value, _customPrompt.value)

    // --- Setup / download state ---------------------------------------------

    private val _downloadState = MutableStateFlow<DownloadState>(
        if (allModelsReady()) DownloadState.Done else DownloadState.Idle
    )
    val downloadState = _downloadState.asStateFlow()

    // --- LLM state ----------------------------------------------------------

    private val _messages  = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _modelState = MutableStateFlow<LlmService.LoadState>(LlmService.LoadState.Idle)
    val modelState = _modelState.asStateFlow()

    private val _llmLoadingStatus = MutableStateFlow("Initializing system...")
    val llmLoadingStatus = _llmLoadingStatus.asStateFlow()

    private val _vrmLoadingStatus = MutableStateFlow("Waking engine...")
    val vrmLoadingStatus = _vrmLoadingStatus.asStateFlow()

    // --- Audio state --------------------------------------------------------

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

    // --- Init ---------------------------------------------------------------

    init {
        // Load memory asynchronously
        viewModelScope.launch {
            val memoryBlock = memoryManager.buildMemoryBlock(character.id)
            fullSystemPrompt = buildFullPrompt(_customName.value, _customPrompt.value) + "\n\n" + memoryBlock
        }
    }

    fun startVrm() {
        if (!vrmActive.value || _vrmUrl.value != null) return
        viewModelScope.launch {
            Log.d(TAG, "Starting VRM load sequence...")
            _vrmLoadingStatus.value = "Scanning assets for VRM data..."
            val url = VrmAssetHelper.ensureVrm(getApplication<Application>(), character.id)
            _vrmLoadingStatus.value = "Mapping humanoid bone structure..."
            _vrmUrl.value = url

            val animUrl = VrmAssetHelper.ensureAnim(getApplication<Application>(), "idle")
            _vrmLoadingStatus.value = "Initializing spring-bone physics..."
            _idleAnimUrl.value = animUrl
        }
    }

    private fun allModelsReady(): Boolean =
        downloader.isModelReady() &&
                audioDl.areFilesReady(AudioConfig.STT_DIR, AudioConfig.STT_FILES) &&
                audioDl.areFilesReady(AudioConfig.TTS_DIR, AudioConfig.TTS_FILES)

    // --- Download sequence --------------------------------------------------

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

    // --- Model loading ------------------------------------------------------

    private fun loadAllModels() {
        viewModelScope.launch(Dispatchers.IO) {
            copyVoiceSampleIfNeeded()
            _modelState.value = LlmService.LoadState.Loading
            
            _llmLoadingStatus.value = "Opening model FD for GGUF..."
            kotlinx.coroutines.delay(400)
            _llmLoadingStatus.value = "Primary ABI: arm64-v8a detected"
            kotlinx.coroutines.delay(400)
            _llmLoadingStatus.value = "Loading librnllama_v8_2_dotprod_i8mm.so"
            
            // Use current character name for the profile passed to LLM
            val activeCharacter = character.copy(
                name = _customName.value,
                systemPrompt = buildFullPrompt(_customName.value, _customPrompt.value)
            )
            
            val llmResult = llm.loadModel(activeCharacter, contextSize = _customContextSize.value)
            withContext(Dispatchers.Main) { 
                _modelState.value = llmResult 
                if (llmResult is LlmService.LoadState.Ready) {
                    _llmLoadingStatus.value = "Neural weights allocated successfully"
                }
            }
            if (llmResult is LlmService.LoadState.Error) return@launch

            kotlinx.coroutines.delay(500)
            Log.d(TAG, "Initializing STT...")
            val sttResult = sttService.init()
            withContext(Dispatchers.Main) { _sttReady.value = sttResult is SttService.LoadState.Ready }

            Log.d(TAG, "Initializing TTS with lang: ${_customVoiceLang.value} (blend=${_useBlending.value})...")
            
            val blendConfig = if (_useBlending.value) {
                TtsService.BlendConfig(
                    voiceASid = _voiceA.value,
                    voiceBSid = _voiceB.value,
                    ratio = _blendRatio.value,
                    customSid = 0 // Overwrite first slot in temp file
                )
            } else null

            val ttsResult = ttsService.init(
                character.voiceSamplePath, 
                lang = _customVoiceLang.value,
                blend = blendConfig
            )
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

    // --- Chat ---------------------------------------------------------------

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value || _isSpeaking.value) return
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
                _isLoading.value = false

                if (_ttsReady.value) {
                    _audioState.value = AudioState.Speaking
                    _isSpeaking.value = true
                    startLipSync()
                    try {
                        val cleanResponse = response
                            .replace(Regex("[\\\"']"), "") // Remove quotes
                            .replace(Regex("([.!?])"), "$1 ") // Ensure space after punctuation
                        
                        val activeVoiceId = if (_useBlending.value) 0 else _customVoiceId.value

                        ttsService.speak(
                            text = cleanResponse, 
                            voiceId = activeVoiceId,
                            speed = _ttsSpeed.value,
                            pitch = _ttsPitch.value
                        )
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

    fun previewVoice(voiceId: Int, lang: String) {
        if (!_ttsReady.value) return
        viewModelScope.launch {
            _audioState.value = AudioState.Speaking
            _isSpeaking.value = true
            try {
                val phrase = when(lang) {
                    "ja" -> "こんにちは、私の新しい声はどうですか？"
                    "zh" -> "你好，你觉得我的新声音怎么样？"
                    else -> "Hello, how does my new voice sound to you?"
                }
                
                val activeVoiceId = if (_useBlending.value && voiceId == 0) 0 else voiceId
                
                ttsService.speak(phrase, voiceId = activeVoiceId, speed = _ttsSpeed.value, pitch = _ttsPitch.value)
            } finally {
                _isSpeaking.value = false
                _audioState.value = AudioState.Idle
            }
        }
    }

    // --- Voice input --------------------------------------------------------

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

    // --- VRM / Lip sync -----------------------------------------------------

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        if (mode == AppMode.MODE_2D) {
            skipVrm()
        } else if (mode == AppMode.MODE_3D) {
            _vrmActive.value = true
            _vrmLoading.value = true
            startVrm()
        }
    }

    fun setLegacyTestScreen(enabled: Boolean) {
        _useLegacyTestScreen.value = enabled
        getApplication<Application>().getSharedPreferences("experimental_prefs", Application.MODE_PRIVATE)
            .edit()
            .putBoolean("use_legacy_test", enabled)
            .apply()
    }

    private var vrmLoadedOnce = false

    fun skipVrm() {
        if (vrmLoadedOnce) return
        Log.i(TAG, "Skipping VRM loading, starting LLM immediately")
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
        Log.i(TAG, "VRM loaded successfully")
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (allModelsReady()) {
                loadAllModels()
            }
        }
    }
    fun onVrmError(msg: String) {
        Log.e(TAG, "VRM error: $msg")
        skipVrm()
    }

    // --- Navigation ---------------------------------------------------------

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    // --- Character Settings -------------------------------------------------

    private fun getPromptBody(fullPrompt: String): String {
        val lines = fullPrompt.trimIndent().lines()
        return if (lines.isNotEmpty() && lines[0].contains("You are", ignoreCase = true)) {
            lines.drop(1).joinToString("\n").trimIndent()
        } else {
            fullPrompt
        }
    }

    private fun buildFullPrompt(name: String, body: String): String {
        val mandatoryLine = "You are $name, a local AI running on the user's mobile phone."
        return mandatoryLine + "\n" + body + "\n\n" + com.ttt.companion.tools.ToolDefinitions.SYSTEM_PROMPT_ADDITION
    }

    fun saveCharacterSettings(
        name: String, 
        promptBody: String, 
        voiceId: Int, 
        voiceLang: String, 
        speed: Float, 
        pitch: Float,
        useBlend: Boolean = false,
        vA: Int = 0,
        vB: Int = 0,
        ratio: Float = 0.5f
    ) {
        _customName.value = name
        _customPrompt.value = promptBody
        _customVoiceId.value = voiceId
        _customVoiceLang.value = voiceLang
        _ttsSpeed.value = speed
        _ttsPitch.value = pitch
        
        _useBlending.value = useBlend
        _voiceA.value = vA
        _voiceB.value = vB
        _blendRatio.value = ratio
        
        fullSystemPrompt = buildFullPrompt(name, promptBody)

        getApplication<Application>().getSharedPreferences("character_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("char_name", name)
            .putString("char_prompt", promptBody)
            .putInt("char_voice_id", voiceId)
            .putString("char_voice_lang", voiceLang)
            .putFloat("char_tts_speed", speed)
            .putFloat("char_tts_pitch", pitch)
            .putBoolean("use_blending", useBlend)
            .putInt("blend_voice_a", vA)
            .putInt("blend_voice_b", vB)
            .putFloat("blend_ratio", ratio)
            .apply()
        
        restartLlm()
    }

    fun resetCharacterSettings() {
        val defaultChar = defaultCharacter(getApplication<Application>().filesDir)
        val defaultBody = getPromptBody(defaultChar.systemPrompt)
        
        saveCharacterSettings(defaultChar.name, defaultBody, defaultChar.ttsVoiceId, defaultChar.ttsLang, 1.1f, 1.0f)
    }

    // --- Service Management -------------------------------------------------

    fun restartLlm() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Restarting LLM services...")
            llm.unload()
            sttService.release()
            ttsService.release()
            withContext(Dispatchers.Main) {
                _modelState.value = LlmService.LoadState.Idle
                _sttReady.value = false
                _ttsReady.value = false
            }
            loadAllModels()
        }
    }

    fun restartVrmEngine() {
        Log.i(TAG, "Restarting 3D engine...")
        _vrmUrl.value = null
        _vrmLoading.value = true
        vrmLoadedOnce = false
        startVrm()
    }

    fun saveLlmSettings(contextSize: Int) {
        _customContextSize.value = contextSize
        getApplication<Application>().getSharedPreferences("llm_prefs", Application.MODE_PRIVATE)
            .edit()
            .putInt("llm_context_size", contextSize)
            .apply()
        
        restartLlm()
    }

    // --- Camera Management --------------------------------------------------

    fun toggleCameraLock() {
        val newState = !_isCameraLocked.value
        _isCameraLocked.value = newState
        getApplication<Application>().getSharedPreferences("vrm_prefs", Application.MODE_PRIVATE)
            .edit()
            .putBoolean("cam_locked", newState)
            .apply()
    }

    fun saveCameraPosition(rx: Float, ry: Float, zoom: Float) {
        getApplication<Application>().getSharedPreferences("vrm_prefs", Application.MODE_PRIVATE)
            .edit()
            .putFloat("cam_rx", rx)
            .putFloat("cam_ry", ry)
            .putFloat("cam_zoom", zoom)
            .apply()
        Log.d(TAG, "Camera position saved: R($rx,$ry) Z($zoom)")
    }

    fun loadCameraPosition(): FloatArray? {
        val prefs = getApplication<Application>().getSharedPreferences("vrm_prefs", Application.MODE_PRIVATE)
        if (!prefs.contains("cam_rx")) return null
        return floatArrayOf(
            prefs.getFloat("cam_rx", 0f), 
            prefs.getFloat("cam_ry", 0f), 
            prefs.getFloat("cam_zoom", 1.3f)
        )
    }

    // --- Lip sync -----------------------------------------------------------

    fun startLipSync() {}

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
