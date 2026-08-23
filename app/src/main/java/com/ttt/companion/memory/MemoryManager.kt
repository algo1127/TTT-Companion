package com.ttt.companion.memory

import android.content.Context
import com.ttt.companion.llm.LlmService
import com.ttt.companion.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryManager(context: Context) {

    private val dao = AppDatabase.get(context).memoryDao()

    /**
     * Build the "[Persistent Memory]" block to inject into the system prompt.
     * Returns empty string if there's no memory yet (first ever session).
     */
    suspend fun buildMemoryBlock(characterId: String): String {
        val recent = dao.getRecent(characterId, limit = 5)
        if (recent.isEmpty()) return ""

        return buildString {
            append("\n\n[Memory from previous sessions]\n")
            // Oldest first so the narrative reads chronologically
            recent.reversed().forEach { entry ->
                append("- ").append(entry.summary.trim()).append("\n")
            }
        }
    }

    /**
     * Ask the LLM to summarize the session, then save it.
     * Call this when the app goes to background or the user explicitly ends a session.
     * Safe to call with an empty history — does nothing.
     */
    suspend fun summarizeAndSave(
        characterId: String,
        history: List<ChatMessage>,
        llm: LlmService
    ) = withContext(Dispatchers.IO) {
        // Don't bother summarizing trivial sessions
        if (history.size < 2) return@withContext

        val summarizerPrompt = """
            /no_think
            Summarize the key facts, events, and preferences from this
            conversation in 1-3 short bullet points. Focus on things worth
            remembering for future conversations (names, preferences, ongoing
            topics, decisions made). Be concise. Output ONLY the bullet points,
            no preamble.
        """.trimIndent()

        try {
            val summary = llm.chat(
                history = history,
                systemPrompt = summarizerPrompt
            )

            if (summary.isNotBlank()) {
                dao.insert(
                    MemoryEntry(
                        characterId = characterId,
                        timestamp = System.currentTimeMillis(),
                        summary = summary
                    )
                )
            }
        } catch (e: Exception) {
            // Non-critical — losing one session's memory isn't fatal
            android.util.Log.e("MemoryManager", "Failed to summarize session", e)
        }
    }
}