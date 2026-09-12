package com.ttt.companion.ui

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import org.godotengine.godot.GodotFragment

@Composable
fun GodotVrmView(
    modelPath: String?,
    idleAnimPath: String?,
    isSpeaking: Boolean,
    isCameraLocked: Boolean,
    isGenerating: Boolean = false,
    initialCameraData: FloatArray?,
    modifier: Modifier = Modifier,
    onLoaded: () -> Unit = {},
    onCameraMoved: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity ?: return
    val fragmentManager = fragmentActivity.supportFragmentManager
    
    // Unique ID for the container
    val containerId = remember { View.generateViewId() }
    
    var godotFragment by remember { mutableStateOf<GodotFragment?>(null) }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                id = containerId
                
                // Add listener to attach fragment only when the view is part of the window
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        if (fragmentManager.findFragmentByTag("godot_fragment") == null) {
                            fragmentManager.beginTransaction()
                                .add(id, GodotFragment(), "godot_fragment")
                                .commitNowAllowingStateLoss()
                        }
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { _ -> 
            val fragment = fragmentManager.findFragmentByTag("godot_fragment") as? GodotFragment
            if (isGenerating) {
                fragment?.onPause()
            } else {
                fragment?.onResume()
            }
        }
    )

    // Keep godotFragment synced with the fragment manager
    SideEffect {
        godotFragment = fragmentManager.findFragmentByTag("godot_fragment") as? GodotFragment
    }

    // Bridge updates to Godot
    LaunchedEffect(modelPath) {
        val path = modelPath ?: return@LaunchedEffect
        val activity = fragmentActivity as? com.ttt.companion.MainActivity ?: return@LaunchedEffect
        
        while (!activity.isGodotReady || activity.getGodot() == null || activity.vrmPlugin == null) {
            kotlinx.coroutines.delay(200)
        }

        val godot = activity.getGodot() ?: return@LaunchedEffect
        val plugin = activity.vrmPlugin ?: return@LaunchedEffect
        
        plugin.setOnVrmLoadedCallback(onLoaded)
        plugin.setOnCameraMovedCallback(onCameraMoved)

        godot.runOnRenderThread {
            plugin.loadVrm(path)
            plugin.setCameraLocked(isCameraLocked)
            
            initialCameraData?.let {
                if (it.size >= 3) {
                    plugin.setInitialCamera(it[0], it[1], it[2])
                }
            }
        }
    }

    LaunchedEffect(idleAnimPath) {
        val path = idleAnimPath ?: return@LaunchedEffect
        val activity = fragmentActivity as? com.ttt.companion.MainActivity ?: return@LaunchedEffect

        while (!activity.isGodotReady || activity.getGodot() == null || activity.vrmPlugin == null) {
            kotlinx.coroutines.delay(200)
        }

        activity.getGodot()?.runOnRenderThread {
            activity.vrmPlugin?.loadAnim(path)
        }
    }

    LaunchedEffect(isSpeaking) {
        val activity = fragmentActivity as? com.ttt.companion.MainActivity ?: return@LaunchedEffect
        
        if (activity.isGodotReady) {
            activity.getGodot()?.runOnRenderThread {
                activity.vrmPlugin?.setSpeaking(isSpeaking)
            }
        }
    }

    LaunchedEffect(isCameraLocked) {
        val activity = fragmentActivity as? com.ttt.companion.MainActivity ?: return@LaunchedEffect
        
        if (activity.isGodotReady) {
            activity.getGodot()?.runOnRenderThread {
                activity.vrmPlugin?.setCameraLocked(isCameraLocked)
            }
        }
    }
}
