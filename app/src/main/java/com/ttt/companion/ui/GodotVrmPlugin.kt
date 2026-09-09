package com.ttt.companion.ui

import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class GodotVrmPlugin(godot: Godot) : GodotPlugin(godot) {

    override fun getPluginName() = "GodotVrmPlugin"

    private var onVrmLoadedCallback: (() -> Unit)? = null
    private var onCameraMovedCallback: ((Float, Float, Float) -> Unit)? = null

    fun setOnVrmLoadedCallback(callback: () -> Unit) {
        onVrmLoadedCallback = callback
    }

    fun setOnCameraMovedCallback(callback: (Float, Float, Float) -> Unit) {
        onCameraMovedCallback = callback
    }

    @UsedByGodot
    fun onVrmLoaded() {
        Log.d("GodotVrmPlugin", "VRM Loaded Signal Received")
        onVrmLoadedCallback?.invoke()
    }

    @UsedByGodot
    fun onCameraMoved(rx: Float, ry: Float, zoom: Float) {
        onCameraMovedCallback?.invoke(rx, ry, zoom)
    }

    fun loadVrm(path: String) {
        emitSignal("load_vrm_requested", path)
    }

    fun loadAnim(path: String) {
        emitSignal("load_anim_requested", path)
    }

    fun setSpeaking(speaking: Boolean) {
        emitSignal("speaking_changed", speaking)
    }

    fun setCameraLocked(locked: Boolean) {
        emitSignal("camera_lock_changed", locked)
    }

    fun setInitialCamera(rx: Float, ry: Float, zoom: Float) {
        emitSignal("camera_init_requested", rx, ry, zoom)
    }

    override fun getPluginSignals(): Set<org.godotengine.godot.plugin.SignalInfo> {
        return setOf(
            org.godotengine.godot.plugin.SignalInfo("load_vrm_requested", String::class.java),
            org.godotengine.godot.plugin.SignalInfo("load_anim_requested", String::class.java),
            org.godotengine.godot.plugin.SignalInfo("speaking_changed", Boolean::class.javaObjectType),
            org.godotengine.godot.plugin.SignalInfo("camera_lock_changed", Boolean::class.javaObjectType),
            org.godotengine.godot.plugin.SignalInfo("camera_init_requested", Float::class.javaObjectType, Float::class.javaObjectType, Float::class.javaObjectType)
        )
    }
}
