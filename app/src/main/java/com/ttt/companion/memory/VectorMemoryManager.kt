package com.ttt.companion.memory

import android.content.Context
import java.util.Locale

/**
 * Enhanced memory manager that uses "Semantic keyword overlap" to retrieve
 * relevant memories rather than just the most recent ones.
 */
class VectorMemoryManager(context: Context) {

    private val dao = AppDatabase.get(context).memoryDao()

    /**
     * Finds the most relevant memories for a given user [query].
     */
    suspend fun findRelevant(characterId: String, query: String, limit: Int = 3): String {
        val allMemories = dao.getAll(characterId)
        if (allMemories.isEmpty()) return ""

        val scored = allMemories.map { entry ->
            entry to calculateRelevance(entry.summary, query)
        }.filter { it.second > 0 }
         .sortedByDescending { it.second }
         .take(limit)

        if (scored.isEmpty()) return ""

        return buildString {
            scored.forEach { (entry, _) ->
                append("- ").append(entry.summary.trim()).append("\n")
            }
        }
    }

    private fun calculateRelevance(summary: String, query: String): Int {
        val stopWords = setOf("the", "a", "an", "and", "or", "but", "i", "you", "is", "was", "my", "to", "of")
        val summaryWords = summary.lowercase(Locale.ROOT).split(Regex("\\W+")).filter { it.length > 2 && it !in stopWords }.toSet()
        val queryWords = query.lowercase(Locale.ROOT).split(Regex("\\W+")).filter { it.length > 2 && it !in stopWords }
        
        return queryWords.count { it in summaryWords }
    }
}
