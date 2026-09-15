package com.ttt.companion.memory

import android.content.Context
import java.util.Locale

/**
 * Enhanced memory manager that uses "Semantic keyword overlap" to retrieve
 * relevant memories rather than just the most recent ones.
 */
class VectorMemoryManager(context: Context) {

    private val dao = AppDatabase.get(context).memoryDao()
    private val embeddingEngine = EmbeddingEngine(context)

    /**
     * Finds the most relevant memories using true Vector Similarity.
     */
    suspend fun findRelevant(characterId: String, query: String, limit: Int = 3): String {
        val allMemories = dao.getAll(characterId)
        if (allMemories.isEmpty()) return ""

        val queryVector = embeddingEngine.embed(query)

        val scored = allMemories.mapNotNull { entry ->
            val vector = entry.vector ?: return@mapNotNull null
            val score = embeddingEngine.calculateSimilarity(queryVector, vector)
            entry to score
        }.filter { it.second > 0.45f } // Similarity threshold
         .sortedByDescending { it.second }
         .take(limit)

        if (scored.isEmpty()) return ""

        return buildString {
            scored.forEach { (entry, _) ->
                append("- ").append(entry.summary.trim()).append("\n")
            }
        }
    }

    /**
     * Used by the Memory Architect to save a new fact and prune conflicting ones.
     */
    suspend fun saveFact(characterId: String, text: String) {
        val newVector = embeddingEngine.embed(text)
        
        // Pruning logic: If we find a very high similarity match (0.85+), 
        // it's likely an update or contradiction.
        val existing = dao.getAll(characterId)
        existing.forEach { entry ->
            entry.vector?.let { v ->
                val similarity = embeddingEngine.calculateSimilarity(newVector, v)
                if (similarity > 0.85f) {
                    dao.delete(entry)
                }
            }
        }

        dao.insert(MemoryEntry(
            characterId = characterId,
            timestamp = System.currentTimeMillis(),
            summary = text,
            vector = newVector
        ))
    }
}
