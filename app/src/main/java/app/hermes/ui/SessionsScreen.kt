package app.hermes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hermes.net.HermesSessionSummary

@Composable
fun SessionsScreen(
    sessions: List<HermesSessionSummary>,
    isLoading: Boolean,
    error: String?,
    activeSessionId: String?,
    onRefresh: () -> Unit,
    onOpenSession: (HermesSessionSummary) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (isLoading && sessions.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            if (!isLoading && sessions.isEmpty() && error.isNullOrBlank()) {
                item {
                    Text(
                        "No sessions yet. Start a new chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Button(onClick = onNewChat, modifier = Modifier.padding(top = 12.dp)) {
                        Text("New chat")
                    }
                }
            }
            items(sessions, key = { it.id }) { s ->
                val title = s.title?.takeIf { it.isNotBlank() }
                    ?: s.preview?.take(80)?.takeIf { it.isNotBlank() }
                    ?: s.id
                val active = s.id == activeSessionId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(s) },
                    colors = CardDefaults.cardColors(
                                            containerColor = if (active) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                            } else {
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                            },
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (active) {
                                Text(
                                    "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            buildString {
                                append(s.id.take(18))
                                if (!s.model.isNullOrBlank()) append(" · ").append(s.model)
                                append(" · ").append(s.messageCount).append(" msgs")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (!s.preview.isNullOrBlank() && s.title != null) {
                            Text(
                                s.preview,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
