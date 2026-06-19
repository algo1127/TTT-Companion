package com.ttt.companion.memory

import androidx.room.*

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(entry: MemoryEntry)

    // Most recent summaries first — used to build the "recent context" block
    @Query("SELECT * FROM memory_entries WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(characterId: String, limit: Int = 5): List<MemoryEntry>

    @Query("SELECT COUNT(*) FROM memory_entries WHERE characterId = :characterId")
    suspend fun count(characterId: String): Int
}