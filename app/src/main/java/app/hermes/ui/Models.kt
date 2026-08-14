package app.hermes.ui

/** Chat / transcript item kinds rendered in ChatScreen. */
enum class MessageKind {
    Text,
    Tool,
    Thinking,
    Approval,
    Clarify,
    System,
}

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val kind: MessageKind = when (role) {
        "system" -> MessageKind.System
        else -> MessageKind.Text
    },
    val toolName: String? = null,
    val toolStatus: String? = null,
    val approvalId: String? = null,
    val approvalOptions: List<String> = emptyList(),
    val clarifyId: String? = null,
    val clarifyChoices: List<String> = emptyList(),
    val expanded: Boolean = false,
    val isStreaming: Boolean = false,
)

sealed class AppState {
    data object Unauthenticated : AppState()
    data class Authenticated(val baseUrl: String) : AppState()
}

enum class AppTab(val label: String) {
    Chat("Chat"),
    Sessions("Sessions"),
    Cron("Cron"),
    Manage("Manage"),
}

enum class ManagePage(
    val title: String,
    val subtitle: String,
    val implemented: Boolean,
) {
    Hub("Manage", "Hub", true),
    Models("Models", "Browse and select active model", true),
    Profiles("Profiles", "List and switch Hermes profiles", true),
    Config("Config", "Edit config.yaml (raw)", true),
    Gateway("Gateway", "Status and platform health", true),
    Pairing("Pairing", "Approve device pairings", true),
    Logs("Logs", "Tail recent server logs", true),
    Ops("Ops", "Doctor, backup, restart", true),
    Settings("App Settings", "Theme and local preferences", true),
}

data class CronJobUi(
    val id: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean,
    val lastStatus: String?,
)

data class ProfileUi(
    val name: String,
    val isActive: Boolean,
    /** Profile the running dashboard/gateway process is scoped to. */
    val isCurrent: Boolean = false,
    val path: String? = null,
    val model: String? = null,
    val provider: String? = null,
)

data class LogLineUi(
    val level: String,
    val message: String,
    val ts: String? = null,
)

data class GatewayStatusUi(
    val ok: Boolean,
    val summary: String,
    val details: String,
)
