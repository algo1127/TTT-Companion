package com.ttt.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(viewModel: MainViewModel, onBackClick: () -> Unit) {
    val currentName by viewModel.userName.collectAsStateWithLifecycle()
    var nameInput by remember(currentName) { mutableStateOf(currentName) }

    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var showDatabase by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadMemories()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "USER PROFILE",
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF6699FF).copy(alpha = 0.1f), RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color(0xFF6699FF), modifier = Modifier.size(50.dp))
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "How should Aria address you?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your name...", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6699FF),
                    unfocusedBorderColor = Color(0xFF333344),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF6699FF)
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { 
                    viewModel.saveUserName(nameInput)
                    onBackClick()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = nameInput.isNotBlank() && nameInput != currentName,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6699FF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("SAVE PROFILE", fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(48.dp))

            // Debug / Memory Section
            Text(
                "MEMORY ENGINE (DEBUG)",
                color = Color(0xFF6699FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showDatabase = !showDatabase },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(if (showDatabase) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                Spacer(Modifier.width(8.dp))
                Text(if (showDatabase) "HIDE VECTOR DATABASE" else "VIEW VECTOR DATABASE")
            }

            if (showDatabase) {
                Spacer(Modifier.height(16.dp))
                if (memories.isEmpty()) {
                    Text("Database is empty. Aria has no long-term memories yet.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    memories.forEach { entry ->
                        MemoryItem(entry, onDelete = { viewModel.deleteMemory(it) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6699FF))
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("ADD TO DATABASE")
            }

            Spacer(Modifier.height(32.dp))
            
            Text(
                "Your name is used for personalization and is cached in the LLM system prompt.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { 
                viewModel.addManualMemory(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MemoryItem(entry: com.ttt.companion.memory.MemoryEntry, onDelete: (com.ttt.companion.memory.MemoryEntry) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.timestamp)),
                    color = Color(0xFF6699FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.summary,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
            IconButton(onClick = { onDelete(entry) }) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun AddMemoryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = { Text("Add Manual Memory", color = Color.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. User likes dark chocolate", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text("ADD", color = Color(0xFF6699FF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        }
    )
}
