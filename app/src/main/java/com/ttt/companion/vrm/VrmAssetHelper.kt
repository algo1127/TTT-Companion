package com.ttt.companion.vrm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VrmAssetHelper {

    private const val TAG = "VrmAssetHelper"

    /**
     * Ensures the VRM for [characterId] is in filesDir.
     * Copies from assets if not already there.
     * Returns the absolute file:// URL ready for the WebView loader.
     */
    suspend fun ensureVrm(context: Context, characterId: String): String? =
        withContext(Dispatchers.IO) {
            val assetPath = "characters/$characterId/model.vrm"
            // SceneView's GLTF loader prefers .glb extension for proper detection
            val outPath   = "characters/$characterId/model.glb"
            val outFile   = File(context.filesDir, outPath)

            // FOR NOW: Always copy/update on launch to ensure latest asset is used
            Log.i(TAG, "Refreshing VRM asset from APK for $characterId...")
            outFile.parentFile?.mkdirs()
            try {
                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Successfully refreshed VRM: ${outFile.absolutePath} (${outFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh VRM for $characterId", e)
                if (!outFile.exists()) return@withContext null
            }

            outFile.absolutePath
        }
}