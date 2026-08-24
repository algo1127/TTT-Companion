package com.ttt.companion

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.ui.MainViewModel
import com.ttt.companion.ui.SetupScreen
import com.ttt.companion.ui.TestScreen
import com.ttt.companion.ui.VrmScreen
import com.ttt.companion.ui.SettingsScreen
import com.ttt.companion.ui.CharacterScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // ── Microphone permission launcher ────────────────────────────────────────
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // MainViewModel reads hasMicPermission live via ContextCompat — no extra action needed.
        // The mic button in TestScreen will enable itself on next recomposition.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request mic permission early so it's ready by the time the chat screen appears.
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

                if (downloadState == DownloadState.Done || downloadState == DownloadState.AlreadyHave) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            MainViewModel.Screen.VRM -> VrmScreen(viewModel)
                            MainViewModel.Screen.SETTINGS -> SettingsScreen(viewModel)
                            MainViewModel.Screen.CHARACTER -> CharacterScreen(viewModel)
                            else -> VrmScreen(viewModel) // Fallback
                        }
                    }
                } else {
                    SetupScreen(viewModel)
                }
            }
        }
    }
}