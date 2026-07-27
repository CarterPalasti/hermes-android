package app.hermes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import app.hermes.ui.AppTab
import app.hermes.ui.AppViewModel
import app.hermes.ui.theme.NousBlue
import app.hermes.ui.theme.NousSnow
import app.hermes.ui.theme.NousWhite

/**
 * Readable page canvas (white/black) with a faint Hermes watermark,
 * similar to the decorative art behind website content sections.
 */
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NousSnow,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesTopBar(
    title: String,
    subtitle: String? = null,
    connectionState: AppViewModel.ConnectionState? = null,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
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
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(28.dp),
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
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
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
            if (showBack && onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NousWhite)
                }
            }
        },
        actions = {
            if (connectionState != null) {
                ConnectionChip(
                    connectionState = connectionState,
                    modifier = Modifier.padding(end = 4.dp),
                )
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
fun HermesBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        tonalElevation = 3.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tabIcon(tab),
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(tab.label.uppercase(), fontWeight = FontWeight.Medium, fontSize = 11.sp)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NousWhite,
                    selectedTextColor = NousBlue,
                    indicatorColor = NousBlue,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.Chat -> Icons.AutoMirrored.Filled.Chat
    AppTab.Sessions -> Icons.Default.History
    AppTab.Cron -> Icons.Default.Schedule
    AppTab.Manage -> Icons.Default.Settings
}

@Composable
fun PlaceholderScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = "Coming soon — shell is ready.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
