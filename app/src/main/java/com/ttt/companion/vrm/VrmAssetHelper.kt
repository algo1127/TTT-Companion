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
     */
    suspend fun ensureVrm(context: Context, characterId: String): String? =
        withContext(Dispatchers.IO) {
            val assetPath = "characters/$characterId/model.vrm"
            val outPath   = "characters/$characterId/model.vrm"
            val outFile   = File(context.filesDir, outPath)

            Log.i(TAG, "Refreshing VRM asset from APK for $characterId...")
            outFile.parentFile?.mkdirs()
            try {
                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Successfully refreshed VRM: ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh VRM for $characterId", e)
                if (!outFile.exists()) return@withContext null
            }

            outFile.absolutePath
        }

    /**
     * Ensures the VRMA animation file is in filesDir.
     */
    suspend fun ensureAnim(context: Context, animName: String): String? =
        withContext(Dispatchers.IO) {
            val assetPath = "anim/$animName.vrma"
            val outPath   = "anim/$animName.vrma"
            val outFile   = File(context.filesDir, outPath)

            Log.i(TAG, "Refreshing animation asset from APK: $animName...")
            outFile.parentFile?.mkdirs()
            try {
                context.assets.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Successfully refreshed Anim: ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh Anim: $animName", e)
                if (!outFile.exists()) return@withContext null
            }
            outFile.absolutePath
        }
}
