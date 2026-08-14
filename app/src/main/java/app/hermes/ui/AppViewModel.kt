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
    /** Empty allowlist means "show every model in the chat picker". */
    private val _pickerAllowlist = MutableStateFlow<Set<String>>(emptySet())
    val pickerAllowlist: StateFlow<Set<String>> = _pickerAllowlist.asStateFlow()
    private val _pickerModels = MutableStateFlow<List<HermesModelOption>>(emptyList())
    val pickerModels: StateFlow<List<HermesModelOption>> = _pickerModels.asStateFlow()
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
    /** Stored session ids with a live turn still running (including background). */
    private val _runningSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val runningSessionIds: StateFlow<Set<String>> = _runningSessionIds.asStateFlow()
    private val runtimeToStored = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _pendingApproval = MutableStateFlow(false)
    val pendingApproval: StateFlow<Boolean> = _pendingApproval.asStateFlow()
    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()
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
        viewModelScope.launch {
            _pickerAllowlist.value = ModelPickerPrefs.load(context)
            recomputePickerModels()
        }
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
            recomputePickerModels()
            refreshModelInfoBlocking(overwriteSelection = _selectedModel.value.isNullOrBlank())
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

    private var userPickedModel = false

    private suspend fun refreshModelInfoBlocking(overwriteSelection: Boolean = false) {
        val a = api ?: return
        try {
            val info = withContext(Dispatchers.IO) { a.modelInfo().getOrNull() } ?: return
            if (overwriteSelection && !userPickedModel && info.model.isNotBlank()) {
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

    fun isInChatPicker(option: HermesModelOption): Boolean {
        val allow = _pickerAllowlist.value
        return allow.isEmpty() || option.pickerKey in allow
    }

    fun toggleChatPickerModel(option: HermesModelOption) {
        val all = _models.value
        if (all.isEmpty()) return
        val key = option.pickerKey
        val current = _pickerAllowlist.value
        val next = if (current.isEmpty()) {
            // First restriction: start from "all minus this one"
            all.map { it.pickerKey }.toSet() - key
        } else if (key in current) {
            (current - key).ifEmpty { current } // never empty the last one
        } else {
            current + key
        }
        persistPickerAllowlist(next)
    }

    fun setProviderInChatPicker(provider: String, visible: Boolean) {
        val all = _models.value
        if (all.isEmpty()) return
        val providerKeys = all.filter { it.provider == provider }.map { it.pickerKey }.toSet()
        val current = _pickerAllowlist.value
        val base = if (current.isEmpty()) all.map { it.pickerKey }.toSet() else current
        val next = if (visible) {
            base + providerKeys
        } else {
            (base - providerKeys).ifEmpty { base }
        }
        persistPickerAllowlist(if (next.size == all.size) emptySet() else next)
    }

    fun resetChatPickerToAll() {
        persistPickerAllowlist(emptySet())
    }

    private fun persistPickerAllowlist(keys: Set<String>) {
        val allKeys = _models.value.map { it.pickerKey }.toSet()
        val normalized = if (keys.isEmpty() || (allKeys.isNotEmpty() && keys.containsAll(allKeys))) {
            emptySet()
        } else {
            keys
        }
        _pickerAllowlist.value = normalized
        recomputePickerModels()
        viewModelScope.launch {
            ModelPickerPrefs.save(context, normalized)
        }
    }

    private fun recomputePickerModels() {
        val allow = _pickerAllowlist.value
        val all = _models.value
        _pickerModels.value = if (allow.isEmpty()) all else all.filter { it.pickerKey in allow }
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
        userPickedModel = true
        _selectedModel.value = option.model
        _selectedProvider.value = option.provider
        appendSystem("Model set to ${option.label}")
        val a = api
        val client = wsClient
        val sid = _runtimeSessionId.value
        viewModelScope.launch {
            try {
                if (a != null) {
                    withContext(Dispatchers.IO) {
                        a.setModel(option.provider, option.model, scope = "main")
                    }
                    refreshModelInfoBlocking(overwriteSelection = false)
                }
            } catch (e: Exception) {
                appendSystem("setModel (profile default) failed: ${e.message}")
            }
            // Live session keeps its own model unless we switch it here.
            // Correct /model syntax: "<model> --provider <provider>" — the
            // "provider/model" slash form is parsed as an aggregator slug and
            // the switch fails silently (verified live).
            if (client != null && client.isOpen && !sid.isNullOrBlank()) {
                val arg = if (option.provider.isNotBlank()) {
                    "${option.model} --provider ${option.provider}"
                } else {
                    option.model
                }
                client.sendRequest(
                    "slash.exec",
                    mapOf(
                        "session_id" to sid,
                        "command" to "/model $arg",
                    ),
                ) { result ->
                    result.fold(
                        onSuccess = { json ->
                            val out = json.optString("output", "")
                            if (out.contains("Model switched", ignoreCase = true) ||
                                out.contains("switched", ignoreCase = true)
                            ) {
                                appendSystem("Session model → ${option.label}")
                            } else {
                                appendSystem("Live /model: ${out.lineSequence().firstOrNull()?.trim()?.take(160)}")
                            }
                        },
                        onFailure = { e ->
                            appendSystem("Live /model failed: ${e.message} — new chats will use this pick")
                        },
                    )
                }
            }
        }
    }

    fun toggleExpanded(id: String) {
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(expanded = !it.expanded) else it }
        }
    }

    private fun rememberRuntime(runtimeId: String?, storedId: String?) {
        val r = runtimeId?.takeIf { it.isNotBlank() } ?: return
        val s = storedId?.takeIf { it.isNotBlank() } ?: return
        runtimeToStored[r] = s
    }

    private fun markSessionRunning(storedId: String?, running: Boolean) {
        val id = storedId?.takeIf { it.isNotBlank() } ?: return
        _runningSessionIds.update { cur -> if (running) cur + id else cur - id }
    }

    private fun noteSessionFinished(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        val stored = when {
            _storedSessionId.value == sessionId -> sessionId
            _runtimeSessionId.value == sessionId -> _storedSessionId.value
            else -> runtimeToStored[sessionId]
        }
        markSessionRunning(stored, false)
        if (belongsToActiveChat(sessionId)) {
            _isBusy.value = false
        }
    }

    private fun applyLiveTurnState(json: org.json.JSONObject, storedId: String?) {
        val running = json.optBoolean("running", false) ||
            json.optString("status").equals("streaming", ignoreCase = true)
        _isBusy.value = running
        markSessionRunning(storedId, running)
    }

    private var chatEpoch = 0

    private fun belongsToActiveChat(sessionId: String?): Boolean {
        val active = _runtimeSessionId.value
        if (active.isNullOrBlank() || sessionId.isNullOrBlank()) return false
        if (sessionId == active) return true
        val stored = _storedSessionId.value
        return !stored.isNullOrBlank() && sessionId == stored
    }

    /** Leave the current chat without killing a live turn. */
    private fun detachCurrentSession() {
        val stored = _storedSessionId.value
        if (_isBusy.value) markSessionRunning(stored, true)
        chatEpoch += 1
        streamingAssistantId = null
        streamingThinkingId = null
        _runtimeSessionId.value = null
        _isBusy.value = false
        _pendingApproval.value = false
    }

    fun newChat() {
        detachCurrentSession()
        val epoch = chatEpoch
        _tab.value = AppTab.Chat
        _messages.value = emptyList()
        _storedSessionId.value = null
        _pendingAttachments.value = emptyList()
        recomputeContextUsage()
        appendSystem("New chat")
        // Create a live runtime session tagged source=android so the first
        // prompt.submit can lazily insert a real DB row (Hermes does not
        // persist empty drafts).
        if (_connectionState.value == ConnectionState.OPEN) {
            createSession(forceNew = true) {
                if (epoch != chatEpoch) return@createSession
            }
        }
    }

    fun openStoredSession(summary: HermesSessionSummary) {
        detachCurrentSession()
        val epoch = chatEpoch
        _tab.value = AppTab.Chat
        _messages.value = emptyList()
        _pendingAttachments.value = emptyList()
        _storedSessionId.value = summary.id
        if (!summary.model.isNullOrBlank()) {
            userPickedModel = false
            _selectedModel.value = summary.model
        }
        val client = wsClient
        if (client != null && client.isOpen) {
            val params = mutableMapOf<String, Any?>(
                "session_id" to summary.id,
                "source" to "android",
                "omit_messages" to true,
            )
            summary.profileName?.takeIf { it.isNotBlank() }?.let { params["profile"] = it }
            client.sendRequest("session.resume", params) { result ->
                if (epoch != chatEpoch) return@sendRequest
                result.fold(
                    onSuccess = { json ->
                        if (epoch != chatEpoch) return@fold
                        val sid = json.optString("session_id")
                        if (sid.isNotBlank()) _runtimeSessionId.value = sid
                        val key = json.optString("session_key")
                            .ifBlank { json.optString("resumed") }
                            .ifBlank { summary.id }
                        _storedSessionId.value = key
                        rememberRuntime(sid, key)
                        applyLiveTurnState(json, key)
                        val info = json.optJSONObject("info")
                        val infoModel = info?.optString("model").orEmpty()
                        if (infoModel.isNotBlank()) _selectedModel.value = infoModel
                        val infoProvider = info?.optString("provider").orEmpty()
                        if (infoProvider.isNotBlank()) _selectedProvider.value = infoProvider
                        loadSessionMessages(key, summary.profileName, epoch)
                        appendSystem("Resumed $key")
                    },
                    onFailure = { e ->
                        if (epoch != chatEpoch) return@fold
                        appendSystem("Resume failed: ${e.message} — loading transcript only")
                        loadSessionMessages(summary.id, summary.profileName, epoch)
                    },
                )
            }
        } else {
            loadSessionMessages(summary.id, summary.profileName, epoch)
        }
    }

    private fun loadSessionMessages(sessionId: String, profileName: String? = null, epoch: Int = chatEpoch) {
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
                if (epoch != chatEpoch) return@launch
                _messages.value = out
                if (_storedSessionId.value.isNullOrBlank()) {
                    _storedSessionId.value = sessionId
                }
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
        val attachments = _pendingAttachments.value
        if (trimmed.isEmpty() && attachments.isEmpty()) return

        val display = buildString {
            if (trimmed.isNotEmpty()) append(trimmed)
            attachments.forEach { a ->
                if (isNotEmpty()) append('\n')
                append(
                    when (a.kind) {
                        AttachmentKind.IMAGE -> "[image: ${a.name}]"
                        AttachmentKind.PDF -> "[pdf: ${a.name}]"
                        AttachmentKind.FILE -> "[file: ${a.name}]"
                    },
                )
            }
        }
        appendMessage(ChatMessage(newId("u"), "user", display, kind = MessageKind.Text))
        recomputeContextUsage()
        _isBusy.value = true
        markSessionRunning(_storedSessionId.value, true)

        fun submit(sessionId: String) {
            attachThenSubmit(client, sessionId, trimmed, attachments)
        }
        val existing = _runtimeSessionId.value
        if (!existing.isNullOrBlank()) submit(existing) else createSession { submit(it) }
    }

    fun addAttachmentFromUri(uriString: String) {
        viewModelScope.launch {
            try {
                val uri = android.net.Uri.parse(uriString)
                val item = withContext(Dispatchers.IO) { readAttachment(uri) }
                if (_pendingAttachments.value.size >= 8) {
                    appendSystem("Max 8 attachments per turn")
                    return@launch
                }
                _pendingAttachments.update { it + item }
            } catch (e: Exception) {
                appendSystem("Attach failed: ${e.message}")
            }
        }
    }

    fun removeAttachment(id: String) {
        _pendingAttachments.update { list -> list.filterNot { it.id == id } }
    }

    private fun readAttachment(uri: android.net.Uri): PendingAttachment {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx) ?: name
            }
        }
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read $name")
        if (bytes.isEmpty()) error("$name is empty")
        if (bytes.size > MAX_ATTACH_BYTES) {
            val mb = MAX_ATTACH_BYTES / (1024 * 1024)
            error("$name is too large (${bytes.size / (1024 * 1024)} MB; cap ${mb} MB)")
        }
        val kind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime == "application/pdf" || name.endsWith(".pdf", true) -> AttachmentKind.PDF
            else -> AttachmentKind.FILE
        }
        return PendingAttachment(
            id = newId("att"),
            name = name,
            mimeType = mime,
            kind = kind,
            sizeBytes = bytes.size,
            base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP),
        )
    }

    private fun attachThenSubmit(
        client: GatewayWsClient,
        sessionId: String,
        text: String,
        attachments: List<PendingAttachment>,
    ) {
        fun submitPrompt() {
            val prompt = text.ifBlank {
                when {
                    attachments.any { it.kind == AttachmentKind.IMAGE } -> "Please look at the attached image(s)."
                    attachments.any { it.kind == AttachmentKind.PDF } -> "Please read the attached PDF."
                    attachments.isNotEmpty() -> "Please review the attached file(s)."
                    else -> ""
                }
            }
            client.sendRequest(
                "prompt.submit",
                mapOf("session_id" to sessionId, "text" to prompt),
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

        fun next(index: Int) {
            if (index >= attachments.size) {
                _pendingAttachments.value = emptyList()
                submitPrompt()
                return
            }
            val a = attachments[index]
            sendAttachRpc(client, sessionId, a) { ok ->
                if (ok) next(index + 1)
                else {
                    _isBusy.value = false
                    appendSystem("Stopped before send — attachment failed: ${a.name}")
                }
            }
        }
        next(0)
    }

    private fun sendAttachRpc(
        client: GatewayWsClient,
        sessionId: String,
        a: PendingAttachment,
        onDone: (Boolean) -> Unit,
    ) {
        when (a.kind) {
            AttachmentKind.IMAGE -> {
                client.sendRequest(
                    "image.attach_bytes",
                    mapOf(
                        "session_id" to sessionId,
                        "content_base64" to a.base64,
                        "filename" to a.name,
                    ),
                ) { result ->
                    result.fold(
                        onSuccess = {
                            appendSystem("Attached image ${a.name}")
                            onDone(true)
                        },
                        onFailure = { e ->
                            appendSystem("image.attach_bytes failed (${a.name}): ${e.message}")
                            onDone(false)
                        },
                    )
                }
            }
            AttachmentKind.PDF -> {
                client.sendRequest(
                    "pdf.attach",
                    mapOf(
                        "session_id" to sessionId,
                        "content_base64" to a.base64,
                        "filename" to a.name,
                    ),
                ) { result ->
                    result.fold(
                        onSuccess = {
                            appendSystem("Attached PDF ${a.name}")
                            onDone(true)
                        },
                        onFailure = {
                            // Fallback if poppler isn't on the host.
                            sendFileAttach(client, sessionId, a, onDone)
                        },
                    )
                }
            }
            AttachmentKind.FILE -> sendFileAttach(client, sessionId, a, onDone)
        }
    }

    private fun sendFileAttach(
        client: GatewayWsClient,
        sessionId: String,
        a: PendingAttachment,
        onDone: (Boolean) -> Unit,
    ) {
        client.sendRequest(
            "file.attach",
            mapOf(
                "session_id" to sessionId,
                "name" to a.name,
                "path" to a.name,
                "data_url" to "data:${a.mimeType};base64,${a.base64}",
            ),
        ) { result ->
            result.fold(
                onSuccess = { json ->
                    val ref = json.optString("ref_text")
                    appendSystem("Attached file ${a.name}" + if (ref.isNotBlank()) " ($ref)" else "")
                    onDone(true)
                },
                onFailure = { e ->
                    appendSystem("file.attach failed (${a.name}): ${e.message}")
                    onDone(false)
                },
            )
        }
    }

    fun interrupt() {
        val client = wsClient ?: return
        val sid = _runtimeSessionId.value ?: return
        client.sendRequest("session.interrupt", mapOf("session_id" to sid)) {
            _isBusy.value = false
            markSessionRunning(_storedSessionId.value, false)
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
        reconnectInternal(reason = "manual")
    }

    /** Called when the activity returns to the foreground (e.g. after sleep). */
    fun onAppForeground() {
        if (_state.value !is AppState.Authenticated) return
        val open = _connectionState.value == ConnectionState.OPEN && wsClient?.isOpen == true
        if (open) return
        reconnectInternal(reason = "app resumed")
    }

    private var reconnectGeneration = 0

    private fun reconnectInternal(reason: String) {
        val baseUrl = currentBaseUrl
        val user = lastUsername
        val pass = lastPassword
        if (baseUrl == null || user == null || pass == null) {
            _state.value = AppState.Unauthenticated
            return
        }
        val gen = ++reconnectGeneration
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            appendSystem("Reconnecting ($reason)…")
            // Stale runtime ids die with the old socket; keep stored session to resume.
            _runtimeSessionId.value = null
            _isBusy.value = false
            val result = withContext(Dispatchers.IO) {
                LoginHelper(context).login(baseUrl, user, pass)
            }
            if (gen != reconnectGeneration) return@launch
            result.fold(
                onSuccess = { ticket ->
                    currentTicket = ticket
                    api = HermesApi(baseUrl)
                    connectWebSocket(baseUrl, ticket)
                },
                onFailure = {
                    if (gen != reconnectGeneration) return@fold
                    _connectionState.value = ConnectionState.ERROR
                    appendSystem("Reconnect failed: ${it.message}")
                },
            )
            // Timeout: if still connecting after 20s, surface an error.
            kotlinx.coroutines.delay(20_000)
            if (gen == reconnectGeneration &&
                _connectionState.value == ConnectionState.CONNECTING
            ) {
                _connectionState.value = ConnectionState.ERROR
                appendSystem("Reconnect timed out")
            }
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
        _runningSessionIds.value = emptySet()
        runtimeToStored.clear()
        _pendingApproval.value = false
        _pendingAttachments.value = emptyList()
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
        client.shouldRetryReconnect = {
            _state.value is AppState.Authenticated
        }
        client.onOpen = {
            _connectionState.value = ConnectionState.OPEN
            appendSystem("WebSocket open")
        }
        client.onReady = { _ ->
            _connectionState.value = ConnectionState.OPEN
            appendSystem("Gateway ready")
            val epoch = chatEpoch
            val stored = _storedSessionId.value
            if (!stored.isNullOrBlank()) {
                client.sendRequest(
                    "session.resume",
                    mapOf(
                        "session_id" to stored,
                        "source" to "android",
                        "omit_messages" to true,
                    ),
                ) { result ->
                    if (epoch != chatEpoch) return@sendRequest
                    result.fold(
                        onSuccess = { json ->
                            if (epoch != chatEpoch) return@fold
                            val sid = json.optString("session_id")
                            if (sid.isNotBlank()) _runtimeSessionId.value = sid
                            val key = json.optString("session_key")
                                .ifBlank { json.optString("resumed") }
                                .ifBlank { stored }
                            _storedSessionId.value = key
                            rememberRuntime(sid, key)
                            applyLiveTurnState(json, key)
                            appendSystem("Reattached $key")
                        },
                        onFailure = { e ->
                            if (epoch != chatEpoch) return@fold
                            appendSystem("Resume after reconnect failed: ${e.message}")
                            ensureSession(forceNew = true)
                        },
                    )
                }
            } else if (_runtimeSessionId.value.isNullOrBlank()) {
                ensureSession(forceNew = true)
            }
        }
        client.onMessageDelta = { sessionId, text, _ ->
            if (belongsToActiveChat(sessionId) && text.isNotEmpty() && !isThinkingStatus(text)) {
                appendAssistantDelta(text)
            }
        }
        client.onEvent = { type, sessionId, payload ->
            if (type == "message.complete" || type == "error" ||
                type == "agent.error" || type == "turn.error"
            ) {
                noteSessionFinished(sessionId)
            }
            if (type == "gateway.ready" || belongsToActiveChat(sessionId)) {
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
                            val provider = payload.optString("provider")
                            if (!userPickedModel) {
                                if (model.isNotBlank()) _selectedModel.value = model
                                if (provider.isNotBlank()) _selectedProvider.value = provider
                            }
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
        val epoch = chatEpoch
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
                    if (epoch != chatEpoch) return@fold
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
                    rememberRuntime(sid, stored.ifBlank { null })
                    val info = json.optJSONObject("info")
                    val infoModel = info?.optString("model").orEmpty()
                    val infoProvider = info?.optString("provider").orEmpty()
                    if (userPickedModel) {
                        // Honor the picker; session.create may echo the profile default.
                        if (infoModel.isNotBlank()) {
                            appendSystem("Created with ${infoModel}; keeping picker ${model}")
                        }
                    } else {
                        if (infoModel.isNotBlank()) _selectedModel.value = infoModel
                        if (infoProvider.isNotBlank()) _selectedProvider.value = infoProvider
                    }
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
                if (epoch != chatEpoch) return@fold
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

    companion object {
        private const val TAG = "AppViewModel"
        private const val MAX_ATTACH_BYTES = 18 * 1024 * 1024
    }
}
