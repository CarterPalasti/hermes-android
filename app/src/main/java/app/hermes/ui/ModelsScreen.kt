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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hermes.net.HermesModelOption

@Composable
fun ModelsScreen(
    models: List<HermesModelOption>,
    selectedModel: String?,
    selectedProvider: String?,
    pickerAllowlist: Set<String>,
    isLoading: Boolean,
    error: String?,
    @Suppress("UNUSED_PARAMETER") onRefresh: () -> Unit,
    onSelect: (HermesModelOption) -> Unit,
    onTogglePicker: (HermesModelOption) -> Unit,
    onSetProviderVisible: (String, Boolean) -> Unit,
    onResetPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grouped = remember(models) { models.groupBy { it.provider } }
    val showAll = pickerAllowlist.isEmpty()
    val visibleCount = if (showAll) models.size else models.count { it.pickerKey in pickerAllowlist }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Active: ${selectedProvider ?: "?"} / ${selectedModel ?: "?"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Selecting a radio sets the DEFAULT model. The in-chat picker only changes its own chat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = if (showAll) {
                "Chat picker shows all ${models.size} models. Uncheck any to hide them."
            } else {
                "Chat picker shows $visibleCount of ${models.size} models."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        if (!showAll) {
            TextButton(onClick = onResetPicker) { Text("Show all in chat picker") }
        }

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (isLoading && models.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (provider, items) ->
                val providerShown = showAll || items.all { it.pickerKey in pickerAllowlist }
                item(key = "hdr-$provider") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = providerShown,
                            onCheckedChange = { onSetProviderVisible(provider, it) },
                        )
                        Text(
                            "${items.firstOrNull()?.providerName?.ifBlank { provider } ?: provider} · ${items.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onSetProviderVisible(provider, !providerShown) },
                        )
                    }
                }
                items(items, key = { it.pickerKey }) { m ->
                    val selected = m.model == selectedModel &&
                        (selectedProvider.isNullOrBlank() || m.provider == selectedProvider)
                    val inPicker = showAll || m.pickerKey in pickerAllowlist
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            },
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = { onSelect(m) })
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(m) }
                                    .padding(end = 8.dp),
                            ) {
                                Text(m.model, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (inPicker) "In chat picker" else "Hidden from chat picker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Checkbox(
                                checked = inPicker,
                                onCheckedChange = { onTogglePicker(m) },
                            )
                        }
                    }
                }
            }
        }
    }
}
