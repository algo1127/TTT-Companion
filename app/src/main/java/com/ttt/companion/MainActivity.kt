package com.ttt.companion

import android.Manifest
import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.ui.MainViewModel
import com.ttt.companion.ui.SetupScreen
import com.ttt.companion.ui.VrmScreen
import com.ttt.companion.ui.CharacterScreen
import com.ttt.companion.ui.SettingsScreen
import com.ttt.companion.ui.AppModeSelectionScreen
import com.ttt.companion.ui.Chat2DScreen
import com.ttt.companion.ui.UserScreen
import com.ttt.companion.ui.TestScreen
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

    // --- Microphone permission launcher -------------------------------------
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val appMode by viewModel.appMode.collectAsStateWithLifecycle()
                val useLegacyTest by viewModel.useLegacyTestScreen.collectAsStateWithLifecycle()

                if (downloadState == DownloadState.Done || downloadState == DownloadState.AlreadyHave) {
                    if (appMode == MainViewModel.AppMode.UNSET) {
                        AppModeSelectionScreen(onModeSelected = { viewModel.setAppMode(it) })
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Always keep the active chat mode in background
                            if (appMode == MainViewModel.AppMode.MODE_3D) {
                                VrmScreen(viewModel)
                            } else {
                                if (useLegacyTest) {
                                    TestScreen(viewModel)
                                } else {
                                    Chat2DScreen(viewModel)
                                }
                            }

                            // Show other screens as overlays
                            AnimatedVisibility(
                                visible = currentScreen == MainViewModel.Screen.CHARACTER,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                CharacterScreen(viewModel)
                            }

                            AnimatedVisibility(
                                visible = currentScreen == MainViewModel.Screen.SETTINGS,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                SettingsScreen(viewModel)
                            }

                            AnimatedVisibility(
                                visible = currentScreen == MainViewModel.Screen.USER,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                UserScreen(viewModel)
                            }
                        }
                    }
                } else {
                    SetupScreen(viewModel)
                }
            }
        }
    }
}
