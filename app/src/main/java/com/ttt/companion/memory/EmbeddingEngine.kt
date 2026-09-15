package com.ttt.companion.memory

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.math.sqrt

/**
 * Handles generating vector embeddings for text facts.
 * Currently uses a simplified placeholder until an ONNX model is provided,
 * but the architecture is ready for a real transformer model.
 */
class EmbeddingEngine(private val context: Context) {

    /**
     * Generates a 384-dimensional embedding for the given [text].
     * TODO: Integrate a real ONNX embedding model (e.g. all-MiniLM-L6-v2) 
     * using the existing sherpa-onnx dependency.
     */
    fun embed(text: String): FloatArray {
        // Placeholder: deterministic hash-based vector for now to allow DB testing
        // This will be replaced with real model inference.
        val dims = 384
        val result = FloatArray(dims)
        val seed = text.hashCode().toLong()
        val random = java.util.Random(seed)
        for (i in 0 until dims) {
            result[i] = random.nextFloat() * 2 - 1
        }
        return normalize(result)
    }

    fun calculateSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
        }
        return dotProduct // Assuming normalized vectors
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
