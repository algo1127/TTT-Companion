package com.ttt.companion.ui

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.LlmService

// Translucent black for the overlay panels
private val PANEL_BG = Color(0xCC000000)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VrmScreen(viewModel: MainViewModel) {
    val messages     by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val modelState   by viewModel.modelState.collectAsStateWithLifecycle()
    val vrmUrl       by viewModel.vrmUrl.collectAsStateWithLifecycle()
    val vrmActive    by viewModel.vrmActive.collectAsStateWithLifecycle()
    val vrmLoading   by viewModel.vrmLoading.collectAsStateWithLifecycle()

    // UI State
    var expanded     by remember { mutableStateOf(false) }
    var inputText    by remember { mutableStateOf("") }
    val listState    = rememberLazyListState()
    val isSpeaking   by viewModel.isSpeaking.collectAsStateWithLifecycle()

    // Load VRM on entry
    LaunchedEffect(Unit) {
        viewModel.startVrm()
    }

    // Auto-scroll messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {

        // ── Native 3D View ─────────────
        if (vrmActive) {
            VrmSceneView(
                modelPath = vrmUrl,
                isSpeaking = isSpeaking,
                modifier = Modifier.fillMaxSize(),
                onLoaded = {
                    viewModel.onVrmLoaded()
                },
                onError = { msg -> viewModel.onVrmError(msg) }
            )
        }

        // ── Loading Overlays ───────────────────────────────────
        if (vrmLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF6699FF))
                    Spacer(Modifier.height(16.dp))
                    Text("Loading 3D Avatar...", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.skipVrm() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333344))
                    ) {
                        Text("Skip VRM & Start AI", color = Color.White)
                    }
                }
            }
        } else if (modelState is LlmService.LoadState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFB0C8FF))
                    Spacer(Modifier.height(16.dp))
                    Text("Starting AI Engine...", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // ── Bottom overlay bar ─────────────────────────────────

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(PANEL_BG)
        ) {
            // Interactive Handle Area (Tap or Drag)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -15) expanded = true
                            if (dragAmount > 15) expanded = false
                        }
                    }
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = if (expanded) 0.5f else 0.3f))
                )
            }

            // Message history — only when expanded
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                exit  = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeOut()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        Text(
                            text = "${if (isUser) "You" else viewModel.character.name}: ${msg.content}",
                            color = if (isUser) Color.White else Color(0xFFB0C8FF),
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isUser) TextAlign.End else TextAlign.Start
                        )
                    }
                    if (isLoading) {
                        item {
                            Text(
                                text = "${viewModel.character.name} is thinking...",
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Input row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val ready = modelState == LlmService.LoadState.Ready && !isLoading

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        if (!expanded) expanded = true // auto-expand when typing
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Say something...", color = Color(0xFF666666))
                    },
                    enabled = ready,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF6699FF),
                        unfocusedBorderColor = Color(0xFF333344),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = Color(0xFF6699FF)
                    )
                )

                // Send button
                IconButton(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = ready && inputText.isNotBlank()
                ) {
                    Text("➤", fontSize = 20.sp, color = if (ready) Color(0xFF6699FF) else Color(0xFF333333))
                }

                // Mic button
                val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
                IconButton(
                    onClick = { viewModel.toggleMic() },
                    enabled = ready
                ) {
                    Text(
                        if (isSpeaking) "⏹" else "🎤",
                        fontSize = 20.sp,
                        color = if (isSpeaking) Color(0xFFFF6666) else Color.White
                    )
                }
            }

            // Navigation bar spacer
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
