package app.hermes.ui

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
)

sealed class AppState {
    data object Unauthenticated : AppState()
    data class Authenticated(val baseUrl: String) : AppState()
}

/** Primary bottom navigation destinations. */
enum class AppTab(val label: String) {
    Chat("Chat"),
    Sessions("Sessions"),
    Cron("Cron"),
    Manage("Manage"),
}

/** Screens under the Manage hub. */
enum class ManagePage(
    val title: String,
    val subtitle: String,
    val implemented: Boolean = false,
) {
    Hub("Manage", "Settings and system surfaces"),
    Models("Models", "Browse and set the active model", implemented = true),
    Profiles("Profiles", "Switch Hermes profiles"),
    Config("Config", "Schema-driven config editor"),
    Gateway("Gateway", "Status and messaging platforms"),
    Pairing("Pairing", "Approve or revoke device pairs"),
    Logs("Logs", "Filtered live log tail"),
    Ops("Ops", "Doctor, backup, debug share"),
    Settings("App settings", "Theme, text size, notifications"),
}
