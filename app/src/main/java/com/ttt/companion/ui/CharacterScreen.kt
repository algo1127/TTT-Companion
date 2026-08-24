package com.ttt.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val PANEL_BG = Color(0xCC000000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(viewModel: MainViewModel) {
    val initialName by viewModel.customName.collectAsStateWithLifecycle()
    val initialPrompt by viewModel.customPrompt.collectAsStateWithLifecycle()

    var name by remember(initialName) { mutableStateOf(initialName) }
    var promptBody by remember(initialPrompt) { mutableStateOf(initialPrompt) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "CHARACTER",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setScreen(MainViewModel.Screen.VRM) }) {
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Identity Section
            SettingsSection(title = "Identity") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color(0xFF6699FF)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF6699FF),
                        unfocusedBorderColor = Color(0xFF333344)
                    ),
                    singleLine = true
                )
            }

            // AI System Prompt Section
            SettingsSection(title = "System Prompt") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // The Mandatory Line (Read Only)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "You are $name, a local AI running on the user's mobile phone.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))

                    // Editable Body
                    OutlinedTextField(
                        value = promptBody,
                        onValueChange = { promptBody = it },
                        label = { Text("Personality & Rules", color = Color(0xFF6699FF)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6699FF),
                            unfocusedBorderColor = Color(0xFF333344)
                        )
                    )
                }
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reset Button
                Button(
                    onClick = { viewModel.resetCharacterSettings() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }

                // Save Button
                Button(
                    onClick = { viewModel.saveCharacterSettings(name, promptBody) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6699FF),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save & Restart")
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}
