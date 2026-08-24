package com.ttt.companion

import android.Manifest
import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.ui.MainViewModel
import com.ttt.companion.ui.SetupScreen
import com.ttt.companion.ui.VrmScreen
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin

class MainActivity : FragmentActivity(), GodotHost {

    private val viewModel: MainViewModel by viewModels()
    var vrmPlugin: com.ttt.companion.ui.GodotVrmPlugin? = null
    var isGodotReady = false
    
    override fun onGodotMainLoopStarted() {
        super.onGodotMainLoopStarted()
        android.util.Log.d("GodotHost", "onGodotMainLoopStarted")
        isGodotReady = true
    }

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        android.util.Log.d("GodotHost", "onGodotSetupCompleted: Engine is fully ready")
    }

    override fun getGodot(): Godot? {
        val fragment = supportFragmentManager.findFragmentByTag("godot_fragment") as? org.godotengine.godot.GodotFragment
        val g = fragment?.godot
        android.util.Log.d("GodotHost", "getGodot: $g")
        return g
    }

    override fun getActivity(): Activity = this

    override fun getCommandLine(): List<String> = emptyList()

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> {
        val plugin = com.ttt.companion.ui.GodotVrmPlugin(godot)
        vrmPlugin = plugin
        return setOf(plugin)
    }

    // ── Microphone permission launcher ────────────────────────────────────────
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation at runtime
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        if (!viewModel.hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                viewModel.endSession()
            }
        })

        setContent {
            MaterialTheme {
                val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
                if (downloadState == DownloadState.Done || downloadState == DownloadState.AlreadyHave) {
                    VrmScreen(viewModel)
                } else {
                    SetupScreen(viewModel)
                }
            }
        }
    }
}
