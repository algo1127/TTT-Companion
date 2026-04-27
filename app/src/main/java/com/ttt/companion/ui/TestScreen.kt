package com.ttt.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttt.companion.llm.LlmService

@Composable
fun TestScreen(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Model loading status bar
        when (val state = modelState) {
            LlmService.LoadState.Loading ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is LlmService.LoadState.Error ->
                Text(
                    text = "⚠ ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            else -> {}
        }

        // Character name header
        Text(
            text = viewModel.character.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        HorizontalDivider()

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = msg.content,
                            modifier = Modifier.padding(10.dp, 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (isLoading) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(24.dp))
                }
            }
        }

        HorizontalDivider()

        // Input row
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say something...") },
                enabled = modelState == LlmService.LoadState.Ready && !isLoading,
                singleLine = false,
                maxLines = 4
            )
            Button(
                onClick = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = modelState == LlmService.LoadState.Ready
                        && !isLoading && inputText.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}