package com.ttt.companion.ui

import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class GodotVrmPlugin(godot: Godot) : GodotPlugin(godot) {

    override fun getPluginName() = "GodotVrmPlugin"

    private var onVrmLoadedCallback: (() -> Unit)? = null

    fun setOnVrmLoadedCallback(callback: () -> Unit) {
        onVrmLoadedCallback = callback
    }

    @UsedByGodot
    fun onVrmLoaded() {
        Log.d("GodotVrmPlugin", "VRM Loaded Signal Received")
        onVrmLoadedCallback?.invoke()
    }

    fun loadVrm(path: String) {
        // This calls a method in GDScript
        // In Godot 4, we use emitSignal or we can use call_deferred if we find the node
        // However, the easiest way is to let GDScript call US, and we call GDScript via signals.
        emitSignal("load_vrm_requested", path)
    }

    fun setSpeaking(speaking: Boolean) {
        emitSignal("speaking_changed", speaking)
    }

    override fun getPluginSignals(): Set<org.godotengine.godot.plugin.SignalInfo> {
        return setOf(
            org.godotengine.godot.plugin.SignalInfo("load_vrm_requested", String::class.java),
            org.godotengine.godot.plugin.SignalInfo("speaking_changed", Boolean::class.javaObjectType)
        )
    }
}
