package com.ttt.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.ttt.companion.llm.LlmService
import com.ttt.companion.llm.ModelConfig

private val PANEL_BG = Color(0xCC000000)

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val contextSize by viewModel.customContextSize.collectAsStateWithLifecycle()
    val useLegacyTest by viewModel.useLegacyTestScreen.collectAsStateWithLifecycle()
    val showStats by viewModel.showPerformanceStats.collectAsStateWithLifecycle()
    val presencePenalty by viewModel.presencePenalty.collectAsStateWithLifecycle()
    val keepOldModels by viewModel.keepOldModels.collectAsStateWithLifecycle()
    val reasoningThreshold by viewModel.reasoningThreshold.collectAsStateWithLifecycle()
    val reasoningEnabled by viewModel.reasoningEnabled.collectAsStateWithLifecycle()
    val nudgeType by viewModel.nudgeType.collectAsStateWithLifecycle()
    val useOfficialNudge by viewModel.useOfficialNudge.collectAsStateWithLifecycle()
    val selectedLlmId by viewModel.selectedLlmId.collectAsStateWithLifecycle()
    val cognitiveMemoryEnabled by viewModel.cognitiveMemoryEnabled.collectAsStateWithLifecycle()
    val longTermMemoryEnabled by viewModel.longTermMemoryEnabled.collectAsStateWithLifecycle()
    val parallelTtsEnabled by viewModel.parallelTtsEnabled.collectAsStateWithLifecycle()
    val useSystemPromptCache by viewModel.useSystemPromptCache.collectAsStateWithLifecycle()
    val visionResolution by viewModel.visionResolution.collectAsStateWithLifecycle()

    SettingsScreenContent(
        contextSize = contextSize,
        useLegacyTest = useLegacyTest,
        showStats = showStats,
        presencePenalty = presencePenalty,
        keepOldModels = keepOldModels,
        reasoningThreshold = reasoningThreshold,
        reasoningEnabled = reasoningEnabled,
        nudgeType = nudgeType,
        useOfficialNudge = useOfficialNudge,
        selectedLlmId = selectedLlmId,
        cognitiveMemoryEnabled = cognitiveMemoryEnabled,
        longTermMemoryEnabled = longTermMemoryEnabled,
        parallelTtsEnabled = parallelTtsEnabled,
        useSystemPromptCache = useSystemPromptCache,
        visionResolution = visionResolution,
        onBackClick = { viewModel.setScreen(MainViewModel.Screen.VRM) },
        onRestartLlmClick = { viewModel.restartLlm() },
        onRestartVrmClick = { viewModel.restartVrmEngine() },
        onSaveLlmSettings = { viewModel.saveLlmSettings(it) },
        onToggleLegacyTest = { viewModel.setLegacyTestScreen(it) },
        onToggleShowStats = { viewModel.setShowPerformanceStats(it) },
        onPresencePenaltyChange = { viewModel.setPresencePenalty(it) },
        onToggleKeepModels = { viewModel.setKeepOldModels(it) },
        onSwitchModelsClick = { viewModel.enterDownloadMode() },
        onThresholdChange = { viewModel.setReasoningThreshold(it.toInt()) },
        onToggleReasoning = { viewModel.setReasoningEnabled(it) },
        onNudgeTypeChange = { viewModel.setNudgeType(it) },
        onToggleOfficialNudge = { viewModel.setUseOfficialNudge(it) },
        onLlmModelChange = { viewModel.selectLlmModel(it) },
        onToggleCognitiveMemory = { viewModel.setCognitiveMemoryEnabled(it) },
        onToggleLongTermMemory = { viewModel.setLongTermMemoryEnabled(it) },
        onToggleParallelTts = { viewModel.setParallelTtsEnabled(it) },
        onToggleSystemPromptCache = { viewModel.setUseSystemPromptCache(it) },
        onVisionResolutionChange = { viewModel.setVisionResolution(it.toInt()) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    contextSize: Int,
    useLegacyTest: Boolean,
    showStats: Boolean,
    presencePenalty: Float,
    keepOldModels: Boolean,
    reasoningThreshold: Int,
    reasoningEnabled: Boolean,
    nudgeType: LlmService.NudgeType,
    useOfficialNudge: Boolean,
    selectedLlmId: String,
    cognitiveMemoryEnabled: Boolean,
    longTermMemoryEnabled: Boolean,
    parallelTtsEnabled: Boolean,
    useSystemPromptCache: Boolean,
    visionResolution: Int,
    onBackClick: () -> Unit,
    onRestartLlmClick: () -> Unit,
    onRestartVrmClick: () -> Unit,
    onSaveLlmSettings: (Int) -> Unit,
    onToggleLegacyTest: (Boolean) -> Unit,
    onToggleShowStats: (Boolean) -> Unit,
    onPresencePenaltyChange: (Float) -> Unit,
    onToggleKeepModels: (Boolean) -> Unit,
    onSwitchModelsClick: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onToggleReasoning: (Boolean) -> Unit,
    onNudgeTypeChange: (LlmService.NudgeType) -> Unit,
    onToggleOfficialNudge: (Boolean) -> Unit,
    onLlmModelChange: (String) -> Unit,
    onToggleCognitiveMemory: (Boolean) -> Unit,
    onToggleLongTermMemory: (Boolean) -> Unit,
    onToggleParallelTts: (Boolean) -> Unit,
    onToggleSystemPromptCache: (Boolean) -> Unit,
    onVisionResolutionChange: (Float) -> Unit
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
            SettingsSection(title = "Core Brain (LLM)") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModelConfig.LLM_VARIANTS.filter { !it.isSpecialist }.forEach { variant ->
                        LlmModelCard(
                            variant = variant,
                            isSelected = variant.id == selectedLlmId,
                            onClick = { onLlmModelChange(variant.id) }
                        )
                    }
                }
            }

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
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Apply & Restart LLM")
                    }

                    val variant = ModelConfig.getLlmVariant(selectedLlmId)
                    if (variant.isReasoning) {
                        Spacer(Modifier.height(16.dp))

                        // Reasoning / Presence Penalty
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reasoning Budget", color = Color.White, fontSize = 14.sp)
                            Text("%.2f".format(presencePenalty), color = Color(0xFFFF6666), fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = presencePenalty,
                            onValueChange = { onPresencePenaltyChange(it) },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF6666))
                        )
                        Text(
                            "Higher values (0.6+) nudge Aria to finish internal reasoning faster.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Reasoning Threshold Watchdog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cool Down Threshold", color = Color.White, fontSize = 14.sp)
                        Text("$reasoningThreshold tokens", color = Color(0xFF6699FF), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = reasoningThreshold.toFloat(),
                        onValueChange = { onThresholdChange(it) },
                        valueRange = 50f..500f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF6699FF))
                    )
                    Text(
                        "Force Aria to wrap up if reasoning exceeds this limit.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            SettingsSection(title = "Service Management") {
                ServiceButton(
                    label = "Switch STT Models",
                    icon = Icons.Default.Refresh,
                    onClick = onSwitchModelsClick,
                    color = Color(0xFF6699FF)
                )

                Spacer(Modifier.height(8.dp))

                ToggleOption(
                    title = "Keep Old Models",
                    subtitle = "Don't delete previous Whisper variants when switching.",
                    checked = keepOldModels,
                    onCheckedChange = onToggleKeepModels
                )

                Spacer(Modifier.height(16.dp))

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
                    title = "Long-Term Memory",
                    subtitle = "Enable the vector database system to save facts across sessions.",
                    checked = longTermMemoryEnabled,
                    onCheckedChange = onToggleLongTermMemory
                )

                ToggleOption(
                    title = "Cognitive Architect",
                    subtitle = "Use a secondary 0.5B model to summarize and organize facts. (Requires extra RAM)",
                    checked = cognitiveMemoryEnabled,
                    onCheckedChange = onToggleCognitiveMemory
                )

                if (com.ttt.companion.llm.DeviceUtils.isSnapdragonDevice()) {
                    ToggleOption(
                        title = "Parallel Speech (Snapdragon)",
                        subtitle = "Bake audio in the background while the LLM is thinking. Much faster starts.",
                        checked = parallelTtsEnabled,
                        onCheckedChange = onToggleParallelTts
                    )
                }

                ToggleOption(
                    title = "System Prompt Cache",
                    subtitle = "Reuse the KV-cache for faster response times. Disable to investigate repetition issues.",
                    checked = useSystemPromptCache,
                    onCheckedChange = onToggleSystemPromptCache
                )

                Spacer(Modifier.height(16.dp))

                // Vision Resolution Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vision Resolution", color = Color.White, fontSize = 14.sp)
                    Text("${visionResolution}p", color = Color(0xFF6699FF), fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = visionResolution.toFloat(),
                    onValueChange = onVisionResolutionChange,
                    valueRange = 144f..512f,
                    steps = 3, // 144, 256, 384, 512 roughly
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF6699FF))
                )
                Text(
                    "Lower resolution is MUCH faster and uses less memory. Higher is better for small text.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )

                val variant = ModelConfig.getLlmVariant(selectedLlmId)
                
                if (variant.isReasoning) {
                    ToggleOption(
                        title = "Deep Reasoning",
                        subtitle = "Allow the model to use internal monologue (increases response time).",
                        checked = reasoningEnabled,
                        onCheckedChange = onToggleReasoning
                    )

                    ToggleOption(
                        title = "Official Qwen Nudge",
                        subtitle = "Use '/think' and '/no_think' keywords inside the user prompt.",
                        checked = useOfficialNudge,
                        onCheckedChange = onToggleOfficialNudge
                    )

                    if (!reasoningEnabled && !useOfficialNudge) {
                        Spacer(Modifier.height(16.dp))
                        Text("Nudge Style (No-Think)", color = Color.White, fontSize = 14.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NudgeButton(
                                label = "/no_think",
                                isSelected = nudgeType == LlmService.NudgeType.NO_THINK,
                                onClick = { onNudgeTypeChange(LlmService.NudgeType.NO_THINK) },
                                modifier = Modifier.weight(1f)
                            )
                            NudgeButton(
                                label = "Done.",
                                isSelected = nudgeType == LlmService.NudgeType.DONE,
                                onClick = { onNudgeTypeChange(LlmService.NudgeType.DONE) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Reasoning Threshold Watchdog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cool Down Threshold", color = Color.White, fontSize = 14.sp)
                        Text("$reasoningThreshold tokens", color = Color(0xFF6699FF), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = reasoningThreshold.toFloat(),
                        onValueChange = { onThresholdChange(it) },
                        valueRange = 50f..500f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF6699FF))
                    )
                    Text(
                        "Force Aria to wrap up if reasoning exceeds this limit.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    
                    Spacer(Modifier.height(16.dp))
                }

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
fun LlmModelCard(variant: ModelConfig.ModelVariant, isSelected: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) Color(0xFF6699FF) else Color.White.copy(alpha = 0.1f)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF6699FF).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(variant.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                
                if (variant.isRecommended) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color.Green.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("RECOMMENDED", color = Color.Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                } else if (!variant.isSpecialist && (variant.sizeLabel.contains("7B") || variant.isReasoning)) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color.Red.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("NOT RECOMMENDED", color = Color.Red, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }

                if (variant.isReasoning) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color(0xFF6699FF).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("EXPERIMENTAL", color = Color(0xFF6699FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(variant.description, color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(variant.sizeLabel, color = Color(0xFF6699FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NudgeButton(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = if (isSelected) Color(0xFF6699FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color(0xFF6699FF) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (isSelected) Color(0xFF6699FF) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(16.dp),
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
