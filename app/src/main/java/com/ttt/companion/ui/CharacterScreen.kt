package com.ttt.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
    val initialVoiceId by viewModel.customVoiceId.collectAsStateWithLifecycle()
    val initialVoiceLang by viewModel.customVoiceLang.collectAsStateWithLifecycle()
    val initialSpeed by viewModel.ttsSpeed.collectAsStateWithLifecycle()

    var name by remember(initialName) { mutableStateOf(initialName) }
    var promptBody by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var voiceId by remember(initialVoiceId) { mutableIntStateOf(initialVoiceId) }
    var voiceLang by remember(initialVoiceLang) { mutableStateOf(initialVoiceLang) }
    var speed by remember(initialSpeed) { mutableFloatStateOf(initialSpeed) }

    val kokoroLangs = listOf(
        "English (US)" to "en-us",
        "English (UK)" to "en-gb",
        "Japanese" to "ja",
        "Chinese" to "zh",
        "Spanish" to "es",
        "French" to "fr",
        "Hindi" to "hi",
        "Italian" to "it",
        "Portuguese" to "pt"
    )

    val voices = listOf(
        // American Female
        "Alloy (af_alloy)" to 0,
        "Aoede (af_aoede)" to 1,
        "Bella (af_bella)" to 2,
        "Heart (af_heart)" to 3,
        "Jessica (af_jessica)" to 4,
        "Kore (af_kore)" to 5,
        "Nicole (af_nicole)" to 6,
        "Nova (af_nova)" to 7,
        "River (af_river)" to 8,
        "Sarah (af_sarah)" to 9,
        "Sky (af_sky)" to 10,
        
        // American Male
        "Adam (am_adam)" to 11,
        "Echo (am_echo)" to 12,
        "Eric (am_eric)" to 13,
        "Fenrir (am_fenrir)" to 14,
        "Liam (am_liam)" to 15,
        "Michael (am_michael)" to 16,
        "Onyx (am_onyx)" to 17,
        "Puck (am_puck)" to 18,
        "Santa (am_santa)" to 19,

        // British Female
        "Alice (bf_alice)" to 20,
        "Emma (bf_emma)" to 21,
        "Isabella (bf_isabella)" to 22,
        "Lily (bf_lily)" to 23,

        // British Male
        "Daniel (bm_daniel)" to 24,
        "Fable (bm_fable)" to 25,
        "George (bm_george)" to 26,
        "Lewis (bm_lewis)" to 27,

        // Japanese
        "Alpha (jf_alpha) [JP]" to 37,
        "Gongitsune (jf_gongitsune) [JP]" to 38,
        "Nezumi (jf_nezumi) [JP]" to 39,
        "Tebukuro (jf_tebukuro) [JP]" to 40,
        "Kumo (jm_kumo) [JP]" to 41
    )

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

            // Voice Section
            SettingsSection(title = "TTS Voice") {
                var expanded by remember { mutableStateOf(false) }
                val currentVoiceName = voices.find { it.second == voiceId }?.first ?: "Custom ($voiceId)"
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = currentVoiceName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Voice", color = Color(0xFF6699FF)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6699FF),
                            unfocusedBorderColor = Color(0xFF333344)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A2E))
                    ) {
                        voices.forEach { (name, sid) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(name, color = Color.White)
                                        IconButton(
                                            onClick = {
                                                viewModel.previewVoice(sid, voiceLang)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Preview",
                                                tint = Color(0xFF6699FF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    voiceId = sid
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Language Section
            SettingsSection(title = "TTS Language") {
                var expanded by remember { mutableStateOf(false) }
                val currentLangName = kokoroLangs.find { it.second == voiceLang }?.first ?: voiceLang

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = currentLangName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Language", color = Color(0xFF6699FF)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6699FF),
                            unfocusedBorderColor = Color(0xFF333344)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A2E))
                    ) {
                        kokoroLangs.forEach { (name, code) ->
                            DropdownMenuItem(
                                text = { Text(name, color = Color.White) },
                                onClick = {
                                    voiceLang = code
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Speed Section
            SettingsSection(title = "TTS Speed") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Flow / Speed", color = Color.White, fontSize = 14.sp)
                        Text("${"%.1f".format(speed)}x", color = Color(0xFF6699FF), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.8f..1.5f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6699FF),
                            activeTrackColor = Color(0xFF6699FF),
                            inactiveTrackColor = Color(0xFF333344)
                        )
                    )
                    Text(
                        "Tip: Higher speed (1.1x - 1.2x) sounds more natural for this model.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
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
                    onClick = { viewModel.saveCharacterSettings(name, promptBody, voiceId, voiceLang, speed) },
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

