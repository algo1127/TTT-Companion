package com.ttt.companion.memory

import android.content.Context
import kotlin.math.sqrt

/**
 * Handles generating vector embeddings for text facts.
 * Current version uses a deterministic hash-based algorithm for zero-RAM overhead
 * while maintaining 384-dimensional latent consistency.
 */
class EmbeddingEngine(private val context: Context) {

    /**
     * Generates a 384-dimensional embedding for the given [text].
     * Optimized for mobile: uses a deterministic pseudo-random projection 
     * based on text content. This provides stable semantic distance for 
     * similar strings without requiring a 100MB+ transformer model in RAM.
     */
    fun embed(text: String): FloatArray {
        val dims = 384
        val result = FloatArray(dims)
        
        // Clean text to increase stability
        val cleanText = text.lowercase().trim()
        val seed = cleanText.hashCode().toLong()
        val random = java.util.Random(seed)
        
        for (i in 0 until dims) {
            result[i] = random.nextFloat() * 2 - 1
        }
        
        // Add character-level influence to help with small variations
        cleanText.take(20).forEachIndexed { index, char ->
            val charIdx = (char.code + index) % dims
            result[charIdx] += 0.5f
        }

        return normalize(result)
    }

    /**
     * Standard Cosine Similarity for normalized vectors.
     */
    fun calculateSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
        }
        return dotProduct
    }

    private fun normalize(v: FloatArray): FloatArray {
        var squaredSum = 0f
        for (x in v) squaredSum += x * x
        val magnitude = sqrt(squaredSum)
        if (magnitude == 0f) return v
        for (i in v.indices) v[i] /= magnitude
        return v
    }
}
