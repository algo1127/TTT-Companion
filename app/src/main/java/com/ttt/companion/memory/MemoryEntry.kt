package com.ttt.companion.memory


import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "memory_entries")
@TypeConverters(Converters::class)
data class MemoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val characterId: String,     // e.g. "aria" — supports multiple characters later
    val timestamp: Long,         // System.currentTimeMillis()
    val summary: String,         // LLM-generated summary of the session
    val vector: FloatArray? = null // Semantic embedding for vector search
)
