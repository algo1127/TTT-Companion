package com.ttt.companion

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.ui.MainViewModel
import com.ttt.companion.ui.SetupScreen
import com.ttt.companion.ui.TestScreen

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

        setContent {
            MaterialTheme {
                val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
                if (downloadState == DownloadState.Done || downloadState == DownloadState.AlreadyHave) {
                    TestScreen(viewModel)
                } else {
                    SetupScreen(viewModel)
                }
            }
        }
    }
}