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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hermes.net.HermesSessionSummary
import app.hermes.ui.theme.NousBlue

private fun sourceLabel(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return "unknown"
    return s.lowercase()
}

private fun sourceSortKey(source: String): Int = when (source) {
    "android" -> 0
    "desktop" -> 1
    "cli", "tui" -> 2
    "cron" -> 90
    "tool", "subagent", "kanban" -> 99
    else -> 50
}

@Composable
fun SessionsScreen(
    sessions: List<HermesSessionSummary>,
    isLoading: Boolean,
    error: String?,
    activeSessionId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSession: (HermesSessionSummary) -> Unit,
    onNewChat: () -> Unit,
    onRename: (HermesSessionSummary, String) -> Unit,
    onDelete: (HermesSessionSummary) -> Unit,
    onExport: (HermesSessionSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<HermesSessionSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<HermesSessionSummary?>(null) }

    val grouped = remember(sessions) {
        sessions
            .groupBy { sourceLabel(it.source) }
            .toList()
            .sortedWith(
                compareBy<Pair<String, List<HermesSessionSummary>>> { sourceSortKey(it.first) }
                    .thenBy { it.first },
            )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Search sessions") },
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${sessions.size} sessions · by source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
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
                        "No sessions yet. Start a new chat — it appears here after the first message.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Button(onClick = onNewChat, modifier = Modifier.padding(top = 12.dp)) { Text("New chat") }
                }
            }
            grouped.forEach { (source, items) ->
                item(key = "hdr-$source") {
                    Text(
                        "${source.uppercase()} · ${items.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (source == "android") NousBlue else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.id }) { s ->
                    SessionCard(
                        s = s,
                        active = s.id == activeSessionId,
                        menuOpen = menuFor == s.id,
                        onOpenMenu = { menuFor = s.id },
                        onDismissMenu = { menuFor = null },
                        onOpen = { onOpenSession(s) },
                        onRename = {
                            menuFor = null
                            renameTarget = s
                            renameText = s.title.orEmpty()
                        },
                        onExport = {
                            menuFor = null
                            onExport(s)
                        },
                        onDelete = {
                            menuFor = null
                            deleteTarget = s
                        },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = renameText.trim()
                    if (t.isNotEmpty()) onRename(target, t)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete session?") },
            text = { Text("This removes ${target.id} from the server.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionCard(
    s: HermesSessionSummary,
    active: Boolean,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = s.title?.takeIf { it.isNotBlank() }
        ?: s.preview?.take(80)?.takeIf { it.isNotBlank() }
        ?: s.id
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
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
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (active) {
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
                    DropdownMenuItem(text = { Text("Export") }, onClick = onExport)
                    DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(sourceLabel(s.source)) },
                )
                Text(
                    buildString {
                        if (!s.profileName.isNullOrBlank()) append(s.profileName).append(" · ")
                        append(s.id.take(18))
                        if (!s.model.isNullOrBlank()) append(" · ").append(s.model)
                        append(" · ").append(s.messageCount).append(" msgs")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
