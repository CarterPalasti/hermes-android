package app.hermes.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hermes.ui.GatewayStatusUi
import app.hermes.ui.theme.NousBlue

@Composable
fun ProfilesScreen(
    profiles: List<ProfileUi>,
    error: String?,
    activeProfileName: String?,
    currentProcessProfile: String?,
    onRefresh: () -> Unit,
    onSwitch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Profiles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                val sticky = activeProfileName ?: profiles.firstOrNull { it.isActive }?.name
                val running = currentProcessProfile ?: profiles.firstOrNull { it.isCurrent }?.name
                Text(
                    buildString {
                        append("Sticky active: ")
                        append(sticky ?: "unknown")
                        if (!running.isNullOrBlank() && running != sticky) {
                            append(" · Server process: ")
                            append(running)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }

        Text(
            "Selecting a profile sets the sticky active profile (same as `hermes profile use`). " +
                "The running dashboard process may stay on its launch profile until restarted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        if (profiles.isEmpty() && error.isNullOrBlank()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.name }) { p ->
                val selected = p.isActive || p.name == activeProfileName
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, NousBlue, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSwitch(p.name) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSwitch(p.name) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            val meta = listOfNotNull(
                                p.provider?.takeIf { it.isNotBlank() },
                                p.model?.takeIf { it.isNotBlank() },
                            ).joinToString(" / ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.isActive || p.name == activeProfileName) {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = { Text("ACTIVE") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            disabledContainerColor = NousBlue,
                                            disabledLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    )
                                }
                                if (p.isCurrent || p.name == currentProcessProfile) {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = { Text("SERVER") },
                                        colors = AssistChipDefaults.assistChipColors(
                                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigScreen(
    raw: String,
    error: String?,
    saving: Boolean,
    onChange: (String) -> Unit,
    onReload: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onReload() }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Config", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReload) { Text("Reload") }
                Button(onClick = onSave, enabled = !saving) {
                    Text(if (saving) "Saving…" else "Save")
                }
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value = raw,
            onValueChange = onChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            label = { Text("config.yaml / raw") },
        )
    }
}

@Composable
fun GatewayScreen(
    status: GatewayStatusUi?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Gateway", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (status == null) {
            CircularProgressIndicator()
        } else {
            Text(
                if (status.ok) "OK · ${status.summary}" else "Issue · ${status.summary}",
                style = MaterialTheme.typography.titleMedium,
                color = if (status.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                status.details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun PairingScreen(
    text: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pairing", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
        Text(
            text.ifBlank { "Loading…" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun LogsScreen(
    text: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Logs", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
        Text(
            text.ifBlank { "No logs yet." },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 200.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun OpsScreen(
    output: String,
    busy: Boolean,
    onDoctor: () -> Unit,
    onBackup: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ops", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDoctor, enabled = !busy) { Text("Doctor") }
            OutlinedButton(onClick = onBackup, enabled = !busy) { Text("Backup") }
            OutlinedButton(onClick = onRestart, enabled = !busy) { Text("Restart") }
        }
        if (busy) CircularProgressIndicator()
        Text(
            output.ifBlank { "Run an action to see output." },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
fun SettingsScreen(
    darkTheme: Boolean?,
    onDarkTheme: (Boolean?) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionLabel: String = remember(context.packageName) {
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                val name = info.versionName ?: "?"
                val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                "Version $name  ·  Build $code"
            }.getOrDefault("Version unknown")
        }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("App Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        listOf(null to "System", false to "Light", true to "Dark").forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDarkTheme(value) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = darkTheme == value, onClick = { onDarkTheme(value) })
                Text(label)
            }
        }
        Button(onClick = onLogout, modifier = Modifier.padding(top = 16.dp)) {
            Text("Log out / clear session")
        }
        Text(
            versionLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
