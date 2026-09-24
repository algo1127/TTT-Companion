package com.ttt.companion.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.audio.AudioConfig
import com.ttt.companion.llm.DownloadState
import com.ttt.companion.llm.ModelConfig

/**
 * High-fidelity initialization screen.
 * Replaces the original basic setup with Cyberpunk aesthetics and model selection.
 */
@Composable
fun SetupScreen(viewModel: MainViewModel) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val selectedWhisperId by viewModel.selectedWhisperId.collectAsStateWithLifecycle()
    val selectedLlmId by viewModel.selectedLlmId.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1B))
    ) {
        // Neon background glow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF6699FF).copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "SYSTEM INITIALIZATION",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Text(
                "ARIA REQUIRES NEURAL WEIGHTS TO OPERATE",
                color = Color(0xFF6699FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(24.dp))

            // Whisper Model Selector
            Text(
                "SELECT SPEECH ENGINE",
                modifier = Modifier.fillMaxWidth(),
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(AudioConfig.WHISPER_VARIANTS) { variant ->
                    ModelCard(
                        variant = variant,
                        isSelected = variant.id == selectedWhisperId,
                        onSelect = { viewModel.selectWhisperModel(variant.id) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // LLM Model Selector
            Text(
                "SELECT NEURAL CORE (LLM)",
                modifier = Modifier.fillMaxWidth(),
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(ModelConfig.LLM_VARIANTS.filter { !it.isSpecialist }) { variant ->
                    LlmModelCardSmall(
                        variant = variant,
                        isSelected = variant.id == selectedLlmId,
                        onSelect = { viewModel.selectLlmModel(variant.id) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Neural Registry (Phase List)
            Text(
                "NEURAL REGISTRY",
                modifier = Modifier.fillMaxWidth(),
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            NeuralRegistryList(
                currentPhase = (downloadState as? DownloadState.Downloading)?.phase,
                selectedLlmId = selectedLlmId,
                viewModel = viewModel
            )

            Spacer(Modifier.height(24.dp))

            // Progress Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                DownloadProgressContent(state = downloadState, onStart = { viewModel.startDownload() })
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NeuralRegistryList(
    currentPhase: com.ttt.companion.llm.SetupPhase?,
    selectedLlmId: String,
    viewModel: MainViewModel
) {
    val cognitiveMemoryEnabled by viewModel.cognitiveMemoryEnabled.collectAsStateWithLifecycle()
    val pulseAlpha by rememberInfiniteTransition(label = "p").animateFloat(
        1f, 0.4f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
    )

    val llmVariant = ModelConfig.getLlmVariant(selectedLlmId)
    val phases = mutableListOf(
        com.ttt.companion.llm.SetupPhase.LLM to "Main Core (${llmVariant.displayName})",
    )
    
    if (cognitiveMemoryEnabled) {
        phases.add(com.ttt.companion.llm.SetupPhase.LLM to "Memory Architect (Qwen 0.5B)")
    }
    
    phases.add(com.ttt.companion.llm.SetupPhase.STT to "Speech Processor (Whisper)")
    phases.add(com.ttt.companion.llm.SetupPhase.TTS to "Vocal Synthesis (Kokoro)")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        phases.forEach { (phase, label) ->
            val isActive = phase == currentPhase
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isActive) pulseAlpha else if (currentPhase == null) 1f else 0.4f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActive) ">> $label" else "   $label",
                    color = if (isActive) Color(0xFF6699FF) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                if (isActive) {
                    Text("SYNCHRONIZING...", color = Color(0xFF6699FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else if (currentPhase != null && phase.ordinal < currentPhase.ordinal) {
                    Icon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun LlmModelCardSmall(variant: ModelConfig.ModelVariant, isSelected: Boolean, onSelect: () -> Unit) {
    val borderColor = if (isSelected) Color(0xFF6699FF) else Color.White.copy(alpha = 0.1f)
    val bgColor = if (isSelected) Color(0xFF6699FF).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.02f)

    Surface(
        modifier = Modifier
            .width(180.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint = if (isSelected) Color(0xFF6699FF) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(variant.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            if (variant.isRecommended) {
                Surface(color = Color.Green.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("RECOMMENDED", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            } else if (!variant.isSpecialist && (variant.sizeLabel.contains("7B") || variant.isReasoning)) {
                Surface(color = Color.Red.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("NOT RECOMMENDED", color = Color.Red, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }

            if (variant.isReasoning) {
                Spacer(Modifier.height(4.dp))
                Surface(color = Color(0xFF6699FF).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("EXPERIMENTAL", color = Color(0xFF6699FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(variant.description, color = Color.Gray, fontSize = 10.sp, minLines = 2, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(variant.sizeLabel, color = Color(0xFF6699FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModelCard(variant: AudioConfig.WhisperVariant, isSelected: Boolean, onSelect: () -> Unit) {
    val borderColor = if (isSelected) Color(0xFF6699FF) else Color.White.copy(alpha = 0.1f)
    val bgColor = if (isSelected) Color(0xFF6699FF).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.02f)

    Surface(
        modifier = Modifier
            .width(180.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint = if (isSelected) Color(0xFF6699FF) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(variant.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Text(variant.description, color = Color.Gray, fontSize = 10.sp, minLines = 3, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            Text(variant.sizeLabel, color = Color(0xFF6699FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadProgressContent(state: DownloadState, onStart: () -> Unit) {
    when (state) {
        is DownloadState.Idle -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("READY TO DEPLOY", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6699FF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("START DOWNLOAD", fontWeight = FontWeight.Bold)
                }
            }
        }
        is DownloadState.Downloading -> {
            val pulseAlpha by rememberInfiniteTransition(label = "p").animateFloat(
                1f, 0.4f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "a"
            )
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(state.label.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(pulseAlpha))
                    Text("${state.progressPct}%", color = Color(0xFF6699FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.progressPct / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF6699FF),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${"%.1f".format(state.mbReceived)} MB / ${if (state.mbTotal > 0) "%.1f".format(state.mbTotal) else "?"} MB",
                    color = Color.Gray, fontSize = 10.sp
                )
            }
        }
        is DownloadState.Failed -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text("DOWNLOAD INTERRUPTED", color = Color.White, fontWeight = FontWeight.Bold)
                Text(state.reason, color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f), contentColor = Color.Red)) {
                    Text("RETRY")
                }
            }
        }
        else -> {
            CircularProgressIndicator(color = Color(0xFF6699FF))
        }
    }
}
