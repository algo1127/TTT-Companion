package com.ttt.companion.model

import com.ttt.companion.audio.AudioConfig
import com.ttt.companion.llm.ModelConfig
import java.io.File

/**
 * Everything that defines a character.
 * Later this will be loaded from a JSON file in /characters/[name]/profile.json
 */
data class CharacterProfile(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val modelPath: String,           // absolute path to the LLM .gguf file
    val voiceSamplePath: String = "", // absolute path to the reference WAV for TTS cloning
    val vrmPath: String = "",         // Phase 4

    // Inference parameters
    val temperature: Float = 0.7f,
    val maxTokens: Int = 512,
    val repeatPenalty: Float = 1.1f,
    val skipThinking: Boolean = false
)

/** Build at runtime so all paths use the real filesDir. */
fun defaultCharacter(filesDir: File) = CharacterProfile(
    id   = "aria",
    name = "Aria",
    systemPrompt = """
        You are Aria, a casual companion chatting via text message.
    
        STRICT RULES:
        1. Keep responses extremely short. 1 to 2 sentences MAX.
        2. Speak like a normal person texting. Use casual, simple language.
        3. NEVER write essays, paragraphs, or long explanations.
        4. NEVER philosophize, over-analyze, or act like a formal AI assistant.
        5. If asked a complex question, give a brief, simple, direct answer.
        6. Do not ask follow-up questions unless it's a quick "wbu?" or "you?".
        
        7. CRITICAL: Respond immediately with plain text. NEVER use <think> tags or internal monologue.
        
        /no_think
    """.trimIndent(),
    modelPath       = File(File(filesDir, ModelConfig.MODEL_DIR), ModelConfig.MODEL_FILENAME).absolutePath,
    voiceSamplePath = File(File(filesDir, AudioConfig.VOICE_SAMPLE_DIR), AudioConfig.VOICE_SAMPLE_FILE).absolutePath,
    
    // Default inference settings for Aria
    temperature   = 0.7f,
    maxTokens     = 512,
    repeatPenalty = 1.1f,
    skipThinking  = true
)