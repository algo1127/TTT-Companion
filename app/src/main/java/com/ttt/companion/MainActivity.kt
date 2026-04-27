package com.ttt.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.ui.MainViewModel
import com.ttt.companion.ui.SetupScreen
import com.ttt.companion.ui.TestScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // Route: show setup until model is downloaded, then show chat
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