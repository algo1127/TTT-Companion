package com.ttt.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(viewModel: MainViewModel, onBackClick: () -> Unit) {
    val currentName by viewModel.userName.collectAsStateWithLifecycle()
    var nameInput by remember(currentName) { mutableStateOf(currentName) }

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

            Spacer(Modifier.height(32.dp))

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
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                "Your name is used for personalization and is cached in the LLM system prompt.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
