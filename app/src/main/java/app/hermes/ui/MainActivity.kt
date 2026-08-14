package app.hermes.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hermes.ui.components.HermesBrandedBackground
import app.hermes.ui.components.HermesNavDrawerContent
import app.hermes.ui.components.HermesTopBar
import app.hermes.ui.theme.HermesTheme
import app.hermes.ui.util.Haptics
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = AppViewModel.Factory(applicationContext)
        setContent {
            val vm: AppViewModel = viewModel(factory = factory)
                        val darkPref by vm.darkTheme.collectAsStateWithLifecycle()
                        HermesTheme(darkTheme = darkPref ?: androidx.compose.foundation.isSystemInDarkTheme()) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                AuthenticatedApp(viewModel = vm)
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
    val systemLogs by viewModel.systemLogs.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val pickerModels by viewModel.pickerModels.collectAsStateWithLifecycle()
    val pickerAllowlist by viewModel.pickerAllowlist.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val contextUsedTokens by viewModel.contextUsedTokens.collectAsStateWithLifecycle()
    val contextLimitTokens by viewModel.contextLimitTokens.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    val sessionsLoading by viewModel.sessionsLoading.collectAsStateWithLifecycle()
    val modelsError by viewModel.modelsError.collectAsStateWithLifecycle()
    val sessionsError by viewModel.sessionsError.collectAsStateWithLifecycle()
    val sessionQuery by viewModel.sessionQuery.collectAsStateWithLifecycle()
    val sessionId by viewModel.runtimeSessionId.collectAsStateWithLifecycle()
    val storedSessionId by viewModel.storedSessionId.collectAsStateWithLifecycle()
    val chatScrollEpoch by viewModel.chatScrollEpoch.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val runningSessionIds by viewModel.runningSessionIds.collectAsStateWithLifecycle()
    val pendingApproval by viewModel.pendingApproval.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val profilesError by viewModel.profilesError.collectAsStateWithLifecycle()
    val activeProfileName by viewModel.activeProfileName.collectAsStateWithLifecycle()
    val currentProcessProfile by viewModel.currentProcessProfile.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val cronJobs by viewModel.cronJobs.collectAsStateWithLifecycle()
    val cronLoading by viewModel.cronLoading.collectAsStateWithLifecycle()
    val cronError by viewModel.cronError.collectAsStateWithLifecycle()
    val configRaw by viewModel.configRaw.collectAsStateWithLifecycle()
    val configError by viewModel.configError.collectAsStateWithLifecycle()
    val configSaving by viewModel.configSaving.collectAsStateWithLifecycle()
    val gatewayStatus by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val pairingText by viewModel.pairingText.collectAsStateWithLifecycle()
    val logsText by viewModel.logsText.collectAsStateWithLifecycle()
    val opsOutput by viewModel.opsOutput.collectAsStateWithLifecycle()
    val manageBusy by viewModel.manageBusy.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.onAppForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var managePage by remember { mutableStateOf(ManagePage.Hub) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    when (val s = state) {
        is AppState.Unauthenticated -> LoginScreen(
            onLogin = viewModel::login,
            isLoading = isLoading,
            errorMessage = loginError,
        )

        is AppState.Authenticated -> {
            fun selectTab(next: AppTab) {
                if (next == AppTab.Manage && tab != AppTab.Manage) managePage = ManagePage.Hub
                viewModel.setTab(next)
                when (next) {
                    AppTab.Sessions -> viewModel.refreshSessions()
                    AppTab.Cron -> viewModel.refreshCron()
                    AppTab.Manage -> if (managePage == ManagePage.Models) viewModel.refreshModels()
                    else -> Unit
                }
                scope.launch { drawerState.close() }
            }

            BackHandler(enabled = true) {
                when {
                    drawerState.isOpen -> scope.launch { drawerState.close() }
                    tab == AppTab.Manage && managePage != ManagePage.Hub -> managePage = ManagePage.Hub
                    tab != AppTab.Chat -> selectTab(AppTab.Chat)
                    else -> (view.context as? ComponentActivity)?.finish()
                }
            }

            val activeSessionSummary = sessions.firstOrNull { it.id == storedSessionId }
                ?: sessions.firstOrNull { it.id == sessionId }
            val sessionTitle = activeSessionSummary?.title?.takeIf { it.isNotBlank() }
                ?: activeSessionSummary?.preview?.take(40)?.ifBlank { null }
                ?: if (tab == AppTab.Chat) "New chat" else null

            val topTitle: String
            val topSubtitle: String?
            val showBack: Boolean
            val onBack: (() -> Unit)?
            val onRefresh: (() -> Unit)?
            val onNewChat: (() -> Unit)?

            when (tab) {
                AppTab.Chat -> {
                                    topTitle = "Chat"
                                    // Compact: model/context/conn live in ChatScreen status strip.
                                    topSubtitle = null
                                    showBack = false
                                    onBack = null
                                    onRefresh = viewModel::reconnect
                                    onNewChat = viewModel::newChat
                                }
                AppTab.Sessions -> {
                    topTitle = "All sessions"
                    topSubtitle = "${sessions.size} total"
                    showBack = true
                    onBack = { selectTab(AppTab.Chat) }
                    onRefresh = viewModel::refreshSessions
                    onNewChat = viewModel::newChat
                }
                AppTab.Cron -> {
                    topTitle = "Cron"
                    topSubtitle = "Automations"
                    showBack = false
                    onBack = null
                    onRefresh = viewModel::refreshCron
                    onNewChat = null
                }
                AppTab.Manage -> {
                    topTitle = if (managePage == ManagePage.Hub) "Manage" else managePage.title
                    topSubtitle = if (managePage == ManagePage.Hub) s.baseUrl else managePage.subtitle
                    showBack = managePage != ManagePage.Hub
                    onBack = { managePage = ManagePage.Hub }
                    onRefresh = when (managePage) {
                        ManagePage.Models -> viewModel::refreshModels
                        ManagePage.Profiles -> viewModel::refreshProfiles
                        ManagePage.Gateway -> viewModel::refreshGateway
                        ManagePage.Pairing -> viewModel::refreshPairing
                        ManagePage.Logs -> viewModel::refreshLogs
                        else -> null
                    }
                    onNewChat = null
                }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    HermesNavDrawerContent(
                        selected = tab,
                        connectionState = connectionState,
                        profileLabel = activeProfileName,
                        recentSessions = sessions,
                        activeSessionId = storedSessionId,
                        onSelectTab = { selectTab(it) },
                        onOpenSession = { summary ->
                            viewModel.openStoredSession(summary)
                            scope.launch { drawerState.close() }
                        },
                        onSeeAllSessions = { selectTab(AppTab.Sessions) },
                        onNewChat = {
                            viewModel.newChat()
                            scope.launch { drawerState.close() }
                        },
                        onLogout = viewModel::logout,
                    )
                },
            ) {
                Scaffold(
                    topBar = {
                        HermesTopBar(
                                                    title = topTitle,
                                                    subtitle = if (tab == AppTab.Chat) null else topSubtitle,
                                                    // Chat keeps session title only; conn/model live in status strip.
                                                    sessionTitle = if (tab == AppTab.Chat) sessionTitle else null,
                                                    connectionState = if (tab == AppTab.Chat) null else connectionState,
                                                    showBack = showBack,
                                                    onBack = onBack,
                                                    onOpenDrawer = {
                                                        scope.launch { drawerState.open() }
                                                        Haptics.performLight(view)
                                                    },
                                                    onNewChat = onNewChat,
                            onRefresh = onRefresh,
                            onLogout = viewModel::logout,
                        )
                    },
                ) { padding ->
                    HermesBrandedBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            AppTab.Chat -> ChatScreen(
                                messages = messages,
                                connectionState = connectionState,
                                isBusy = isBusy,
                                pendingApproval = pendingApproval,
                                systemLogs = systemLogs,
                                models = pickerModels,
                                selectedModel = selectedModel,
                                selectedProvider = selectedProvider,
                                contextUsedTokens = contextUsedTokens,
                                contextLimitTokens = contextLimitTokens,
                                onSendMessage = viewModel::sendMessage,
                                onInterrupt = viewModel::interrupt,
                                onReconnect = viewModel::reconnect,
                                onRespondApproval = viewModel::respondApproval,
                                onRespondClarify = viewModel::respondClarify,
                                onToggleExpanded = viewModel::toggleExpanded,
                                onSelectModel = viewModel::selectModel,
                                onRefreshModels = viewModel::refreshModels,
                                pendingAttachments = pendingAttachments,
                                onAddAttachment = viewModel::addAttachmentFromUri,
                                onRemoveAttachment = viewModel::removeAttachment,
                                scrollEpoch = chatScrollEpoch,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                            AppTab.Sessions -> SessionsScreen(
                                sessions = sessions,
                                isLoading = sessionsLoading,
                                error = sessionsError,
                                activeSessionId = storedSessionId,
                                query = sessionQuery,
                                onQueryChange = viewModel::setSessionQuery,
                                onRefresh = viewModel::refreshSessions,
                                onOpenSession = viewModel::openStoredSession,
                                onNewChat = viewModel::newChat,
                                onRename = viewModel::renameSession,
                                onDelete = viewModel::deleteSession,
                                onExport = { s ->
                                    viewModel.exportSession(s) { /* body available for share later */ }
                                },
                                runningIds = runningSessionIds,
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppTab.Cron -> CronScreen(
                                jobs = cronJobs,
                                isLoading = cronLoading,
                                error = cronError,
                                onRefresh = viewModel::refreshCron,
                                onRun = { id -> viewModel.cronAction(id, "run") },
                                onPause = { id -> viewModel.cronAction(id, "pause") },
                                onResume = { id -> viewModel.cronAction(id, "resume") },
                                modifier = Modifier.fillMaxSize(),
                            )
                            AppTab.Manage -> when (managePage) {
                                ManagePage.Hub -> ManageHubScreen(
                                    onOpen = { page ->
                                        managePage = page
                                        when (page) {
                                            ManagePage.Models -> viewModel.refreshModels()
                                            ManagePage.Profiles -> viewModel.refreshProfiles()
                                            ManagePage.Config -> viewModel.loadConfig()
                                            ManagePage.Gateway -> viewModel.refreshGateway()
                                            ManagePage.Pairing -> viewModel.refreshPairing()
                                            ManagePage.Logs -> viewModel.refreshLogs()
                                            else -> Unit
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Models -> ModelsScreen(
                                    models = models,
                                    selectedModel = selectedModel,
                                    selectedProvider = selectedProvider,
                                    pickerAllowlist = pickerAllowlist,
                                    isLoading = modelsLoading,
                                    error = modelsError,
                                    onRefresh = viewModel::refreshModels,
                                    onSelect = viewModel::selectModel,
                                    onTogglePicker = viewModel::toggleChatPickerModel,
                                    onSetProviderVisible = viewModel::setProviderInChatPicker,
                                    onResetPicker = viewModel::resetChatPickerToAll,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Profiles -> ProfilesScreen(
                                    profiles = profiles,
                                    error = profilesError,
                                    activeProfileName = activeProfileName,
                                    currentProcessProfile = currentProcessProfile,
                                    onRefresh = viewModel::refreshProfiles,
                                    onSwitch = viewModel::switchProfile,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Config -> ConfigScreen(
                                    raw = configRaw,
                                    error = configError,
                                    saving = configSaving,
                                    onChange = viewModel::setConfigRaw,
                                    onReload = viewModel::loadConfig,
                                    onSave = viewModel::saveConfig,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Gateway -> GatewayScreen(
                                    status = gatewayStatus,
                                    onRefresh = viewModel::refreshGateway,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Pairing -> PairingScreen(
                                    text = pairingText,
                                    onRefresh = viewModel::refreshPairing,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Logs -> LogsScreen(
                                    text = logsText,
                                    onRefresh = viewModel::refreshLogs,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Ops -> OpsScreen(
                                    output = opsOutput,
                                    busy = manageBusy,
                                    onDoctor = viewModel::runDoctor,
                                    onBackup = viewModel::runBackup,
                                    onRestart = viewModel::restartGateway,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                ManagePage.Settings -> SettingsScreen(
                                    darkTheme = darkTheme,
                                    onDarkTheme = viewModel::setDarkTheme,
                                    onLogout = viewModel::logout,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
