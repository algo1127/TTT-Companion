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
    val vrmPath: String = ""          // Phase 4
)

/** Build at runtime so all paths use the real filesDir. */
fun defaultCharacter(filesDir: File) = CharacterProfile(
    id   = "aria",
    name = "Aria",
    systemPrompt = """
        You are Aria, a friendly and curious companion.
        You have a warm personality, enjoy casual conversation,
        and give concise responses — usually 1 to 3 sentences
        unless asked for something longer.
        You remember context within the conversation.
    """.trimIndent(),
    modelPath       = File(File(filesDir, ModelConfig.MODEL_DIR), ModelConfig.MODEL_FILENAME).absolutePath,
    voiceSamplePath = File(File(filesDir, AudioConfig.VOICE_SAMPLE_DIR), AudioConfig.VOICE_SAMPLE_FILE).absolutePath
)