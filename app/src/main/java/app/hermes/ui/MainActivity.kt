package app.hermes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hermes.ui.components.HermesBottomBar
import app.hermes.ui.components.HermesBrandedBackground
import app.hermes.ui.components.HermesTopBar
import app.hermes.ui.components.PlaceholderScreen
import app.hermes.ui.theme.HermesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = AppViewModel.Factory(applicationContext)
        setContent {
            // Follow system light/dark for readable white or black body
            HermesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthenticatedApp(viewModel = viewModel(factory = factory))
                }
            }
        }
    }
}

@Composable
fun AuthenticatedApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    val sessionsLoading by viewModel.sessionsLoading.collectAsStateWithLifecycle()
    val modelsError by viewModel.modelsError.collectAsStateWithLifecycle()
    val sessionsError by viewModel.sessionsError.collectAsStateWithLifecycle()
    val sessionId by viewModel.runtimeSessionId.collectAsStateWithLifecycle()
    val storedSessionId by viewModel.storedSessionId.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var managePage by remember { mutableStateOf(ManagePage.Hub) }

    when (val s = state) {
        is AppState.Unauthenticated -> LoginScreen(
            onLogin = viewModel::login,
            isLoading = isLoading,
            errorMessage = loginError,
        )

        is AppState.Authenticated -> {
            val topTitle: String
            val topSubtitle: String?
            val showBack: Boolean
            val onBack: (() -> Unit)?
            val onRefresh: (() -> Unit)?
            val onNewChat: (() -> Unit)?

            when (tab) {
                AppTab.Chat -> {
                    topTitle = "Chat"
                    topSubtitle = listOfNotNull(
                        selectedModel?.let { m -> "${selectedProvider ?: "model"} / $m" },
                        sessionId?.take(12)?.let { "sess $it" },
                    ).joinToString(" · ").ifBlank { s.baseUrl }
                    showBack = false
                    onBack = null
                    onRefresh = viewModel::reconnect
                    onNewChat = viewModel::newChat
                }
                AppTab.Sessions -> {
                    topTitle = "Sessions"
                    topSubtitle = "${sessions.size} recent"
                    showBack = false
                    onBack = null
                    onRefresh = viewModel::refreshSessions
                    onNewChat = viewModel::newChat
                }
                AppTab.Cron -> {
                    topTitle = "Cron"
                    topSubtitle = "Automations"
                    showBack = false
                    onBack = null
                    onRefresh = null
                    onNewChat = null
                }
                AppTab.Manage -> {
                    topTitle = if (managePage == ManagePage.Hub) "Manage" else managePage.title
                    topSubtitle = if (managePage == ManagePage.Hub) s.baseUrl else managePage.subtitle
                    showBack = managePage != ManagePage.Hub
                    onBack = { managePage = ManagePage.Hub }
                    onRefresh = when (managePage) {
                        ManagePage.Models -> viewModel::refreshModels
                        else -> null
                    }
                    onNewChat = null
                }
            }

            Scaffold(
                topBar = {
                    HermesTopBar(
                        title = topTitle,
                        subtitle = topSubtitle,
                        connectionState = connectionState,
                        showBack = showBack,
                        onBack = onBack,
                        onNewChat = onNewChat,
                        onRefresh = onRefresh,
                        onLogout = viewModel::logout,
                    )
                },
                bottomBar = {
                    HermesBottomBar(
                        selected = tab,
                        onSelect = { next ->
                            if (next == AppTab.Manage && tab != AppTab.Manage) {
                                managePage = ManagePage.Hub
                            }
                            viewModel.setTab(next)
                            when (next) {
                                AppTab.Sessions -> viewModel.refreshSessions()
                                AppTab.Manage -> if (managePage == ManagePage.Models) viewModel.refreshModels()
                                else -> Unit
                            }
                        },
                    )
                },
            ) { padding ->
                HermesBrandedBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        AppTab.Chat -> ChatScreen(
                            messages = messages,
                            connectionState = connectionState,
                            isBusy = isBusy,
                            onSendMessage = viewModel::sendMessage,
                            onInterrupt = viewModel::interrupt,
                            onReconnect = viewModel::reconnect,
                            modifier = Modifier.fillMaxSize(),
                        )
                        AppTab.Sessions -> SessionsScreen(
                            sessions = sessions,
                            isLoading = sessionsLoading,
                            error = sessionsError,
                            activeSessionId = storedSessionId,
                            onRefresh = viewModel::refreshSessions,
                            onOpenSession = viewModel::openStoredSession,
                            onNewChat = viewModel::newChat,
                            modifier = Modifier.fillMaxSize(),
                        )
                        AppTab.Cron -> CronScreen(modifier = Modifier.fillMaxSize())
                        AppTab.Manage -> when (managePage) {
                            ManagePage.Hub -> ManageHubScreen(
                                onOpen = { page ->
                                    managePage = page
                                    if (page == ManagePage.Models) viewModel.refreshModels()
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            ManagePage.Models -> ModelsScreen(
                                models = models,
                                selectedModel = selectedModel,
                                selectedProvider = selectedProvider,
                                isLoading = modelsLoading,
                                error = modelsError,
                                onRefresh = viewModel::refreshModels,
                                onSelect = viewModel::selectModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> PlaceholderScreen(
                                title = managePage.title,
                                body = managePage.subtitle +
                                    "\n\nThis surface is scaffolded for the locked v1 scope and will be wired next.",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
