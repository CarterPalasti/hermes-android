package app.hermes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hermes.net.HermesModelOption
import app.hermes.ui.components.BrandedEmptyState
import app.hermes.ui.components.MarkdownText
import app.hermes.ui.theme.NousBlue

private sealed class ChatRow {
    data class Primary(val message: ChatMessage) : ChatRow()
    data class Activity(val id: String, val items: List<ChatMessage>) : ChatRow()
}

private fun ChatMessage.isActivity() =
    kind == MessageKind.Tool || kind == MessageKind.Thinking

private fun buildChatRows(messages: List<ChatMessage>): List<ChatRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = mutableListOf<ChatRow>()
    var batch = mutableListOf<ChatMessage>()
    var batchStart = 0
    fun flush(end: Int) {
        if (batch.isEmpty()) return
        rows += ChatRow.Activity("act-${batch.first().id}-$batchStart-$end", batch.toList())
        batch = mutableListOf()
    }
    messages.forEachIndexed { index, msg ->
        if (msg.isActivity()) {
            if (batch.isEmpty()) batchStart = index
            batch += msg
        } else {
            flush(index)
            rows += ChatRow.Primary(msg)
        }
    }
    flush(messages.size)
    return rows
}

private fun activitySummary(items: List<ChatMessage>): String {
    val thinking = items.count { it.kind == MessageKind.Thinking }
    val tools = items.count { it.kind == MessageKind.Tool }
    return buildList {
        if (thinking > 0) add(if (thinking == 1) "thinking" else "$thinking thinking")
        if (tools > 0) add(if (tools == 1) "1 tool" else "$tools tools")
    }.joinToString(" · ").ifBlank { "${items.size} steps" }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    connectionState: AppViewModel.ConnectionState,
    isBusy: Boolean,
    pendingApproval: Boolean,
    systemLogs: List<String>,
    models: List<HermesModelOption>,
    selectedModel: String?,
    selectedProvider: String?,
    contextUsedTokens: Int,
    contextLimitTokens: Int,
    onToggleLogs: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onInterrupt: () -> Unit,
    onReconnect: () -> Unit,
    onRespondApproval: (String, String) -> Unit,
    onRespondClarify: (String, String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onSelectModel: (HermesModelOption) -> Unit,
    onRefreshModels: () -> Unit = {},
    scrollEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var logsExpanded by remember { mutableStateOf(false) }
    var expandAllActivity by remember { mutableStateOf(false) }
    val groupExpanded = remember { mutableStateMapOf<String, Boolean>() }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    val rows = remember(messages) { buildChatRows(messages) }
    val activityGroupCount = rows.count { it is ChatRow.Activity }

    LaunchedEffect(modelMenuOpen) {
        if (modelMenuOpen) onRefreshModels()
    }

    LaunchedEffect(scrollEpoch, rows.size, messages.lastOrNull()?.id) {
        if (rows.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(rows.lastIndex)
        kotlinx.coroutines.delay(32)
        listState.animateScrollToItem(rows.lastIndex)
    }

    val liveActivityId = remember(rows, isBusy) {
        val last = rows.lastOrNull() as? ChatRow.Activity
        if (isBusy && last != null && last.items.any { it.isStreaming || it.toolStatus == "running" }) last.id else null
    }

    Column(modifier = modifier.fillMaxSize()) {
            // Compact glance strip: conn · model · context%  (tap to expand controls)
            val limit = contextLimitTokens
            val used = contextUsedTokens
            val pct = if (limit > 0) (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
            val pctLabel = if (limit > 0) "${(pct * 100).toInt()}%" else "—"
            val connColor = when (connectionState) {
                AppViewModel.ConnectionState.OPEN -> Color(0xFF2E7D32)
                AppViewModel.ConnectionState.CONNECTING -> Color(0xFFF9A825)
                AppViewModel.ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                AppViewModel.ConnectionState.CLOSED -> MaterialTheme.colorScheme.error
                AppViewModel.ConnectionState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val connLabel = when (connectionState) {
                AppViewModel.ConnectionState.OPEN -> "OK"
                AppViewModel.ConnectionState.CONNECTING -> "…"
                AppViewModel.ConnectionState.ERROR -> "ERR"
                AppViewModel.ConnectionState.CLOSED -> "OFF"
                AppViewModel.ConnectionState.IDLE -> "—"
            }
            val shortModel = (selectedModel ?: "model").let {
                if (it.length > 22) it.take(20) + "…" else it
            }
            val barColor = when {
                pct >= 0.9f -> MaterialTheme.colorScheme.error
                pct >= 0.75f -> Color(0xFFFFA000)
                else -> NousBlue
            }

            Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        tonalElevation = 0.dp,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Connection
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(connColor),
                                    )
                                    Text(
                                        connLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = connColor,
                                        modifier = Modifier.padding(start = 4.dp),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                // Model picker — dedicated control (not nested under a row clickable)
                                Box {
                                    TextButton(
                                        onClick = {
                                            if (models.isEmpty()) {
                                                // still open so user sees empty state
                                            }
                                            modelMenuOpen = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.heightIn(max = 32.dp),
                                    ) {
                                        Text(
                                            shortModel,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.widthIn(max = 140.dp),
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Pick model",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = modelMenuOpen,
                                        onDismissRequest = { modelMenuOpen = false },
                                    ) {
                                        if (models.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No models loaded — open Manage → Models") },
                                                onClick = { modelMenuOpen = false },
                                            )
                                        } else {
                                            models.take(60).forEach { m ->
                                                val selected = m.model == selectedModel &&
                                                    (selectedProvider.isNullOrBlank() || m.provider == selectedProvider)
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            m.label,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                        )
                                                    },
                                                    onClick = {
                                                        modelMenuOpen = false
                                                        onSelectModel(m)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                // Context %
                                Text(
                                    pctLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = barColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (limit > 0) {
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp),
                                        color = barColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                if (connectionState == AppViewModel.ConnectionState.CLOSED ||
                                    connectionState == AppViewModel.ConnectionState.ERROR
                                ) {
                                    TextButton(
                                        onClick = onReconnect,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.heightIn(max = 32.dp),
                                    ) {
                                        Text("Retry", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(
                                    onClick = { statusExpanded = !statusExpanded },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        if (statusExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (statusExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            if (statusExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        buildString {
                                            append(selectedProvider ?: "provider")
                                            append(" / ")
                                            append(selectedModel ?: "—")
                                            if (limit > 0) append("  ·  $used / $limit tokens")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = { modelMenuOpen = true }) {
                                        Text("Change model")
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (systemLogs.isNotEmpty()) {
                                            TextButton(onClick = {
                                                logsExpanded = !logsExpanded
                                                onToggleLogs()
                                            }) {
                                                Text(if (logsExpanded) "Hide log" else "Session log (${systemLogs.size})")
                                            }
                                        }
                                        if (activityGroupCount > 0) {
                                            TextButton(onClick = {
                                                expandAllActivity = !expandAllActivity
                                                groupExpanded.clear()
                                            }) {
                                                Icon(
                                                    if (expandAllActivity) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.padding(end = 4.dp).size(16.dp),
                                                )
                                                Text(if (expandAllActivity) "Hide steps" else "Show steps")
                                            }
                                        }
                                    }
                                    if (logsExpanded && systemLogs.isNotEmpty()) {
                                        Column {
                                            systemLogs.takeLast(20).forEach { log ->
                                                Text(
                                                    log,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

            LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rows.isEmpty()) {
                item { BrandedEmptyState("Send a message to start a turn.") }
            }
            items(
                rows,
                key = {
                    when (it) {
                        is ChatRow.Primary -> it.message.id
                        is ChatRow.Activity -> it.id
                    }
                },
            ) { row ->
                when (row) {
                    is ChatRow.Primary -> PrimaryMessage(row.message, onRespondApproval, onRespondClarify)
                    is ChatRow.Activity -> {
                        val forced = row.id == liveActivityId
                        val expanded = when {
                            forced -> true
                            groupExpanded.containsKey(row.id) -> groupExpanded.getValue(row.id)
                            else -> expandAllActivity
                        }
                        ActivityGroupCard(
                            items = row.items,
                            expanded = expanded,
                            onToggleGroup = { groupExpanded[row.id] = !expanded },
                            onToggleItem = onToggleExpanded,
                        )
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp, shadowElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    placeholder = {
                        Text(
                            if (pendingApproval) "Respond to approval/clarify above…"
                            else "Message Hermes…",
                        )
                    },
                    maxLines = 5,
                    enabled = connectionState == AppViewModel.ConnectionState.OPEN && !isBusy && !pendingApproval,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                if (isBusy && !pendingApproval) {
                    FilledIconButton(
                        onClick = onInterrupt,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
                } else {
                    FilledIconButton(
                        onClick = {
                            val t = inputText.trim()
                            if (t.isNotEmpty()) {
                                onSendMessage(t)
                                inputText = ""
                            }
                        },
                        enabled = connectionState == AppViewModel.ConnectionState.OPEN &&
                            inputText.isNotBlank() && !pendingApproval && !isBusy,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = NousBlue,
                            contentColor = Color.White,
                        ),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                }
            }
        }
    }
}

@Composable
private fun PrimaryMessage(
    message: ChatMessage,
    onRespondApproval: (String, String) -> Unit,
    onRespondClarify: (String, String) -> Unit,
) {
    when (message.kind) {
        MessageKind.Approval -> ApprovalCard(message, onRespondApproval)
        MessageKind.Clarify -> ClarifyCard(message, onRespondClarify)
        else -> ChatBubble(message)
    }
}

@Composable
private fun ActivityGroupCard(
    items: List<ChatMessage>,
    expanded: Boolean,
    onToggleGroup: () -> Unit,
    onToggleItem: (String) -> Unit,
) {
    val summary = activitySummary(items)
    val toolNames = items.filter { it.kind == MessageKind.Tool }.mapNotNull { it.toolName }.distinct().take(3)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggleGroup).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (items.any { it.kind == MessageKind.Thinking }) Icons.Default.Psychology else Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        if (expanded) "Hide steps · $summary" else "Show steps · $summary",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (!expanded && toolNames.isNotEmpty()) {
                        Text(
                            toolNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { msg ->
                        when (msg.kind) {
                            MessageKind.Tool -> ToolCard(msg) { onToggleItem(msg.id) }
                            MessageKind.Thinking -> ThinkingCard(msg) { onToggleItem(msg.id) }
                            else -> ChatBubble(msg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system" || message.kind == MessageKind.System
    val backgroundColor = when {
        isUser -> NousBlue
        isSystem -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val contentColor = when {
        isUser -> Color.White
        isSystem -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val body = message.content.ifBlank { if (message.isStreaming) "…" else "" }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            shadowElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 0.96f),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    when {
                        isUser -> "You"
                        isSystem -> "System"
                        else -> "Hermes"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarkdownText(
                    markdown = body,
                    color = contentColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolCard(message: ChatMessage, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Build, null, tint = NousBlue)
                Spacer(Modifier.width(8.dp))
                Text(
                    message.toolName ?: "tool",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(onClick = onToggle, label = { Text(message.toolStatus ?: "…") })
                Icon(if (message.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (message.expanded) {
                Text(
                    message.content.ifBlank { "(no output)" }.let {
                        if (it.length > 4000) it.take(4000) + "\n…(truncated)" else it
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ThinkingCard(message: ChatMessage, onToggle: () -> Unit) {
    val showBody = message.expanded || message.isStreaming
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        message.isStreaming -> "Thinking…"
                        showBody -> "Thinking"
                        else -> "Thinking (tap to show)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (showBody) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (showBody) {
                Text(
                    message.content.ifBlank { "…" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(message: ChatMessage, onRespond: (String, String) -> Unit) {
    val id = message.approvalId ?: message.id
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            MarkdownText(message.content, MaterialTheme.colorScheme.onTertiaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val opts = message.approvalOptions.ifEmpty { listOf("allow_once", "allow_always", "deny") }
                opts.forEach { opt ->
                    val label = opt.replace('_', ' ')
                    if (opt.contains("deny", true)) {
                        OutlinedButton(onClick = { onRespond(id, opt) }) { Text(label) }
                    } else {
                        Button(
                            onClick = { onRespond(id, opt) },
                            colors = ButtonDefaults.buttonColors(containerColor = NousBlue),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClarifyCard(message: ChatMessage, onRespond: (String, String) -> Unit) {
    val id = message.clarifyId ?: message.id
    var freeText by remember(message.id) { mutableStateOf("") }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clarification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            MarkdownText(message.content, MaterialTheme.colorScheme.onSecondaryContainer)
            message.clarifyChoices.forEach { choice ->
                OutlinedButton(onClick = { onRespond(id, choice) }, modifier = Modifier.fillMaxWidth()) {
                    Text(choice)
                }
            }
            OutlinedTextField(
                value = freeText,
                onValueChange = { freeText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type your answer") },
                singleLine = true,
            )
            Button(
                onClick = {
                    val t = freeText.trim()
                    if (t.isNotEmpty()) onRespond(id, t)
                },
                enabled = freeText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NousBlue),
            ) { Text("Submit") }
        }
    }
}
