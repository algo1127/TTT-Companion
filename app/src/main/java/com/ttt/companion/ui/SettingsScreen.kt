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
import androidx.compose.ui.tooling.preview.Preview
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
    SettingsScreenContent(
        contextSize = contextSize,
        onBackClick = { viewModel.setScreen(MainViewModel.Screen.VRM) },
        onRestartLlmClick = { viewModel.restartLlm() },
        onRestartVrmClick = { viewModel.restartVrmEngine() },
        onSaveLlmSettings = { viewModel.saveLlmSettings(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    contextSize: Int,
    onBackClick: () -> Unit,
    onRestartLlmClick: () -> Unit,
    onRestartVrmClick: () -> Unit,
    onSaveLlmSettings: (Int) -> Unit
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
                        steps = 6, // 1024, 2048, 3072, 4096, 5120, 6144, 7168, 8192
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6699FF),
                            activeTrackColor = Color(0xFF6699FF),
                            inactiveTrackColor = Color(0xFF333344)
                        )
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6699FF),
                            contentColor = Color.White
                        ),
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
                PlaceholderToggle("Auto-Focus Camera", false)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            color = Color(0xFF6699FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PANEL_BG)
                .padding(16.dp)
        ) {
            content()
        }
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
            enabled = false,
            colors = SwitchDefaults.colors(
                disabledUncheckedThumbColor = Color.Gray,
                disabledUncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSettings() {
    MaterialTheme {
        SettingsScreenContent(
            contextSize = 4096,
            onBackClick = {},
            onRestartLlmClick = {},
            onRestartVrmClick = {},
            onSaveLlmSettings = {}
        )
    }
}
