package app.hermes.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.hermes.net.HermesApi
import app.hermes.net.HermesModelOption
import app.hermes.net.HermesSessionSummary
import app.hermes.net.LoginHelper
import app.hermes.net.SharedCookieJar
import app.hermes.net.gateway.GatewayWsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AppViewModel(private val context: Context) : ViewModel() {

    enum class ConnectionState { IDLE, CONNECTING, OPEN, CLOSED, ERROR }

    private val _state = MutableStateFlow<AppState>(AppState.Unauthenticated)
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _tab = MutableStateFlow(AppTab.Chat)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()
    private val _models = MutableStateFlow<List<HermesModelOption>>(emptyList())
    val models: StateFlow<List<HermesModelOption>> = _models.asStateFlow()
    private val _sessions = MutableStateFlow<List<HermesSessionSummary>>(emptyList())
    val sessions: StateFlow<List<HermesSessionSummary>> = _sessions.asStateFlow()
    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()
    private val _selectedProvider = MutableStateFlow<String?>(null)
    val selectedProvider: StateFlow<String?> = _selectedProvider.asStateFlow()
    private val _contextLimitTokens = MutableStateFlow(0)
    val contextLimitTokens: StateFlow<Int> = _contextLimitTokens.asStateFlow()
    private val _contextUsedTokens = MutableStateFlow(0)
    val contextUsedTokens: StateFlow<Int> = _contextUsedTokens.asStateFlow()
    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()
    private val _sessionsLoading = MutableStateFlow(false)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading.asStateFlow()
    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()
    private val _sessionsError = MutableStateFlow<String?>(null)
    val sessionsError: StateFlow<String?> = _sessionsError.asStateFlow()
    private val _sessionQuery = MutableStateFlow("")
    val sessionQuery: StateFlow<String> = _sessionQuery.asStateFlow()
    private val _runtimeSessionId = MutableStateFlow<String?>(null)
    val runtimeSessionId: StateFlow<String?> = _runtimeSessionId.asStateFlow()
    private val _storedSessionId = MutableStateFlow<String?>(null)
    val storedSessionId: StateFlow<String?> = _storedSessionId.asStateFlow()
    private val _chatScrollEpoch = MutableStateFlow(0)
    val chatScrollEpoch: StateFlow<Int> = _chatScrollEpoch.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val _pendingApproval = MutableStateFlow(false)
    val pendingApproval: StateFlow<Boolean> = _pendingApproval.asStateFlow()
    private val _profiles = MutableStateFlow<List<ProfileUi>>(emptyList())
    val profiles: StateFlow<List<ProfileUi>> = _profiles.asStateFlow()
    private val _profilesError = MutableStateFlow<String?>(null)
    val profilesError: StateFlow<String?> = _profilesError.asStateFlow()
    private val _activeProfileName = MutableStateFlow<String?>(null)
    val activeProfileName: StateFlow<String?> = _activeProfileName.asStateFlow()
    private val _currentProcessProfile = MutableStateFlow<String?>(null)
    val currentProcessProfile: StateFlow<String?> = _currentProcessProfile.asStateFlow()
    private val _darkTheme = MutableStateFlow<Boolean?>(null)
    val darkTheme: StateFlow<Boolean?> = _darkTheme.asStateFlow()
    private val _cronJobs = MutableStateFlow<List<CronJobUi>>(emptyList())
    val cronJobs: StateFlow<List<CronJobUi>> = _cronJobs.asStateFlow()
    private val _cronLoading = MutableStateFlow(false)
    val cronLoading: StateFlow<Boolean> = _cronLoading.asStateFlow()
    private val _cronError = MutableStateFlow<String?>(null)
    val cronError: StateFlow<String?> = _cronError.asStateFlow()
    private val _configRaw = MutableStateFlow("")
    val configRaw: StateFlow<String> = _configRaw.asStateFlow()
    private val _configError = MutableStateFlow<String?>(null)
    val configError: StateFlow<String?> = _configError.asStateFlow()
    private val _configSaving = MutableStateFlow(false)
    val configSaving: StateFlow<Boolean> = _configSaving.asStateFlow()
    private val _gatewayStatus = MutableStateFlow<GatewayStatusUi?>(null)
    val gatewayStatus: StateFlow<GatewayStatusUi?> = _gatewayStatus.asStateFlow()
    private val _pairingText = MutableStateFlow("")
    val pairingText: StateFlow<String> = _pairingText.asStateFlow()
    private val _logsText = MutableStateFlow("")
    val logsText: StateFlow<String> = _logsText.asStateFlow()
    private val _opsOutput = MutableStateFlow("")
    val opsOutput: StateFlow<String> = _opsOutput.asStateFlow()
    private val _manageBusy = MutableStateFlow(false)
    val manageBusy: StateFlow<Boolean> = _manageBusy.asStateFlow()

    private var wsClient: GatewayWsClient? = null
    private var api: HermesApi? = null
    private var currentBaseUrl: String? = null
    private var currentTicket: String? = null
    private var streamingAssistantId: String? = null
    private var streamingThinkingId: String? = null
    private var lastUsername: String? = null
    private var lastPassword: String? = null
    private val idSeq = java.util.concurrent.atomic.AtomicLong(0)
    private fun newId(prefix: String) = "$prefix-${System.currentTimeMillis()}-${idSeq.incrementAndGet()}"

    init {
        if (DevCredentials.AUTO_LOGIN) {
            login(DevCredentials.GATEWAY_URL, DevCredentials.USERNAME, DevCredentials.PASSWORD)
        }
    }

    fun setTab(tab: AppTab) { _tab.value = tab }
    fun setSessionQuery(q: String) {
        _sessionQuery.value = q
        refreshSessions()
    }
    fun setDarkTheme(v: Boolean?) { _darkTheme.value = v }
    fun setConfigRaw(raw: String) { _configRaw.value = raw }

    fun login(url: String, username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            try {
                val baseUrl = url.trim().trimEnd('/')
                if (baseUrl.isEmpty() || username.isBlank() || password.isBlank()) {
                    _loginError.value = "URL, username, and password are required"
                    return@launch
                }
                lastUsername = username
                lastPassword = password
                currentBaseUrl = baseUrl
                api = HermesApi(baseUrl)
                _connectionState.value = ConnectionState.CONNECTING
                LoginHelper(context).login(baseUrl, username, password).fold(
                    onSuccess = { ticket ->
                        currentTicket = ticket
                        _state.value = AppState.Authenticated(baseUrl)
                        _messages.value = emptyList()
                        appendSystem("Logged in to $baseUrl")
                        loadModelsBlocking()
                        connectWebSocket(baseUrl, ticket)
                        refreshSessions()
                        refreshProfiles()
                    },
                    onFailure = { e ->
                        _connectionState.value = ConnectionState.ERROR
                        _loginError.value = e.message ?: "Login failed"
                    },
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                _loginError.value = e.message ?: "Connection error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadModelsBlocking() {
        val a = api ?: return
        _modelsLoading.value = true
        try {
            val options = withContext(Dispatchers.IO) { a.modelOptions().getOrThrow() }
            _models.value = options
            refreshModelInfoBlocking()
            if (_selectedModel.value.isNullOrBlank()) {
                val preferred = options.firstOrNull { it.isCurrentProvider }
                    ?: options.firstOrNull()
                if (preferred != null) {
                    _selectedModel.value = preferred.model
                    _selectedProvider.value = preferred.provider
                }
            }
        } catch (e: Exception) {
            _modelsError.value = e.message
        } finally {
            _modelsLoading.value = false
        }
    }

    private suspend fun refreshModelInfoBlocking() {
        val a = api ?: return
        try {
            val info = withContext(Dispatchers.IO) { a.modelInfo().getOrNull() } ?: return
            if (info.model.isNotBlank()) {
                _selectedModel.value = info.model
                if (info.provider.isNotBlank()) _selectedProvider.value = info.provider
            }
            _contextLimitTokens.value = info.contextLength ?: 0
            recomputeContextUsage()
        } catch (_: Exception) {
        }
    }

    private fun recomputeContextUsage() {
        val chars = _messages.value.sumOf { it.content.length.toLong() }
        val estimate = ((chars / 4L) + 512L).toInt().coerceAtLeast(0)
        val limit = _contextLimitTokens.value
        _contextUsedTokens.value = if (limit > 0) estimate.coerceAtMost(limit) else estimate
    }

    fun refreshModels() {
        viewModelScope.launch {
            _modelsLoading.value = true
            _modelsError.value = null
            try {
                loadModelsBlocking()
            } catch (e: Exception) {
                _modelsError.value = e.message
            } finally {
                _modelsLoading.value = false
            }
        }
    }

    fun refreshSessions() {
        val a = api ?: return
        viewModelScope.launch {
            _sessionsLoading.value = true
            _sessionsError.value = null
            try {
                _sessions.value = withContext(Dispatchers.IO) {
                    a.listSessions(
                        limit = 300,
                        query = _sessionQuery.value.ifBlank { null },
                        profile = "all",
                    ).getOrThrow()
                }
            } catch (e: Exception) {
                _sessionsError.value = e.message
            } finally {
                _sessionsLoading.value = false
            }
        }
    }

    fun refreshProfiles() {
        val a = api ?: return
        viewModelScope.launch {
            _profilesError.value = null
            try {
                val listed = withContext(Dispatchers.IO) { a.listProfiles().getOrThrow() }
                val (active, current) = withContext(Dispatchers.IO) {
                    a.getActiveProfiles().getOrDefault(null to null)
                }
                _activeProfileName.value = active
                _currentProcessProfile.value = current
                _profiles.value = listed.map { (name, path) ->
                    ProfileUi(
                        name = name,
                        isActive = name == active,
                        isCurrent = name == current,
                        path = path,
                    )
                }
            } catch (e: Exception) {
                _profilesError.value = e.message
            }
        }
    }

    fun switchProfile(name: String) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val (active, current) = withContext(Dispatchers.IO) {
                    a.setActiveProfile(name).getOrThrow()
                }
                _activeProfileName.value = active
                _currentProcessProfile.value = current
                refreshProfiles()
                refreshModels()
                refreshSessions()
                appendSystem("Active profile → $active (server process: ${current ?: "?"})")
            } catch (e: Exception) {
                _profilesError.value = e.message
            }
        }
    }

    fun selectModel(option: HermesModelOption) {
            // Optimistic UI update so the strip reflects the pick immediately.
            _selectedModel.value = option.model
            _selectedProvider.value = option.provider
            val a = api
            viewModelScope.launch {
                try {
                    if (a != null) {
                        withContext(Dispatchers.IO) {
                            a.setModel(option.provider, option.model, scope = "main").getOrThrow()
                        }
                    }
                    appendSystem("Model set to ${option.label}")
                    // Prefer server-confirmed model/context after set.
                    refreshModelInfoBlocking()
                    // Keep local selection if info is stale/empty.
                    if (_selectedModel.value.isNullOrBlank()) {
                        _selectedModel.value = option.model
                        _selectedProvider.value = option.provider
                    }
                } catch (e: Exception) {
                    appendSystem("setModel failed: ${e.message}")
                    // Keep optimistic selection for next session.create even if server set fails.
                }
            }
        }

    fun toggleExpanded(id: String) {
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(expanded = !it.expanded) else it }
        }
    }

    fun newChat() {
        _tab.value = AppTab.Chat
        _messages.value = emptyList()
        streamingAssistantId = null
        streamingThinkingId = null
        _runtimeSessionId.value = null
        _storedSessionId.value = null
        _isBusy.value = false
        _pendingApproval.value = false
        recomputeContextUsage()
        appendSystem("New chat")
        // Create a live runtime session tagged source=android so the first
        // prompt.submit can lazily insert a real DB row (Hermes does not
        // persist empty drafts).
        if (_connectionState.value == ConnectionState.OPEN) {
            createSession(forceNew = true) { }
        }
    }

    fun openStoredSession(summary: HermesSessionSummary) {
        _tab.value = AppTab.Chat
        _messages.value = emptyList()
        streamingAssistantId = null
        streamingThinkingId = null
        _isBusy.value = false
        _pendingApproval.value = false
        _runtimeSessionId.value = null
        _storedSessionId.value = summary.id
        if (!summary.model.isNullOrBlank()) {
            _selectedModel.value = summary.model
        }
        val client = wsClient
        if (client != null && client.isOpen) {
            val params = mutableMapOf<String, Any?>(
                "session_id" to summary.id,
                "source" to "android",
                // REST hydrates the transcript; keep the WS payload light.
                "omit_messages" to true,
            )
            summary.profileName?.takeIf { it.isNotBlank() }?.let { params["profile"] = it }
            client.sendRequest("session.resume", params) { result ->
                result.fold(
                    onSuccess = { json ->
                        val sid = json.optString("session_id")
                        if (sid.isNotBlank()) _runtimeSessionId.value = sid
                        val key = json.optString("session_key")
                            .ifBlank { json.optString("resumed") }
                            .ifBlank { summary.id }
                        _storedSessionId.value = key
                        val info = json.optJSONObject("info")
                        val infoModel = info?.optString("model").orEmpty()
                        if (infoModel.isNotBlank()) _selectedModel.value = infoModel
                        val infoProvider = info?.optString("provider").orEmpty()
                        if (infoProvider.isNotBlank()) _selectedProvider.value = infoProvider
                        loadSessionMessages(key, summary.profileName)
                        appendSystem("Resumed $key")
                    },
                    onFailure = { e ->
                        appendSystem("Resume failed: ${e.message} — loading transcript only")
                        loadSessionMessages(summary.id, summary.profileName)
                    },
                )
            }
        } else {
            loadSessionMessages(summary.id, summary.profileName)
        }
    }
    private fun loadSessionMessages(sessionId: String, profileName: String? = null) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val msgs = withContext(Dispatchers.IO) {
                    a.sessionMessages(sessionId, profile = profileName).getOrThrow()
                }
                val out = mutableListOf<ChatMessage>()
                msgs.forEachIndexed { idx, msg ->
                    val role = normalizeRole(msg.role)
                    val baseId = msg.id?.let { "hist-$sessionId-$it" } ?: "hist-$sessionId-$idx"
                    val reasoning = msg.reasoning?.takeIf { it.isNotBlank() }
                    val toolish = msg.role.equals("tool", true) || msg.role.equals("function", true) ||
                        (!msg.toolCallSummary.isNullOrBlank() && msg.content.startsWith("Called "))
                    if (!reasoning.isNullOrBlank()) {
                        out += ChatMessage(
                            id = "$baseId-think",
                            role = "assistant",
                            content = reasoning,
                            kind = MessageKind.Thinking,
                            expanded = false,
                        )
                    }
                    val content = when {
                        msg.content.isNotBlank() -> msg.content
                        !msg.toolCallSummary.isNullOrBlank() -> "Called ${msg.toolCallSummary}"
                        else -> ""
                    }
                    if (content.isBlank() && !toolish) return@forEachIndexed
                    val kind = when {
                        toolish -> MessageKind.Tool
                        role == "system" -> MessageKind.System
                        else -> MessageKind.Text
                    }
                    out += ChatMessage(
                        id = baseId,
                        role = role,
                        content = content,
                        kind = kind,
                        toolName = msg.toolName ?: msg.toolCallSummary,
                        toolStatus = if (kind == MessageKind.Tool) "done" else null,
                    )
                }
                _messages.value = out
                _storedSessionId.value = sessionId
                _chatScrollEpoch.value = _chatScrollEpoch.value + 1
                recomputeContextUsage()
                appendSystem("Loaded ${out.size} messages from $sessionId")
            } catch (e: Exception) {
                appendSystem("Failed to load messages: ${e.message}")
            }
        }
    }

    private fun normalizeRole(role: String) = when (role.lowercase()) {
        "user", "human" -> "user"
        "system" -> "system"
        else -> "assistant"
    }

    fun sendMessage(text: String) {
        val client = wsClient
        if (client == null || !client.isOpen || _connectionState.value != ConnectionState.OPEN) {
            appendSystem("Not connected — tap Retry")
            return
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        appendMessage(ChatMessage(newId("u"), "user", trimmed, kind = MessageKind.Text))
        recomputeContextUsage()
        _isBusy.value = true
        fun submit(sessionId: String) {
            client.sendRequest(
                "prompt.submit",
                mapOf("session_id" to sessionId, "text" to trimmed),
            ) { result ->
                result.onFailure { e ->
                    _isBusy.value = false
                    appendSystem("prompt.submit failed: ${e.message}")
                }.onSuccess { json ->
                    if (json.optString("status") == "error") {
                        _isBusy.value = false
                        setAssistantFinal(json.optString("error", "submit error"))
                    }
                }
            }
        }
        val existing = _runtimeSessionId.value
        if (!existing.isNullOrBlank()) submit(existing) else createSession { submit(it) }
    }

    fun interrupt() {
        val client = wsClient ?: return
        val sid = _runtimeSessionId.value ?: return
        client.sendRequest("session.interrupt", mapOf("session_id" to sid)) {
            _isBusy.value = false
            finalizeThinkingBubble()
            appendSystem("Interrupted")
        }
    }

    fun respondApproval(requestId: String, decision: String) {
        val client = wsClient ?: return
        client.sendRequest(
            "approval.respond",
            mapOf("request_id" to requestId, "decision" to decision),
        ) {
            removeInteractive(requestId)
        }
    }

    fun respondClarify(requestId: String, answer: String) {
        val client = wsClient ?: return
        client.sendRequest(
            "clarify.respond",
            mapOf("request_id" to requestId, "text" to answer),
        ) {
            removeInteractive(requestId)
        }
    }

    private fun removeInteractive(id: String) {
        _messages.update { list ->
            list.map {
                if (it.id == id || it.approvalId == id || it.clarifyId == id) {
                    it.copy(kind = MessageKind.Text, content = "${it.content}\n(Resolved)")
                } else it
            }
        }
        _pendingApproval.value = _messages.value.any {
            it.kind == MessageKind.Approval || it.kind == MessageKind.Clarify
        }
    }

    fun reconnect() {
        val baseUrl = currentBaseUrl
        val user = lastUsername
        val pass = lastPassword
        if (baseUrl == null || user == null || pass == null) {
            _state.value = AppState.Unauthenticated
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            LoginHelper(context).login(baseUrl, user, pass).fold(
                onSuccess = { ticket ->
                    currentTicket = ticket
                    api = HermesApi(baseUrl)
                    connectWebSocket(baseUrl, ticket)
                },
                onFailure = { appendSystem("Reconnect failed: ${it.message}") },
            )
        }
    }

    fun logout() {
        try { wsClient?.disconnect() } catch (_: Exception) {}
        wsClient = null
        api = null
        SharedCookieJar.clear()
        currentBaseUrl = null
        currentTicket = null
        streamingAssistantId = null
        streamingThinkingId = null
        lastUsername = null
        lastPassword = null
        _messages.value = emptyList()
        _systemLogs.value = emptyList()
        _models.value = emptyList()
        _sessions.value = emptyList()
        _runtimeSessionId.value = null
        _storedSessionId.value = null
        _connectionState.value = ConnectionState.IDLE
        _isBusy.value = false
        _pendingApproval.value = false
        _tab.value = AppTab.Chat
        _state.value = AppState.Unauthenticated
    }

    fun refreshCron() {
        val a = api ?: return
        viewModelScope.launch {
            _cronLoading.value = true
            _cronError.value = null
            try {
                val jobs = withContext(Dispatchers.IO) { a.listCronJobs().getOrThrow() }
                _cronJobs.value = jobs.map {
                    CronJobUi(it.id, it.name, it.schedule, it.enabled, it.lastStatus)
                }
            } catch (e: Exception) {
                _cronError.value = e.message
            } finally {
                _cronLoading.value = false
            }
        }
    }

    fun cronAction(id: String, action: String) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (action) {
                        "run", "trigger" -> a.triggerCronJob(id).getOrThrow()
                        "pause" -> a.pauseCronJob(id).getOrThrow()
                        "resume" -> a.resumeCronJob(id).getOrThrow()
                        else -> error("unknown action $action")
                    }
                }
                refreshCron()
            } catch (e: Exception) {
                _cronError.value = e.message
            }
        }
    }

    fun renameSession(s: HermesSessionSummary, title: String) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { a.renameSession(s.id, title).getOrThrow() }
                refreshSessions()
            } catch (e: Exception) {
                appendSystem("Rename failed: ${e.message}")
            }
        }
    }

    fun deleteSession(s: HermesSessionSummary) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { a.deleteSession(s.id).getOrThrow() }
                if (_storedSessionId.value == s.id) newChat()
                refreshSessions()
            } catch (e: Exception) {
                appendSystem("Delete failed: ${e.message}")
            }
        }
    }

    fun exportSession(s: HermesSessionSummary, onBody: (String) -> Unit) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) { a.exportSession(s.id).getOrThrow() }
                onBody(body)
            } catch (e: Exception) {
                onBody("Export failed: ${e.message}")
            }
        }
    }

    fun loadConfig() {
        val a = api ?: return
        viewModelScope.launch {
            _configError.value = null
            try {
                val raw = withContext(Dispatchers.IO) { a.getConfigRaw().getOrThrow() }
                val yaml = try {
                    JSONObject(raw).optString("yaml", raw)
                } catch (_: Exception) {
                    raw
                }
                _configRaw.value = yaml
            } catch (e: Exception) {
                _configError.value = e.message
            }
        }
    }

    fun saveConfig() {
        val a = api ?: return
        viewModelScope.launch {
            _configSaving.value = true
            _configError.value = null
            try {
                withContext(Dispatchers.IO) { a.saveConfigRaw(_configRaw.value).getOrThrow() }
                appendSystem("Config saved")
            } catch (e: Exception) {
                _configError.value = e.message
            } finally {
                _configSaving.value = false
            }
        }
    }

    fun refreshGateway() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val st = withContext(Dispatchers.IO) { a.status().getOrThrow() }
                val running = st.optBoolean("gateway_running", false)
                val state = st.optString("gateway_state", if (running) "running" else "stopped")
                _gatewayStatus.value = GatewayStatusUi(
                    ok = running || state == "running",
                    summary = "gateway $state · agents ${st.optInt("active_agents")} · sessions ${st.optInt("active_sessions")}",
                    details = st.toString(2),
                )
            } catch (e: Exception) {
                _gatewayStatus.value = GatewayStatusUi(false, e.message ?: "status failed", "")
            }
        }
    }

    fun refreshPairing() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { a.listPairing().getOrThrow() }
                _pairingText.value = if (pending.isEmpty()) {
                    "No pending pairings."
                } else {
                    pending.joinToString("\n\n") { it.toString(2) }
                }
            } catch (e: Exception) {
                _pairingText.value = "Pairing error: ${e.message}"
            }
        }
    }

    fun refreshLogs() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val lines = withContext(Dispatchers.IO) { a.getLogs(limit = 80).getOrThrow() }
                _logsText.value = lines.joinToString("\n")
            } catch (e: Exception) {
                _logsText.value = "Logs error: ${e.message}"
            }
        }
    }

    fun runDoctor() {
        val a = api ?: return
        viewModelScope.launch {
            _manageBusy.value = true
            try {
                _opsOutput.value = withContext(Dispatchers.IO) { a.runDoctor().getOrThrow() }
            } catch (e: Exception) {
                _opsOutput.value = "Doctor failed: ${e.message}"
            } finally {
                _manageBusy.value = false
            }
        }
    }

    fun runBackup() {
        val a = api ?: return
        viewModelScope.launch {
            _manageBusy.value = true
            try {
                _opsOutput.value = withContext(Dispatchers.IO) { a.runBackup().getOrThrow() }
            } catch (e: Exception) {
                _opsOutput.value = "Backup failed: ${e.message}"
            } finally {
                _manageBusy.value = false
            }
        }
    }

    fun restartGateway() {
        val a = api ?: return
        viewModelScope.launch {
            _manageBusy.value = true
            try {
                withContext(Dispatchers.IO) { a.restartGateway().getOrThrow() }
                _opsOutput.value = "Gateway restart requested."
            } catch (e: Exception) {
                _opsOutput.value = "Restart failed: ${e.message}"
            } finally {
                _manageBusy.value = false
            }
        }
    }

    private fun connectWebSocket(baseUrl: String, ticket: String) {
        try { wsClient?.disconnect() } catch (_: Exception) {}
        _connectionState.value = ConnectionState.CONNECTING
        val client = GatewayWsClient(baseUrl = baseUrl, token = null, ticket = ticket)
        wsClient = client
        client.onOpen = {
            _connectionState.value = ConnectionState.OPEN
            appendSystem("WebSocket open")
        }
        client.onReady = { _ ->
            _connectionState.value = ConnectionState.OPEN
            appendSystem("Gateway ready")
            ensureSession(forceNew = _runtimeSessionId.value == null)
        }
        client.onMessageDelta = { sessionId, text, _ ->
                    if (!sessionId.isNullOrBlank()) _runtimeSessionId.value = sessionId
                    // Answer tokens only (thinking.delta is not routed here).
                    if (text.isNotEmpty() && !isThinkingStatus(text)) appendAssistantDelta(text)
                }
                client.onEvent = { type, sessionId, payload ->
                    if (!sessionId.isNullOrBlank()) _runtimeSessionId.value = sessionId
                    when (type) {
                        "status.update" -> {
                            val t = payload.optString("text", "")
                            // Don't clobber an accumulating thinking stream with status labels.
                            if (t.isNotBlank() && streamingThinkingId == null) {
                                setAssistantPlaceholder(t)
                            }
                        }
                        "message.start" -> {
                                                    // Real answer is starting — collapse thinking once.
                                                    finalizeThinkingBubble()
                                                    ensureAssistantBubble()
                                                }
                                                "message.interim" -> {
                                                    val t = payload.optString("text", "")
                                                    if (t.isNotBlank() && !isThinkingStatus(t)) setAssistantFinal(t)
                                                }
                        "message.complete" -> {
                            val text = payload.optString("text", "").ifBlank { payload.optString("error", "") }
                            when {
                                payload.has("error") || payload.optString("status") == "error" ->
                                    setAssistantFinal("Error: ${payload.optString("error", text).ifBlank { "Turn failed" }}")
                                text.isNotBlank() -> setAssistantFinal(text)
                                else -> {
                                    val cur = currentAssistantText()
                                    if (cur.isNullOrBlank() || isThinkingStatus(cur)) setAssistantFinal("(empty response)")
                                    else setAssistantFinal(cur)
                                }
                            }
                            finalizeThinkingBubble()
                            streamingAssistantId = null
                            _isBusy.value = false
                            recomputeContextUsage()
                            refreshSessions()
                        }
                        "tool.start", "tool.update", "tool.end", "tool.progress", "tool.complete" ->
                            handleToolEvent(type, payload)
                        "thinking.delta", "reasoning.delta" -> {
                            val t = payload.optString("delta", "")
                                .ifBlank { payload.optString("text", "") }
                            if (t.isNotEmpty()) appendThinkingDelta(t)
                        }
                        "session.info" -> {
                            val model = payload.optString("model")
                            if (model.isNotBlank()) _selectedModel.value = model
                            val provider = payload.optString("provider")
                            if (provider.isNotBlank()) _selectedProvider.value = provider
                            val stored = payload.optString("session_key").ifBlank {
                                payload.optString("stored_session_id")
                            }
                            if (stored.isNotBlank()) _storedSessionId.value = stored
                        }
                        "approval.request" -> handleApproval(payload)
                        "clarify.request" -> handleClarify(payload)
                        "error", "agent.error", "turn.error" -> {
                            appendSystem("Error: ${payload.optString("message", type)}")
                            finalizeThinkingBubble()
                            _isBusy.value = false
                        }
                    }
                }
        client.onError = {
            _connectionState.value = ConnectionState.ERROR
            appendSystem(it ?: "WebSocket error")
        }
        client.onClosed = {
            _connectionState.value = ConnectionState.CLOSED
            appendSystem("WebSocket closed")
        }
        try {
            client.connect()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            Log.e(TAG, "connectWebSocket failed", e)
        }
    }

    private fun handleToolEvent(type: String, payload: JSONObject) {
        val name = payload.optString("name").ifBlank { payload.optString("tool", "tool") }
        val status = when (type) {
            "tool.start" -> "running"
            "tool.end", "tool.complete" ->
                if (payload.optBoolean("ok", true) && !payload.has("error")) "done" else "error"
            else -> payload.optString("status", "running")
        }
        val content = payload.optString("output").ifBlank {
            payload.optString("result").ifBlank { payload.optString("message", status) }
        }
        val id = payload.optString("id").ifBlank { "tool-$name" }
        _messages.update { list ->
            val existing = list.indexOfFirst {
                it.id == id || (it.kind == MessageKind.Tool && it.toolName == name && it.toolStatus == "running")
            }
            if (existing >= 0) {
                list.toMutableList().also {
                    it[existing] = it[existing].copy(
                        content = content.ifBlank { it[existing].content },
                        toolStatus = status,
                        kind = MessageKind.Tool,
                        toolName = name,
                    )
                }
            } else {
                list + ChatMessage(
                    id = id,
                    role = "assistant",
                    content = content,
                    kind = MessageKind.Tool,
                    toolName = name,
                    toolStatus = status,
                    expanded = false,
                )
            }
        }
    }

    private fun handleApproval(payload: JSONObject) {
        val id = payload.optString("request_id").ifBlank { payload.optString("id", newId("appr")) }
        val opts = mutableListOf<String>()
        val arr = payload.optJSONArray("options")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optString(i)
                if (o.isNotBlank()) opts += o
            }
        }
        appendMessage(
            ChatMessage(
                id = id,
                role = "assistant",
                content = payload.optString("detail", payload.optString("title", "Approval required")),
                kind = MessageKind.Approval,
                approvalId = id,
                approvalOptions = opts.ifEmpty { listOf("allow_once", "allow_always", "deny") },
            ),
        )
        _pendingApproval.value = true
    }

    private fun handleClarify(payload: JSONObject) {
        val id = payload.optString("request_id").ifBlank { payload.optString("id", newId("clar")) }
        val choices = mutableListOf<String>()
        val arr = payload.optJSONArray("choices")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val c = arr.optString(i)
                if (c.isNotBlank()) choices += c
            }
        }
        appendMessage(
            ChatMessage(
                id = id,
                role = "assistant",
                content = payload.optString("question", payload.optString("text", "Clarification needed")),
                kind = MessageKind.Clarify,
                clarifyId = id,
                clarifyChoices = choices,
            ),
        )
        _pendingApproval.value = true
    }

    private fun ensureSession(forceNew: Boolean = false) {
        if (!forceNew && !_runtimeSessionId.value.isNullOrBlank()) return
        createSession(forceNew = forceNew) { }
    }

    private fun createSession(forceNew: Boolean = false, onReady: (String) -> Unit) {
        val client = wsClient
        if (client == null || !client.isOpen) {
            appendSystem("Cannot create session — not connected")
            return
        }
        if (!forceNew && !_runtimeSessionId.value.isNullOrBlank()) {
            onReady(_runtimeSessionId.value!!)
            return
        }
        val model = _selectedModel.value?.takeIf { it.isNotBlank() }
        val provider = _selectedProvider.value?.takeIf { it.isNotBlank() }
        // source=android is what desktop/CLI use to group this client.
        val params = mutableMapOf<String, Any?>(
            "source" to "android",
            "title" to "Android chat",
        )
        if (model != null) params["model"] = model
        if (provider != null) params["provider"] = provider
        _activeProfileName.value?.takeIf { it.isNotBlank() }?.let { params["profile"] = it }

        client.sendRequest("session.create", params) { result ->
            result.fold(
                onSuccess = { json ->
                    // Runtime id (short) is required for prompt.submit.
                    val sid = json.optString("session_id").ifBlank { json.optString("id") }
                    // Stored key is the durable DB id used by list/CLI/desktop.
                    val stored = json.optString("stored_session_id")
                        .ifBlank { json.optString("session_key") }
                    if (sid.isBlank()) {
                        appendSystem("session.create returned no id: ${json.toString().take(200)}")
                        return@fold
                    }
                    _runtimeSessionId.value = sid
                    if (stored.isNotBlank()) {
                        _storedSessionId.value = stored
                    }
                    val info = json.optJSONObject("info")
                    val infoModel = info?.optString("model").orEmpty()
                    if (infoModel.isNotBlank()) _selectedModel.value = infoModel
                    val infoProvider = info?.optString("provider").orEmpty()
                    if (infoProvider.isNotBlank()) _selectedProvider.value = infoProvider
                    appendSystem(
                        buildString {
                            append("Session $sid")
                            if (stored.isNotBlank()) append(" · db $stored")
                            append(" · source android")
                        },
                    )
                    onReady(sid)
                },
                onFailure = { e ->
                    appendSystem("session.create failed: ${e.message}")
                    _isBusy.value = false
                },
            )
        }
    }

    private fun isThinkingStatus(text: String): Boolean {
        val t = text.trim().lowercase()
        return t == "thinking" || t.startsWith("thinking") || t.startsWith("processing") ||
            t.startsWith("reasoning")
    }

    private fun ensureAssistantBubble() {
        val id = streamingAssistantId
        if (id != null && _messages.value.any { it.id == id }) return
        val newId = newId("a")
        streamingAssistantId = newId
        appendMessage(ChatMessage(newId, "assistant", "", isStreaming = true))
    }

    private fun appendAssistantDelta(chunk: String) {
        if (chunk.isEmpty()) return
        // First real answer token ends the thinking phase (once).
        finalizeThinkingBubble()
        ensureAssistantBubble()
        val id = streamingAssistantId ?: return
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == id) {
                    msg.copy(content = msg.content + chunk, kind = MessageKind.Text, isStreaming = true)
                } else msg
            }
        }
        recomputeContextUsage()
    }

    /** Accumulate thinking tokens into a single MessageKind.Thinking bubble. */
    private fun appendThinkingDelta(chunk: String) {
        if (chunk.isEmpty()) return
        val id = streamingThinkingId ?: newId("think").also { streamingThinkingId = it }
        _messages.update { list ->
            val existing = list.indexOfFirst { it.id == id }
            if (existing >= 0) {
                val old = list[existing]
                // Drop pure status placeholders when real tokens arrive.
                val base = if (isThinkingStatus(old.content)) "" else old.content
                list.toMutableList().also {
                    it[existing] = old.copy(
                        content = base + chunk,
                        kind = MessageKind.Thinking,
                        expanded = true,
                        isStreaming = true,
                    )
                }
            } else {
                list + ChatMessage(
                    id = id,
                    role = "assistant",
                    content = chunk,
                    kind = MessageKind.Thinking,
                    expanded = true,
                    isStreaming = true,
                )
            }
        }
    }

    /** Status label only (e.g. "thinking…") — replace, do not create per-token bubbles. */
    private fun setAssistantPlaceholder(text: String) {
        val id = streamingThinkingId ?: newId("think").also { streamingThinkingId = it }
        _messages.update { list ->
            val existing = list.indexOfFirst { it.id == id }
            val next = ChatMessage(
                id = id,
                role = "assistant",
                content = text.ifBlank { "Thinking…" },
                kind = MessageKind.Thinking,
                expanded = true,
                isStreaming = true,
            )
            if (existing >= 0) {
                // Don't wipe accumulated reasoning with a short status label.
                val old = list[existing]
                if (old.content.isNotBlank() && !isThinkingStatus(old.content) && isThinkingStatus(text)) {
                    list
                } else {
                    list.toMutableList().also { it[existing] = next }
                }
            } else {
                list + next
            }
        }
    }

    private fun finalizeThinkingBubble() {
        val id = streamingThinkingId ?: return
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(isStreaming = false, expanded = false) else it }
        }
        streamingThinkingId = null
    }
    private fun setAssistantFinal(text: String) {
        finalizeThinkingBubble()
        val id = streamingAssistantId
        if (id == null) {
            appendMessage(ChatMessage(newId("a"), "assistant", text, kind = MessageKind.Text))
            return
        }
        _messages.update { list ->
            list.map {
                if (it.id == id) it.copy(content = text, isStreaming = false, kind = MessageKind.Text) else it
            }
        }
        recomputeContextUsage()
    }

    private fun currentAssistantText(): String? {
        val id = streamingAssistantId ?: return _messages.value.lastOrNull { it.role == "assistant" }?.content
        return _messages.value.firstOrNull { it.id == id }?.content
    }

    private fun appendMessage(msg: ChatMessage) { _messages.update { it + msg } }
    private fun appendSystem(text: String) { _systemLogs.update { (it + text).takeLast(80) } }

    override fun onCleared() {
        super.onCleared()
        try { wsClient?.disconnect() } catch (_: Exception) {}
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                return AppViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }

    companion object { private const val TAG = "AppViewModel" }
}
