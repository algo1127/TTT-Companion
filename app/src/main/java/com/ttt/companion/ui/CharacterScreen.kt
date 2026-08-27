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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val PANEL_BG = Color(0xCC000000)

@Composable
fun CharacterScreen(viewModel: MainViewModel) {
    val name by viewModel.customName.collectAsStateWithLifecycle()
    val prompt by viewModel.customPrompt.collectAsStateWithLifecycle()
    val voiceId by viewModel.customVoiceId.collectAsStateWithLifecycle()
    val voiceLang by viewModel.customVoiceLang.collectAsStateWithLifecycle()
    val speed by viewModel.ttsSpeed.collectAsStateWithLifecycle()
    val pitch by viewModel.ttsPitch.collectAsStateWithLifecycle()
    val initialUseBlend by viewModel.useBlending.collectAsStateWithLifecycle()
    val initialVoiceA by viewModel.voiceA.collectAsStateWithLifecycle()
    val initialVoiceB by viewModel.voiceB.collectAsStateWithLifecycle()
    val initialBlendRatio by viewModel.blendRatio.collectAsStateWithLifecycle()

    CharacterScreenContent(
        initialName = name,
        initialPrompt = prompt,
        initialVoiceId = voiceId,
        initialVoiceLang = voiceLang,
        initialSpeed = speed,
        initialPitch = pitch,
        initialUseBlend = initialUseBlend,
        initialVoiceA = initialVoiceA,
        initialVoiceB = initialVoiceB,
        initialBlendRatio = initialBlendRatio,
        onBackClick = { viewModel.setScreen(MainViewModel.Screen.VRM) },
        onResetClick = { viewModel.resetCharacterSettings() },
        onSaveClick = { n, p, v, l, s, pi, ub, va, vb, r -> 
            viewModel.saveCharacterSettings(n, p, v, l, s, pi, ub, va, vb, r) 
        },
        onPreviewVoice = { sid, lang -> viewModel.previewVoice(sid, lang) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreenContent(
    initialName: String,
    initialPrompt: String,
    initialVoiceId: Int,
    initialVoiceLang: String,
    initialSpeed: Float,
    initialPitch: Float,
    initialUseBlend: Boolean,
    initialVoiceA: Int,
    initialVoiceB: Int,
    initialBlendRatio: Float,
    onBackClick: () -> Unit,
    onResetClick: () -> Unit,
    onSaveClick: (String, String, Int, String, Float, Float, Boolean, Int, Int, Float) -> Unit,
    onPreviewVoice: (Int, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var promptBody by remember(initialPrompt) { mutableStateOf(initialPrompt) }
    var voiceId by remember(initialVoiceId) { mutableIntStateOf(initialVoiceId) }
    var voiceLang by remember(initialVoiceLang) { mutableStateOf(initialVoiceLang) }
    var speed by remember(initialSpeed) { mutableFloatStateOf(initialSpeed) }
    var pitch by remember(initialPitch) { mutableFloatStateOf(initialPitch) }
    
    var useBlend by remember(initialUseBlend) { mutableStateOf(initialUseBlend) }
    var voiceA by remember(initialVoiceA) { mutableIntStateOf(initialVoiceA) }
    var voiceB by remember(initialVoiceB) { mutableIntStateOf(initialVoiceB) }
    var blendRatio by remember(initialBlendRatio) { mutableFloatStateOf(initialBlendRatio) }

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
                    onExpandedChange = { if (!useBlend) expanded = it }
                ) {
                    OutlinedTextField(
                        value = if (useBlend) "--- Lab Hybrid ---" else currentVoiceName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !useBlend,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        label = { Text("Select Voice", color = if (useBlend) Color.Gray else Color(0xFF6699FF)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = if (useBlend) Color.Gray else Color.White,
                            focusedBorderColor = Color(0xFF6699FF),
                            unfocusedBorderColor = Color(0xFF333344)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF1A1A2E))
                    ) {
                        voices.forEach { (vName, sid) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(vName, color = Color.White)
                                        IconButton(
                                            onClick = {
                                                onPreviewVoice(sid, voiceLang)
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
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
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
                        kokoroLangs.forEach { (lName, code) ->
                            DropdownMenuItem(
                                text = { Text(lName, color = Color.White) },
                                onClick = {
                                    voiceLang = code
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Voice Lab Section
            SettingsSection(title = "Voice Lab (Hybrid Blending)") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Enable Voice Blending", color = Color.White, fontSize = 14.sp)
                            Text("Mixes two voices into one", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = useBlend,
                            onCheckedChange = { useBlend = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6699FF))
                        )
                    }

                    if (useBlend) {
                        // Voice A
                        VoiceSelector("Primary Voice (A)", voices, voiceA) { voiceA = it }
                        
                        // Voice B
                        VoiceSelector("Secondary Voice (B)", voices, voiceB) { voiceB = it }

                        // Ratio
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Blend Ratio (A vs B)", color = Color.White, fontSize = 13.sp)
                                Text("${(blendRatio * 100).toInt()}% / ${(100 - (blendRatio * 100)).toInt()}%", color = Color(0xFFB0C8FF))
                            }
                            Slider(
                                value = blendRatio,
                                onValueChange = { blendRatio = it },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFB0C8FF))
                            )
                        }

                        Button(
                            onClick = { onPreviewVoice(0, voiceLang) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Preview Hybrid result", color = Color.White)
                        }
                    }
                }
            }

            // Speed Section
            SettingsSection(title = "TTS Voice Tuning") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Speed Slider
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
                    }

                    // Pitch Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tone / Pitch", color = Color.White, fontSize = 14.sp)
                            Text("${"%.2f".format(pitch)}x", color = Color(0xFFFF6666), fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = pitch,
                            onValueChange = { pitch = it },
                            valueRange = 0.5f..1.5f,
                            steps = 20,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF6666),
                                activeTrackColor = Color(0xFFFF6666),
                                inactiveTrackColor = Color(0xFF333344)
                            )
                        )
                    }

                    Text(
                        "Tip: Increase pitch (1.2x - 1.3x) for an anime-style cute voice.",
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
                    onClick = onResetClick,
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
                    onClick = { onSaveClick(name, promptBody, voiceId, voiceLang, speed, pitch, useBlend, voiceA, voiceB, blendRatio) },
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

@Preview(showBackground = true)
@Composable
fun PreviewCharacter() {
    MaterialTheme {
        CharacterScreenContent(
            initialName = "Aria",
            initialPrompt = "Be sarcastic.",
            initialVoiceId = 2,
            initialVoiceLang = "en-us",
            initialSpeed = 1.1f,
            initialPitch = 1.0f,
            initialUseBlend = false,
            initialVoiceA = 37,
            initialVoiceB = 2,
            initialBlendRatio = 0.6f,
            onBackClick = {},
            onResetClick = {},
            onSaveClick = { _, _, _, _, _, _, _, _, _, _ -> },
            onPreviewVoice = { _, _ -> }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelector(label: String, voices: List<Pair<String, Int>>, current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val name = voices.find { it.second == current }?.first ?: "ID $current"
    
    Column {
        Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = name,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
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
                voices.forEach { (vName, sid) ->
                    DropdownMenuItem(
                        text = { Text(vName, fontSize = 12.sp, color = Color.White) },
                        onClick = { onSelect(sid); expanded = false }
                    )
                }
            }
        }
    }
}

