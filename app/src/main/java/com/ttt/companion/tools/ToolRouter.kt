package com.ttt.companion.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

class ToolRouter(private val context: Context) {

    private val tag = "ToolRouter"

    /**
     * Executes [call] and returns a short result string that can be
     * fed back to the LLM or just logged.
     */
    fun execute(call: ParsedToolCall): String {
        return try {
            when (call.tool) {
                "set_alarm" -> setAlarm(call)
                "set_timer" -> setTimer(call)
                "toggle_torch" -> toggleTorch(call)
                "vibrate" -> vibrate(call)
                "open_app" -> openApp(call)
                else -> {
                    Log.w(tag, "Unknown tool: ${call.tool}")
                    "Unknown tool"
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Tool execution failed for ${call.tool}", e)
            "Tool execution failed"
        }
    }

    /**
     * Toggles the device flashlight.
     */
    private fun toggleTorch(call: ParsedToolCall): String {
        val on = call.args["on"]?.jsonPrimitive?.booleanOrNull ?: true
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return "No camera found"
            cameraManager.setTorchMode(cameraId, on)
            val status = if (on) "on" else "off"
            Log.i(tag, "Torch turned $status")
            "Flashlight turned $status"
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle torch", e)
            "Could not control flashlight"
        }
    }

    /**
     * Vibrates the phone for a specified duration.
     */
    private fun vibrate(call: ParsedToolCall): String {
        val duration = call.args["duration_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 500L
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        return if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            Log.i(tag, "Vibrated for ${duration}ms")
            "Vibrated phone"
        } else {
            "Device does not support vibration"
        }
    }

    /**
     * Attempts to open an app by package name or common nickname.
     */
    private fun openApp(call: ParsedToolCall): String {
        val pkg = call.args["package_name"]?.jsonPrimitive?.content?.lowercase(Locale.ROOT) ?: return "Missing app name"
        
        Log.d(tag, "Attempting to open app: $pkg")

        // Map common names to packages or special intents
        val target = when (pkg) {
            "youtube" -> "com.google.android.youtube"
            "maps" -> "com.google.android.apps.maps"
            "browser", "chrome" -> "com.android.chrome"
            "music", "spotify" -> "com.spotify.music"
            "gmail", "email" -> "com.google.android.gm"
            "calendar" -> "com.google.android.calendar"
            "phone", "dialer" -> "com.google.android.dialer"
            "messages", "sms" -> "com.google.android.apps.messaging"
            "camera" -> "ACTION_CAMERA" // special internal flag
            else -> pkg
        }

        return try {
            if (target == "ACTION_CAMERA") {
                val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    "Opening camera..."
                } else {
                    "Camera app not found"
                }
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(target)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    "Opening $pkg..."
                } else {
                    Log.w(tag, "App not installed: $target")
                    "App not found: $pkg"
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to open app $pkg ($target)", e)
            "Error opening app"
        }
    }

    /**
     * Opens Android's "Set Alarm" intent via AlarmClock — this shows the
     * system alarm UI rather than silently creating one, which is the
     * documented, permission-friendly way to do this from a regular app.
     */
    private fun setAlarm(call: ParsedToolCall): String {
        val hour   = call.args["hour"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: return "Missing hour"
        val minute = call.args["minute"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 0
        val label  = call.args["label"]?.jsonPrimitive?.content ?: "TTT Companion"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true) // don't show the clock app — just create it
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        Log.i(tag, "Alarm set: $hour:$minute - $label")
        return "Alarm set for ${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}"
    }

    /**
     * Opens Android's "Set Timer" intent via AlarmClock.
     */
    private fun setTimer(call: ParsedToolCall): String {
        val seconds = call.args["seconds"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() 
            ?: return "Missing or invalid seconds"
        val label   = call.args["label"]?.jsonPrimitive?.content ?: "Timer"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Log.i(tag, "Timer started: ${seconds}s - $label")
            "Timer set for $seconds seconds"
        } catch (e: Exception) {
            Log.e(tag, "Failed to start timer", e)
            "Failed to start timer: ${e.message}"
        }
    }
}

// AlarmClock constants live in android.provider — explicit import for clarity
private typealias AlarmClock = android.provider.AlarmClock