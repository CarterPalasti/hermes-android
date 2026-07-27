package app.hermes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hermes.ui.components.PlaceholderScreen

@Composable
fun CronScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Cron / Automations",
        body = "List scheduled jobs, trigger runs, pause/resume. REST-backed UI lands in Phase 4.",
        modifier = modifier,
    )
}
