package com.ttt.companion.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.llm.SetupPhase

@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TTT Companion", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "First launch — three AI models need to be downloaded once.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        // Phase size summary
        PhaseList(current = (downloadState as? DownloadState.Downloading)?.phase)

        Spacer(Modifier.height(32.dp))

        when (val state = downloadState) {
            is DownloadState.Idle -> {
                Button(onClick = { viewModel.startDownload() }) {
                    Text("Download All Models")
                }
            }

            is DownloadState.Downloading -> {
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${state.progressPct}%  •  ${state.mbReceived.toInt()} / " +
                            "${if (state.mbTotal > 0) state.mbTotal.toInt().toString() + " MB" else "? MB"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progressPct / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Keep the app open. Downloads resume if interrupted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }

            is DownloadState.Failed -> {
                Text(
                    text = "Download failed:\n${state.reason}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.startDownload() }) {
                    Text("Retry")
                }
            }

            else -> {} // Done / AlreadyHave handled by MainActivity
        }
    }
}

@Composable
private fun PhaseList(current: SetupPhase?) {
    val phases = listOf(
        Triple(SetupPhase.LLM, "LLM (Qwen 3.5-2B)",            "~1.5 GB"),
        Triple(SetupPhase.STT, "Speech recognition (Whisper)", "~87 MB"),
        Triple(SetupPhase.TTS, "Voice synthesis (XTTS-v2)",    "~1.6 GB")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        phases.forEach { (phase, label, size) ->
            val isCurrent = phase == current
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                initialValue = 1f, targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulsealpha"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isCurrent) pulse else 1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCurrent) "▶ $label" else "  $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}