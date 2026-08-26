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

@SuppressLint("SetJavaScriptEnabled")
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

    // UI State
    var expanded     by remember { mutableStateOf(false) }
    var inputText    by remember { mutableStateOf("") }
    val listState    = rememberLazyListState()
    val isSpeaking   by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val cameraData   = remember { viewModel.loadCameraPosition() }

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
        viewModel.startVrm()
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
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "MENU",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 28.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )
                Spacer(Modifier.height(16.dp))

                        NavigationDrawerItem(
                            label = { Text("YOU", color = Color.White, fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = { 
                                scope.launch { 
                                    drawerState.close()
                                    // Placeholder for navigation
                                }
                            },
                            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF6699FF)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )

                        NavigationDrawerItem(
                            label = { Text(viewModel.customName.collectAsState().value.uppercase(), color = Color.White, fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = { 
                                scope.launch { 
                                    drawerState.close()
                                    viewModel.setScreen(MainViewModel.Screen.CHARACTER)
                                }
                            },
                            icon = { Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFFFF6666)) },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )

                NavigationDrawerItem(
                    label = { Text("SETTINGS", color = Color.White, fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = { 
                        scope.launch { 
                            drawerState.close()
                            viewModel.setScreen(MainViewModel.Screen.SETTINGS)
                        } 
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.LightGray) },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {

            // ── Native 3D View ─────────────
            if (vrmActive) {
                VrmSceneView(
                    modelPath = vrmUrl,
                    isSpeaking = isSpeaking,
                    isLocked = isCameraLocked,
                    initialCameraData = cameraData,
                    modifier = Modifier.fillMaxSize(),
                    onLoaded = {
                        viewModel.onVrmLoaded()
                    },
                    onError = { msg -> viewModel.onVrmError(msg) },
                    onCameraMoved = { px, py, pz, tx, ty, tz ->
                        viewModel.saveCameraPosition(px, py, pz, tx, ty, tz)
                    }
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
                                text = "${if (isUser) "You" else characterName}: ${msg.content}",
                                color = if (isUser) Color.White else Color(0xFFB0C8FF),
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = if (isUser) TextAlign.End else TextAlign.Start
                            )
                        }
                        if (isLoading) {
                            item {
                                Text(
                                    text = "$characterName is thinking...",
                                    color = Color(0xFF6699FF),
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    modifier = Modifier.alpha(pulseAlpha)
                                )
                            }
                        }
                        if (isSpeaking && !isLoading) {
                                item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6666),
                                        modifier = Modifier.size(14.dp).alpha(pulseAlpha)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "$characterName is speaking...",
                                        color = Color(0xFFFF6666),
                                        fontSize = 12.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        modifier = Modifier.alpha(pulseAlpha)
                                    )
                                }
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

                    // Combined Mic/Send button
                    IconButton(
                        onClick = {
                            if (isSpeaking) {
                                viewModel.toggleMic()
                            } else if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            } else {
                                viewModel.toggleMic()
                            }
                        },
                        enabled = ready
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
                        Icon(
                            imageVector = icon,
                            contentDescription = if (isSpeaking) "Stop" else if (inputText.isNotBlank()) "Send" else "Mic",
                            tint = if (ready) tint else Color(0xFF333333)
                        )
                    }

                    // Camera Lock button
                    IconButton(
                        onClick = { viewModel.toggleCameraLock() },
                        enabled = ready
                    ) {
                        Icon(
                            imageVector = if (isCameraLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Toggle Camera Lock",
                            tint = if (isCameraLocked) Color(0xFF6699FF) else Color(0xFFFF4444)
                        )
                    }
                }

                // Navigation bar spacer
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
