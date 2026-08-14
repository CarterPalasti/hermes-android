package app.hermes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hermes.R
import app.hermes.net.HermesSessionSummary
import app.hermes.ui.AppTab
import app.hermes.ui.AppViewModel
import app.hermes.ui.theme.NousBlue
import app.hermes.ui.theme.NousSnow
import app.hermes.ui.theme.NousWhite

@Composable
fun HermesBrandedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Image(
            painter = painterResource(id = R.drawable.hermes_watermark),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.72f)
                .alpha(if (dark) 0.07f else 0.06f),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(
                if (dark) Color(0xFF9A9AFF) else NousBlue,
            ),
        )
        content()
    }
}

@Composable
fun ConnectionChip(
    connectionState: AppViewModel.ConnectionState,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (connectionState) {
        AppViewModel.ConnectionState.OPEN -> "Connected" to Color(0xFF7CFFB2)
        AppViewModel.ConnectionState.CONNECTING -> "Connecting" to Color(0xFFFFE082)
        AppViewModel.ConnectionState.ERROR -> "Error" to Color(0xFFFF8A80)
        AppViewModel.ConnectionState.CLOSED -> "Offline" to NousSnow.copy(alpha = 0.75f)
        AppViewModel.ConnectionState.IDLE -> "Idle" to NousSnow.copy(alpha = 0.75f)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = NousSnow)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesTopBar(
    title: String,
    subtitle: String? = null,
    sessionTitle: String? = null,
    connectionState: AppViewModel.ConnectionState? = null,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.hermes_mark),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp).size(28.dp),
                    contentScale = ContentScale.Fit,
                )
                Column {
                    Text(
                        text = title.uppercase(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    val sub = sessionTitle?.takeIf { it.isNotBlank() } ?: subtitle
                    if (!sub.isNullOrBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = NousSnow.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            when {
                showBack && onBack != null -> {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NousWhite)
                    }
                }
                onOpenDrawer != null -> {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = NousWhite)
                    }
                }
            }
        },
        actions = {
            if (connectionState != null) {
                ConnectionChip(connectionState = connectionState, modifier = Modifier.padding(end = 4.dp))
            }
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NousWhite)
                }
            }
            if (onNewChat != null) {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = NousWhite)
                }
            }
            if (onLogout != null) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = NousWhite)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = NousBlue,
            titleContentColor = NousWhite,
            actionIconContentColor = NousWhite,
            navigationIconContentColor = NousWhite,
        ),
    )
}

@Composable
fun HermesNavDrawerContent(
    selected: AppTab,
    connectionState: AppViewModel.ConnectionState,
    profileLabel: String?,
    recentSessions: List<HermesSessionSummary>,
    activeSessionId: String?,
    onSelectTab: (AppTab) -> Unit,
    onOpenSession: (HermesSessionSummary) -> Unit,
    onSeeAllSessions: () -> Unit,
    onNewChat: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chatExpanded by remember { mutableStateOf(true) }
    ModalDrawerSheet(modifier = modifier.width(300.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Text(
                "HERMES AGENT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NousBlue,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (!profileLabel.isNullOrBlank()) {
                Text(
                    "profile $profileLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            ConnectionChip(
                connectionState = connectionState,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Chat expandable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { chatExpanded = !chatExpanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = NousBlue)
                Text(
                    "Chat",
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    if (chatExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
            if (chatExpanded) {
                NavigationDrawerItem(
                    label = { Text("Current") },
                    selected = selected == AppTab.Chat,
                    onClick = { onSelectTab(AppTab.Chat) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = NousBlue.copy(alpha = 0.15f)),
                )
                Text(
                    "Recent sessions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 4.dp),
                )
                recentSessions.take(8).forEach { s ->
                                    val src = s.source?.takeIf { it.isNotBlank() }?.lowercase()
                                    val label = buildString {
                                        val base = s.title?.takeIf { it.isNotBlank() }
                                            ?: s.preview?.take(40)?.takeIf { it.isNotBlank() }
                                            ?: s.id.take(18)
                                        append(base)
                                        if (!src.isNullOrBlank()) append(" · ").append(src)
                                    }
                                    NavigationDrawerItem(
                                        label = {
                                            Text(
                                                label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        selected = s.id == activeSessionId,
                                        onClick = { onOpenSession(s) },
                                        modifier = Modifier.padding(horizontal = 12.dp).heightIn(max = 48.dp),
                                    )
                                }
                TextButton(
                    onClick = onSeeAllSessions,
                    modifier = Modifier.padding(start = 16.dp),
                ) { Text("See all sessions") }
                TextButton(
                    onClick = onNewChat,
                    modifier = Modifier.padding(start = 16.dp),
                ) { Text("New chat") }
            }

            NavigationDrawerItem(
                label = { Text("Cron") },
                selected = selected == AppTab.Cron,
                onClick = { onSelectTab(AppTab.Cron) },
                icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = NousBlue.copy(alpha = 0.15f)),
            )
            NavigationDrawerItem(
                label = { Text("Manage") },
                selected = selected == AppTab.Manage,
                onClick = { onSelectTab(AppTab.Manage) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = NousBlue.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text("Log out") },
                selected = false,
                onClick = onLogout,
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
fun BrandedEmptyState(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
