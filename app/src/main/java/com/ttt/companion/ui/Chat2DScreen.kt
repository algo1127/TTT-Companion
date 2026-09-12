package com.ttt.companion.ui

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.LlmService
import kotlinx.coroutines.launch

private val PANEL_BG = Color(0xCC000000)

@Composable
fun Chat2DScreen(viewModel: MainViewModel) {
    val messages     by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val modelState   by viewModel.modelState.collectAsStateWithLifecycle()
    val characterName by viewModel.customName.collectAsStateWithLifecycle()
    val userName     by viewModel.userName.collectAsStateWithLifecycle()
    val isSpeaking   by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val audioState   by viewModel.audioState.collectAsStateWithLifecycle()
    val llmStatus    by viewModel.llmLoadingStatus.collectAsStateWithLifecycle()
    val showStats    by viewModel.showPerformanceStats.collectAsStateWithLifecycle()

    Chat2DScreenContent(
        messages = messages,
        isLoading = isLoading,
        modelState = modelState,
        characterName = characterName,
        userName = userName,
        isSpeaking = isSpeaking,
        audioState = audioState,
        llmStatus = llmStatus,
        engineName = viewModel.engineName,
        computeUnit = viewModel.computeUnit,
        showStats = showStats,
        onSendMessage = { viewModel.sendMessage(it) },
        onToggleMic = { viewModel.toggleMic() },
        onSetScreen = { viewModel.setScreen(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chat2DScreenContent(
    messages: List<com.ttt.companion.model.ChatMessage>,
    isLoading: Boolean,
    modelState: LlmService.LoadState,
    characterName: String,
    userName: String,
    isSpeaking: Boolean,
    audioState: MainViewModel.AudioState,
    llmStatus: String,
    engineName: String,
    computeUnit: String,
    showStats: Boolean,
    onSendMessage: (String) -> Unit,
    onToggleMic: () -> Unit,
    onSetScreen: (MainViewModel.Screen) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    // Static alpha for when the LLM is busy (to save GPU cycles)
    val displayAlpha = if (isLoading) 1f else pulseAlpha

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PANEL_BG,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.fillMaxHeight().width(280.dp)
            ) {
                Spacer(Modifier.height(48.dp))
                Text("MENU", modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp), color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))

                NavigationDrawerItem(
                    label = { Text(userName.uppercase(), color = Color.White) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onSetScreen(MainViewModel.Screen.USER) } },
                    icon = { Icon(Icons.Default.Person, null, tint = Color(0xFF6699FF)) },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(characterName.uppercase(), color = Color.White) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onSetScreen(MainViewModel.Screen.CHARACTER) } },
                    icon = { Icon(Icons.Default.Face, null, tint = Color(0xFFFF6666)) },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("SETTINGS", color = Color.White) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close(); onSetScreen(MainViewModel.Screen.SETTINGS) } },
                    icon = { Icon(Icons.Default.Settings, null, tint = Color.LightGray) },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(Modifier.weight(1f))

                // Debug / System Info Section
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .alpha(0.6f)
                ) {
                    Text("SYSTEM INFO", color = Color(0xFF6699FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Engine: $engineName", color = Color.White, fontSize = 11.sp)
                    Text("Backend: $computeUnit", color = Color.White, fontSize = 11.sp)
                    Text("MinSDK: 27 | Target: 36", color = Color.Gray, fontSize = 9.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(characterName.uppercase(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("LOCAL AI COMPANION", color = Color(0xFF6699FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color(0xFF1A1A2E)
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1B))
                        )
                    )
            ) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF6699FF).copy(alpha = 0.05f), Color.Transparent),
                                radius = 500f
                            )
                        )
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(messages) { msg ->
                                val isUser = msg.role == "user"
                                ChatBubble(msg, isUser, characterName, userName, showStats)
                            }
                            if (isLoading) {
                                item {
                                    StatusIndicator("$characterName is thinking...", Color(0xFF6699FF), displayAlpha)
                                }
                            }
                            if (audioState is MainViewModel.AudioState.Speaking) {
                                item {
                                    StatusIndicator("$characterName is speaking...", Color(0xFFFF6666), displayAlpha, Icons.AutoMirrored.Filled.VolumeUp)
                                }
                            }
                            if (audioState is MainViewModel.AudioState.Recording) {
                                item {
                                    StatusIndicator("Listening...", Color(0xFFFF4444), displayAlpha)
                                }
                            }
                            if (audioState is MainViewModel.AudioState.Transcribing) {
                                item {
                                    StatusIndicator("Transcribing...", Color(0xFFB0C8FF), displayAlpha)
                                }
                            }
                        }

                        // LLM Loading Overlay for 2D Mode
                        if (modelState is LlmService.LoadState.Loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(Color(0xFF6699FF).copy(alpha = 0.1f), RoundedCornerShape(40.dp))
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Memory, null, tint = Color(0xFF6699FF), modifier = Modifier.size(40.dp).alpha(displayAlpha))
                                    }
                                    Spacer(Modifier.height(24.dp))
                                    Text("IGNITING LOCAL AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    Text(llmStatus, color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Input Area
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PANEL_BG,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .navigationBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val ready = modelState == LlmService.LoadState.Ready && !isLoading
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...", color = Color.Gray) },
                                enabled = ready,
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6699FF),
                                    unfocusedBorderColor = Color(0xFF333344),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF6699FF)
                                )
                            )
                            
                            IconButton(
                                onClick = {
                                    if (isSpeaking) onToggleMic()
                                    else if (inputText.isNotBlank()) { onSendMessage(inputText); inputText = "" }
                                    else onToggleMic()
                                },
                                enabled = ready,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSpeaking) Color(0xFFFF6666).copy(alpha = 0.2f)
                                        else Color(0xFF6699FF).copy(alpha = 0.1f),
                                        RoundedCornerShape(24.dp)
                                    )
                            ) {
                                val icon = when {
                                    isSpeaking -> Icons.Default.Stop
                                    inputText.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                                    else -> Icons.Default.Mic
                                }
                                val tint = when {
                                    isSpeaking -> Color(0xFFFF6666)
                                    inputText.isNotBlank() -> Color(0xFF6699FF)
                                    else -> Color.White
                                }
                                Icon(imageVector = icon, contentDescription = null, tint = tint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    msg: com.ttt.companion.model.ChatMessage, 
    isUser: Boolean, 
    charName: String,
    userName: String,
    showStats: Boolean
) {
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail && msg.performanceStats != null) {
        PerformanceStatsDialog(msg.performanceStats) { showDetail = false }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = if (isUser) userName.uppercase() else charName.uppercase(),
            color = if (isUser) Color(0xFF6699FF) else Color(0xFFFF6666),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Surface(
            color = if (isUser) Color(0xFF252545) else Color(0xFF333344).copy(alpha = 0.5f),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            border = if (isUser) BorderStroke(1.dp, Color(0xFF6699FF).copy(alpha = 0.3f)) else null
        ) {
            Text(
                text = msg.content,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (!isUser && showStats && msg.performanceStats != null) {
            Text(
                text = "${msg.performanceStats.totalTime}ms | ${"%.1f".format(msg.performanceStats.tokensPerSec)} t/s",
                color = Color.Gray,
                fontSize = 9.sp,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clickable { showDetail = true }
            )
        }
    }
}

@Composable
fun PerformanceStatsDialog(stats: com.ttt.companion.model.PerformanceStats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = { Text("Inference Details", color = Color.White, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("Engine", stats.engine)
                StatRow("Backend", stats.backend)
                StatRow("Time to First Token", "${stats.ttft}ms")
                StatRow("Total Generation Time", "${stats.totalTime}ms")
                StatRow("Token Count", "${stats.tokenCount}")
                StatRow("Average Speed", "${"%.2f".format(stats.tokensPerSec)} tok/s")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = Color(0xFF6699FF))
            }
        }
    )
}

@Composable
fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusIndicator(text: String, color: Color, alpha: Float, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.alpha(alpha).padding(vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        } else {
            Box(modifier = Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, color = color, fontSize = 12.sp, fontStyle = FontStyle.Italic)
    }
}
