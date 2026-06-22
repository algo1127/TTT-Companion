package com.ttt.companion.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.LlmService

@Composable
fun TestScreen(viewModel: MainViewModel) {
    val messages    by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading   by viewModel.isLoading.collectAsStateWithLifecycle()
    val modelState  by viewModel.modelState.collectAsStateWithLifecycle()
    val audioState  by viewModel.audioState.collectAsStateWithLifecycle()
    val sttReady    by viewModel.sttReady.collectAsStateWithLifecycle()
    val ttsReady    by viewModel.ttsReady.collectAsStateWithLifecycle()
    var inputText   by remember { mutableStateOf("") }
    val listState   = rememberLazyListState()

    // When STT transcribes, the text is auto-sent — nothing to pre-fill.
    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status bar ────────────────────────────────────────────────────────
        when (val state = modelState) {
            LlmService.LoadState.Loading ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is LlmService.LoadState.Error ->
                Text(
                    text = "⚠ ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            else -> {}
        }

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(viewModel.character.name, style = MaterialTheme.typography.titleLarge)

            // Indicator pills for audio model readiness
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(label = "STT", ready = sttReady)
                StatusPill(label = "TTS", ready = ttsReady)
            }
        }

        HorizontalDivider()

        // ── Messages ──────────────────────────────────────────────────────────
        LazyColumn(
            state   = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text     = msg.content,
                            modifier = Modifier.padding(10.dp, 8.dp),
                            style    = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (isLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        if (audioState is MainViewModel.AudioState.Speaking) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Speaking",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text  = "Speaking…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ── Audio feedback bar ────────────────────────────────────────────────
        AudioStateBar(audioState)

        HorizontalDivider()

        // ── Input row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text field
            OutlinedTextField(
                value         = inputText,
                onValueChange = { inputText = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Say something…") },
                enabled       = modelState == LlmService.LoadState.Ready &&
                        audioState == MainViewModel.AudioState.Idle && !isLoading,
                singleLine    = false,
                maxLines      = 4
            )

            // Send button
            Button(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = modelState == LlmService.LoadState.Ready &&
                        audioState == MainViewModel.AudioState.Idle &&
                        !isLoading && inputText.isNotBlank()
            ) {
                Text("Send")
            }

            // ── Mic button (hold to talk) ─────────────────────────────────────
            MicButton(
                audioState = audioState,
                sttReady   = sttReady,
                enabled    = modelState == LlmService.LoadState.Ready &&
                        sttReady && !isLoading &&
                        audioState in listOf(
                    MainViewModel.AudioState.Idle,
                    MainViewModel.AudioState.Recording
                ),
                onPress    = { viewModel.startRecording() },
                onRelease  = { viewModel.stopRecording() }
            )
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(label: String, ready: Boolean) {
    Surface(
        shape  = MaterialTheme.shapes.small,
        color  = if (ready) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = if (ready) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudioStateBar(audioState: MainViewModel.AudioState) {
    val text = when (audioState) {
        MainViewModel.AudioState.Recording    -> "🎙 Recording… release to send"
        MainViewModel.AudioState.Transcribing -> "⏳ Transcribing…"
        MainViewModel.AudioState.Speaking     -> "🔊 Aria is speaking…"
        is MainViewModel.AudioState.Error     -> "⚠ ${audioState.msg}"
        else                                  -> return
    }
    val color = when (audioState) {
        is MainViewModel.AudioState.Error -> MaterialTheme.colorScheme.error
        MainViewModel.AudioState.Recording -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = color.copy(alpha = 0.12f)
    ) {
        Text(
            text     = text,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth(),
            style    = MaterialTheme.typography.labelMedium,
            color    = color
        )
    }
}

@Composable
private fun MicButton(
    audioState : MainViewModel.AudioState,
    sttReady   : Boolean,
    enabled    : Boolean,
    onPress    : () -> Unit,
    onRelease  : () -> Unit
) {
    val isRecording = audioState == MainViewModel.AudioState.Recording

    // Pulse animation while recording
    val pulse by rememberInfiniteTransition(label = "micPulse").animateFloat(
        initialValue = 1f,
        targetValue  = 1.22f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    FilledIconButton(
        onClick   = { /* handled by pointerInput below */ },
        modifier  = Modifier
            .size(52.dp)
            .scale(if (isRecording) pulse else 1f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        enabled = enabled,
        colors  = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isRecording)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isRecording)
                MaterialTheme.colorScheme.onError
            else
                MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Icon(
            imageVector         = if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription  = if (isRecording) "Stop recording" else "Hold to speak"
        )
    }
}