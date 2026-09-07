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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.LlmService
import kotlinx.coroutines.launch

// Translucent black for the overlay panels
private val PANEL_BG = Color(0xCC000000)

@Composable
fun VrmScreen(viewModel: MainViewModel) {
    val messages     by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading    by viewModel.isLoading.collectAsStateWithLifecycle()
    val modelState   by viewModel.modelState.collectAsStateWithLifecycle()
    val vrmUrl       by viewModel.vrmUrl.collectAsStateWithLifecycle()
    val vrmActive    by viewModel.vrmActive.collectAsStateWithLifecycle()
    val vrmLoading   by viewModel.vrmLoading.collectAsStateWithLifecycle()
    val isCameraLocked by viewModel.isCameraLocked.collectAsStateWithLifecycle()
    val characterName by viewModel.customName.collectAsStateWithLifecycle()
    val isSpeaking   by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val audioState   by viewModel.audioState.collectAsStateWithLifecycle()
    val llmStatus    by viewModel.llmLoadingStatus.collectAsStateWithLifecycle()
    val vrmStatus    by viewModel.vrmLoadingStatus.collectAsStateWithLifecycle()

    val cameraData   = remember { viewModel.loadCameraPosition() }

    VrmScreenContent(
        messages = messages,
        isLoading = isLoading,
        modelState = modelState,
        vrmUrl = vrmUrl,
        vrmActive = vrmActive,
        vrmLoading = vrmLoading,
        isCameraLocked = isCameraLocked,
        characterName = characterName,
        isSpeaking = isSpeaking,
        audioState = audioState,
        llmStatus = llmStatus,
        vrmStatus = vrmStatus,
        cameraData = cameraData,
        onSendMessage = { viewModel.sendMessage(it) },
        onToggleMic = { viewModel.toggleMic() },
        onToggleCameraLock = { viewModel.toggleCameraLock() },
        onVrmLoaded = { viewModel.onVrmLoaded() },
        onVrmError = { viewModel.onVrmError(it) },
        onCameraMoved = { rx, ry, zoom -> viewModel.saveCameraPosition(rx, ry, zoom) },
        onSetScreen = { viewModel.setScreen(it) },
        onStartVrm = { viewModel.startVrm() },
        onSkipVrm = { viewModel.skipVrm() }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VrmScreenContent(
    messages: List<com.ttt.companion.model.ChatMessage>,
    isLoading: Boolean,
    modelState: LlmService.LoadState,
    vrmUrl: String?,
    vrmActive: Boolean,
    vrmLoading: Boolean,
    isCameraLocked: Boolean,
    characterName: String,
    isSpeaking: Boolean,
    audioState: MainViewModel.AudioState,
    llmStatus: String,
    vrmStatus: String,
    cameraData: FloatArray?,
    onSendMessage: (String) -> Unit,
    onToggleMic: () -> Unit,
    onToggleCameraLock: () -> Unit,
    onVrmLoaded: () -> Unit,
    onVrmError: (String) -> Unit,
    onCameraMoved: (Float, Float, Float) -> Unit,
    onSetScreen: (MainViewModel.Screen) -> Unit,
    onStartVrm: () -> Unit,
    onSkipVrm: () -> Unit
) {
    // UI State
    var expanded     by remember { mutableStateOf(false) }
    var inputText    by remember { mutableStateOf("") }
    val listState    = rememberLazyListState()

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

    // Load VRM on entry
    LaunchedEffect(Unit) {
        onStartVrm()
    }

    // Auto-scroll messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isCameraLocked,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PANEL_BG,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .statusBarsPadding()
            ) {
                Spacer(Modifier.height(48.dp))
                Text("MENU", modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp), color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(16.dp))

                NavigationDrawerItem(
                    label = { Text("YOU", color = Color.White) },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
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
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {

            // Godot 3D View
            if (vrmActive) {
                GodotVrmView(
                    modelPath = vrmUrl,
                    isSpeaking = isSpeaking,
                    isCameraLocked = isCameraLocked,
                    initialCameraData = cameraData,
                    modifier = Modifier.fillMaxSize(),
                    onLoaded = onVrmLoaded,
                    onError = onVrmError,
                    onCameraMoved = onCameraMoved
                )
            }

            // Side Menu Toggle (Top Start)
            IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.Menu, "Menu", tint = Color.White)
            }

            // Loading Overlays
            if (vrmLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color(0xFFFF6666).copy(alpha = 0.1f), RoundedCornerShape(40.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ViewInAr, null, tint = Color(0xFFFF6666), modifier = Modifier.size(40.dp).alpha(pulseAlpha))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("MANIFESTING AVATAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(vrmStatus, color = Color.Gray, fontSize = 10.sp)
                    }
                }
            } else if (modelState is LlmService.LoadState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
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
                            Icon(Icons.Default.Memory, null, tint = Color(0xFF6699FF), modifier = Modifier.size(40.dp).alpha(pulseAlpha))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("IGNITING LOCAL AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(llmStatus, color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }

            // Bottom overlay bar
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(PANEL_BG)
            ) {
                Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -15) expanded = true
                        if (dragAmount > 15) expanded = false
                    }
                }.clickable { expanded = !expanded }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = if (expanded) 0.5f else 0.3f)))
                }

                AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages) { msg ->
                            val isUser = msg.role == "user"
                            Text(text = "${if (isUser) "You" else characterName}: ${msg.content}", color = if (isUser) Color.White else Color(0xFFB0C8FF), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = if (isUser) TextAlign.End else TextAlign.Start)
                        }
                        if (isLoading) {
                            item {
                                Text(text = "$characterName is thinking...", color = Color(0xFF6699FF), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.alpha(pulseAlpha))
                            }
                        }
                        if (audioState is MainViewModel.AudioState.Speaking) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color(0xFFFF6666), modifier = Modifier.size(14.dp).alpha(pulseAlpha))
                                    Spacer(Modifier.width(4.dp))
                                    Text(text = "$characterName is speaking...", color = Color(0xFFFF6666), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.alpha(pulseAlpha))
                                }
                            }
                        }
                        if (audioState is MainViewModel.AudioState.Recording) {
                            item {
                                Text(text = "Listening...", color = Color(0xFFFF4444), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.alpha(pulseAlpha))
                            }
                        }
                        if (audioState is MainViewModel.AudioState.Transcribing) {
                            item {
                                Text(text = "Transcribing...", color = Color(0xFFB0C8FF), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.alpha(pulseAlpha))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ready = modelState == LlmService.LoadState.Ready && !isLoading
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it; if (!expanded) expanded = true },
                        modifier = Modifier.weight(1f), placeholder = { Text("Say something...", color = Color(0xFF666666)) },
                        enabled = ready, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6699FF), unfocusedBorderColor = Color(0xFF333344), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF6699FF))
                    )
                    IconButton(onClick = {
                        if (isSpeaking) onToggleMic()
                        else if (inputText.isNotBlank()) { onSendMessage(inputText); inputText = "" }
                        else onToggleMic()
                    }, enabled = ready) {
                        val icon = when {
                            isSpeaking -> Icons.Default.Stop
                            inputText.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Default.Mic
                        }
                        Icon(imageVector = icon, contentDescription = null, tint = if (ready) (if (isSpeaking) Color(0xFFFF6666) else if (inputText.isNotBlank()) Color(0xFF6699FF) else Color.White) else Color(0xFF333333))
                    }
                    IconButton(onClick = onToggleCameraLock, enabled = ready) {
                        Icon(imageVector = if (isCameraLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = if (isCameraLocked) Color(0xFF6699FF) else Color(0xFFFF4444))
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
