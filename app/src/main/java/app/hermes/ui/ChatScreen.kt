package app.hermes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.hermes.ui.theme.NousBlue

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    connectionState: AppViewModel.ConnectionState,
    isBusy: Boolean,
    onSendMessage: (String) -> Unit,
    onInterrupt: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.id, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (connectionState != AppViewModel.ConnectionState.OPEN) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (connectionState) {
                        AppViewModel.ConnectionState.CONNECTING -> "Connecting to gateway…"
                        AppViewModel.ConnectionState.CLOSED -> "Disconnected from gateway"
                        AppViewModel.ConnectionState.ERROR -> "Connection error"
                        AppViewModel.ConnectionState.IDLE -> "Gateway idle"
                        AppViewModel.ConnectionState.OPEN -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                if (connectionState == AppViewModel.ConnectionState.CLOSED ||
                    connectionState == AppViewModel.ConnectionState.ERROR
                ) {
                    TextButton(onClick = onReconnect) { Text("Retry") }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "Send a message to start a turn.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(messages, key = { it.id }, contentType = { it.role }) { message ->
                ChatBubble(message)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    placeholder = { Text("Message Hermes…") },
                    maxLines = 5,
                    enabled = connectionState == AppViewModel.ConnectionState.OPEN && !isBusy,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isBusy) {
                    FilledIconButton(
                        onClick = onInterrupt,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val text = inputText.trim()
                            if (text.isNotEmpty()) {
                                onSendMessage(text)
                                inputText = ""
                            }
                        },
                        enabled = connectionState == AppViewModel.ConnectionState.OPEN && inputText.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NousBlue,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val backgroundColor = when {
        isUser -> NousBlue
        isSystem -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val contentColor = when {
        isUser -> Color.White
        isSystem -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelColor = when {
        isUser -> Color.White.copy(alpha = 0.85f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            shadowElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 0.96f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = when (message.role) {
                        "user" -> "You"
                        "assistant" -> "Hermes"
                        "system" -> "System"
                        else -> message.role
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
                Text(
                    text = message.content.ifBlank { "…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
