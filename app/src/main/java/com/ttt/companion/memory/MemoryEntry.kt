package com.ttt.companion.memory


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries")
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val characterId: String,     // e.g. "aria" — supports multiple characters later
    val timestamp: Long,         // System.currentTimeMillis()
    val summary: String          // LLM-generated summary of the session
)