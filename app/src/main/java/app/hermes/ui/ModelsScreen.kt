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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hermes.net.HermesModelOption

@Composable
fun ModelsScreen(
    models: List<HermesModelOption>,
    selectedModel: String?,
    selectedProvider: String?,
    isLoading: Boolean,
    error: String?,
        @Suppress("UNUSED_PARAMETER") onRefresh: () -> Unit,
        onSelect: (HermesModelOption) -> Unit,
        modifier: Modifier = Modifier,
    ) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Selected: ${selectedProvider ?: "?"} / ${selectedModel ?: "?"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (!error.isNullOrBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (isLoading && models.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(models, key = { "${it.provider}::${it.model}" }) { m ->
                val selected = m.model == selectedModel &&
                    (selectedProvider.isNullOrBlank() || m.provider == selectedProvider)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(m) },
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
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(m) })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(m.model, style = MaterialTheme.typography.titleSmall)
                            Text(
                                m.providerName.ifBlank { m.provider },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
