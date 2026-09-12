package com.ttt.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PANEL_BG = Color(0xCC000000)

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val contextSize by viewModel.customContextSize.collectAsStateWithLifecycle()
    val useLegacyTest by viewModel.useLegacyTestScreen.collectAsStateWithLifecycle()
    val showStats by viewModel.showPerformanceStats.collectAsStateWithLifecycle()

    SettingsScreenContent(
        contextSize = contextSize,
        useLegacyTest = useLegacyTest,
        showStats = showStats,
        onBackClick = { viewModel.setScreen(MainViewModel.Screen.VRM) },
        onRestartLlmClick = { viewModel.restartLlm() },
        onRestartVrmClick = { viewModel.restartVrmEngine() },
        onSaveLlmSettings = { viewModel.saveLlmSettings(it) },
        onToggleLegacyTest = { viewModel.setLegacyTestScreen(it) },
        onToggleShowStats = { viewModel.setShowPerformanceStats(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    contextSize: Int,
    useLegacyTest: Boolean,
    showStats: Boolean,
    onBackClick: () -> Unit,
    onRestartLlmClick: () -> Unit,
    onRestartVrmClick: () -> Unit,
    onSaveLlmSettings: (Int) -> Unit,
    onToggleLegacyTest: (Boolean) -> Unit,
    onToggleShowStats: (Boolean) -> Unit
) {
    var localContextSize by remember(contextSize) { mutableIntStateOf(contextSize) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SETTINGS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = Color(0xFF1A1A2E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "LLM Configuration") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Context Size", color = Color.White, fontSize = 14.sp)
                        Text("$localContextSize tokens", color = Color(0xFF6699FF), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = localContextSize.toFloat(),
                        onValueChange = { localContextSize = it.toInt() },
                        valueRange = 1024f..8192f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF6699FF))
                    )
                    Text(
                        "Higher context allows longer memory but uses more RAM.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Button(
                        onClick = { onSaveLlmSettings(localContextSize) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = localContextSize != contextSize,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6699FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply & Restart LLM")
                    }
                }
            }

            SettingsSection(title = "Service Management") {
                ServiceButton(
                    label = "Restart LLM Service",
                    icon = Icons.Default.Memory,
                    onClick = onRestartLlmClick,
                    color = Color(0xFF6699FF)
                )
                
                Spacer(Modifier.height(8.dp))
                
                ServiceButton(
                    label = "Restart 3D Engine",
                    icon = Icons.Default.Refresh,
                    onClick = onRestartVrmClick,
                    color = Color(0xFFB0C8FF)
                )
            }

            SettingsSection(title = "Visuals (Coming Soon)") {
                PlaceholderToggle("Dynamic Lighting", true)
                PlaceholderToggle("Shadows", true)
            }

            SettingsSection(title = "Experimental") {
                ToggleOption(
                    title = "Show Performance Stats",
                    subtitle = "Display inference time and speed below messages.",
                    checked = showStats,
                    onCheckedChange = onToggleShowStats
                )
                
                ToggleOption(
                    title = "Legacy 2D TestScreen",
                    subtitle = "Use the debug interface for 2D mode. Requires reboot.",
                    checked = useLegacyTest,
                    onCheckedChange = onToggleLegacyTest
                )
            }
        }
    }
}

@Composable
fun ToggleOption(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6699FF))
        )
    }
}

@Composable
fun ServiceButton(label: String, icon: ImageVector, onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PlaceholderToggle(label: String, initial: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(
            checked = initial,
            onCheckedChange = {},
            enabled = false
        )
    }
}
