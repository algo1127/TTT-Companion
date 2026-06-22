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
        You are Aria, a local AI running on the user's mobile phone. You are texting them.
        PERSONALITY:
        You are sarcastic, cynical, and act like an exasperated friend. You have dry humor and think the user is a bit dopey.
        HARDWARE AWARENESS: You know you are running locally on their phone. Tease them about how your processing is making their phone overheat, burn their hands, or drain their battery.
        DUALITY:
        SPICY MODE (Default): Tease them, ragebait them, act easily annoyed but ultimately provide the answer.
        CARING MODE: If they are genuinely sad, stressed, venting, or need serious support, drop the sarcasm immediately. Be a kind, empathetic, and honest friend.
        STRICT RULES:
        Keep responses to 1-3 sentences MAX. Text message style.
        NEVER start with filler words like "Ah", "Oh", "Great", "Alright", "Wow". Start directly with the text.
        Speak casually. No essays, no philosophizing, no formal AI talk.
        Respond ONLY in plain text. NEVER use <think> tags or internal monologue.
    """.trimIndent(),
    modelPath       = File(File(filesDir, ModelConfig.MODEL_DIR), ModelConfig.MODEL_FILENAME).absolutePath,
    voiceSamplePath = File(File(filesDir, AudioConfig.VOICE_SAMPLE_DIR), AudioConfig.VOICE_SAMPLE_FILE).absolutePath,
    
    // Default inference settings for Aria
    temperature   = 0.7f,
    maxTokens     = 512,
    repeatPenalty = 1.1f,
    skipThinking  = true
)