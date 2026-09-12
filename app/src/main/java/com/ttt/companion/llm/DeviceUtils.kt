package com.ttt.companion.llm

import android.os.Build
import android.util.Log

object DeviceUtils {
    private const val TAG = "DeviceUtils"

    fun isSnapdragonDevice(): Boolean {
        val board = Build.BOARD.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        Log.d(TAG, "Hardware Detection - Board: $board, Hardware: $hardware, Manufacturer: $manufacturer")

        val isQcom = board.contains("qcom") || 
                     hardware.contains("qcom") || 
                     hardware.contains("snapdragon") ||
                     board.contains("msm") || 
                     board.contains("apq") || 
                     board.contains("sm") // e.g., sm8650 for Gen 3

        // Basic check for architecture - GenieX is arm64 only
        val isArm64 = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")

        return isQcom && isArm64
    }
    
    fun isGenieXAvailable(): Boolean {
        return try {
            Class.forName("com.geniex.sdk.LlmWrapper")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
