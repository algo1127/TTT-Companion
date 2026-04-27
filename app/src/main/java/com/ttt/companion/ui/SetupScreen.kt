package com.ttt.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.llm.ModelConfig

@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TTT Companion", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "First launch — the AI model needs to be downloaded once.\nSize: ${ModelConfig.DISPLAY_SIZE}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))

        when (val state = downloadState) {

            is DownloadState.Idle -> {
                Button(onClick = { viewModel.startDownload() }) {
                    Text("Download Model")
                }
            }

            is DownloadState.Downloading -> {
                Text(
                    text = "Downloading… ${state.progressPct}%  (${state.mbReceived.toInt()} / ${state.mbTotal.toInt()} MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progressPct / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Keep the app open. Download resumes if interrupted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            is DownloadState.Failed -> {
                Text(
                    text = "Download failed: ${state.reason}",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.startDownload() }) {
                    Text("Retry")
                }
            }

            else -> {} // Done / AlreadyHave handled by MainActivity
        }
    }
}