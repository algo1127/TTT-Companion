package com.ttt.companion.memory

import android.content.Context
import com.ttt.companion.llm.LlmService
import com.ttt.companion.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryManager(context: Context) {

    private val dao = AppDatabase.get(context).memoryDao()
    private val vectorManager = VectorMemoryManager(context)
    private val architect = MemoryArchitect(context)

    private val prefs = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)

    /**
     * Build the "[Persistent Memory]" block to inject into the system prompt.
     * Returns empty string if there's no memory yet (first ever session).
     */
    suspend fun buildMemoryBlock(characterId: String): String {
        // Use the vector manager for semantic retrieval
        // This makes the response context-aware rather than just recent-aware
        return "" // Placeholder for now, LlmService calls findRelevant directly
    }

    /**
     * Ask the Architect to summarize the session, then save it.
     */
    suspend fun summarizeAndSave(
        characterId: String,
        history: List<ChatMessage>,
        llm: LlmService
    ) = withContext(Dispatchers.IO) {
        if (history.size < 2) return@withContext

        val cognitiveEnabled = prefs.getBoolean("cognitive_memory_enabled", false)

        if (cognitiveEnabled) {
            android.util.Log.i("MemoryManager", "Cognitive Swap initiated: Unloading main LLM...")
            llm.unload()
            
            try {
                android.util.Log.i("MemoryManager", "Architect: Extracting facts from session...")
                val facts = architect.process(history)
                facts.forEach { fact ->
                    android.util.Log.i("MemoryManager", "Architect: Saving atomic fact: $fact")
                    vectorManager.saveFact(characterId, fact)
                }
            } finally {
                android.util.Log.i("MemoryManager", "Cognitive Swap complete: Reloading main LLM...")
                llm.reloadLastModel()
            }
        }
    }

    suspend fun getAll(characterId: String): List<MemoryEntry> = withContext(Dispatchers.IO) {
        dao.getAll(characterId)
    }

    suspend fun deleteMemory(entry: MemoryEntry) = withContext(Dispatchers.IO) {
        dao.delete(entry)
    }

    suspend fun saveManual(characterId: String, text: String, llm: LlmService) = withContext(Dispatchers.IO) {
        val cognitiveEnabled = prefs.getBoolean("cognitive_memory_enabled", false)
        
        if (cognitiveEnabled) {
            android.util.Log.i("MemoryManager", "Manual Swap initiated: Unloading main LLM...")
            llm.unload()
            
            try {
                val finalFact = architect.formatManualMemory(text)
                vectorManager.saveFact(characterId, finalFact)
            } finally {
                android.util.Log.i("MemoryManager", "Manual Swap complete: Reloading main LLM...")
                llm.reloadLastModel()
            }
        } else {
            vectorManager.saveFact(characterId, text)
        }
    }
}
