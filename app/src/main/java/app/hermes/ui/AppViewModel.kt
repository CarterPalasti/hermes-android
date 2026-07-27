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

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _sessionsLoading = MutableStateFlow(false)
    val sessionsLoading: StateFlow<Boolean> = _sessionsLoading.asStateFlow()

    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

    private val _sessionsError = MutableStateFlow<String?>(null)
    val sessionsError: StateFlow<String?> = _sessionsError.asStateFlow()

    private val _runtimeSessionId = MutableStateFlow<String?>(null)
    val runtimeSessionId: StateFlow<String?> = _runtimeSessionId.asStateFlow()

    private val _storedSessionId = MutableStateFlow<String?>(null)
    val storedSessionId: StateFlow<String?> = _storedSessionId.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var wsClient: GatewayWsClient? = null
    private var api: HermesApi? = null
    private var currentBaseUrl: String? = null
    private var currentTicket: String? = null
    private var streamingAssistantId: String? = null
    private var lastUsername: String? = null
    private var lastPassword: String? = null
    private val idSeq = java.util.concurrent.atomic.AtomicLong(0)

    private fun newId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}-${idSeq.incrementAndGet()}"

    init {
        if (DevCredentials.AUTO_LOGIN) {
            login(
                DevCredentials.GATEWAY_URL,
                DevCredentials.USERNAME,
                DevCredentials.PASSWORD,
            )
        }
    }

    fun setTab(tab: AppTab) {
        _tab.value = tab
    }

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

                val result = LoginHelper(context).login(baseUrl, username, password)
                result.fold(
                    onSuccess = { ticket ->
                        currentTicket = ticket
                        _state.value = AppState.Authenticated(baseUrl)
                        _messages.value = emptyList()
                        appendSystem("Logged in to $baseUrl")
                        // Load models BEFORE opening WS so session.create has a real model.
                        loadModelsBlocking()
                        connectWebSocket(baseUrl, ticket)
                        refreshSessions()
                    },
                    onFailure = { e ->
                        _connectionState.value = ConnectionState.ERROR
                        _loginError.value = e.message ?: "Login failed"
                        Log.e(TAG, "Login failed", e)
                    },
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                _loginError.value = e.message ?: "Connection error"
                Log.e(TAG, "Login exception", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadModelsBlocking() {
        val a = api ?: return
        _modelsLoading.value = true
        _modelsError.value = null
        try {
            val options = withContext(Dispatchers.IO) { a.modelOptions().getOrThrow() }
            _models.value = options
            val info = withContext(Dispatchers.IO) { a.modelInfo().getOrNull() }
            if (info != null) {
                val (provider, model) = info
                if (model.isNotBlank()) {
                    _selectedModel.value = model
                    _selectedProvider.value = provider.ifBlank {
                        options.firstOrNull { it.model == model }?.provider
                    }
                }
            }
            if (_selectedModel.value.isNullOrBlank()) {
                val preferred = options.firstOrNull { it.isCurrentProvider && it.model.contains("ornith", true) }
                    ?: options.firstOrNull { it.model.contains("ornith", true) }
                    ?: options.firstOrNull { it.model == "ornith-1.0-35b" }
                    ?: options.firstOrNull { it.isCurrentProvider }
                    ?: options.firstOrNull()
                if (preferred != null) {
                    _selectedModel.value = preferred.model
                    _selectedProvider.value = preferred.provider
                } else {
                    _selectedModel.value = "ornith-1.0-35b"
                    _selectedProvider.value = "lmstudio"
                }
            }
            appendSystem("Default model: ${_selectedProvider.value} / ${_selectedModel.value}")
        } catch (e: Exception) {
            _modelsError.value = e.message
            // Hard fallback so chat still works
            if (_selectedModel.value.isNullOrBlank()) {
                _selectedModel.value = "ornith-1.0-35b"
                _selectedProvider.value = "lmstudio"
            }
            Log.e(TAG, "loadModelsBlocking failed", e)
        } finally {
            _modelsLoading.value = false
        }
    }

    fun refreshModels() {
        val a = api ?: return
        viewModelScope.launch {
            _modelsLoading.value = true
            _modelsError.value = null
            try {
                val options = withContext(Dispatchers.IO) { a.modelOptions().getOrThrow() }
                _models.value = options
                val info = withContext(Dispatchers.IO) { a.modelInfo().getOrNull() }
                if (info != null) {
                    val (provider, model) = info
                    if (model.isNotBlank()) {
                        _selectedModel.value = model
                        _selectedProvider.value = provider.ifBlank {
                            options.firstOrNull { it.model == model }?.provider
                        }
                    }
                }
                if (_selectedModel.value.isNullOrBlank()) {
                    // Prefer current provider models, then ornith, then first
                    val preferred = options.firstOrNull { it.isCurrentProvider && it.model.contains("ornith", true) }
                        ?: options.firstOrNull { it.model.contains("ornith", true) }
                        ?: options.firstOrNull { it.isCurrentProvider }
                        ?: options.firstOrNull()
                    if (preferred != null) {
                        _selectedModel.value = preferred.model
                        _selectedProvider.value = preferred.provider
                    }
                }
            } catch (e: Exception) {
                _modelsError.value = e.message
                Log.e(TAG, "refreshModels failed", e)
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
                _sessions.value = withContext(Dispatchers.IO) { a.listSessions().getOrThrow() }
            } catch (e: Exception) {
                _sessionsError.value = e.message
                Log.e(TAG, "refreshSessions failed", e)
            } finally {
                _sessionsLoading.value = false
            }
        }
    }

    fun selectModel(option: HermesModelOption) {
        _selectedModel.value = option.model
        _selectedProvider.value = option.provider
        appendSystem("Model set to ${option.label} (applies to new chats)")
        // Next newChat/session.create will use it. If no runtime session yet, create one.
        if (_runtimeSessionId.value == null && _connectionState.value == ConnectionState.OPEN) {
            ensureSession(forceNew = true)
        }
    }

    fun newChat() {
        _tab.value = AppTab.Chat
        _messages.value = emptyList()
        streamingAssistantId = null
        _runtimeSessionId.value = null
        _storedSessionId.value = null
        _isBusy.value = false
        appendSystem("New chat")
        if (_connectionState.value == ConnectionState.OPEN) {
            ensureSession(forceNew = true)
        }
    }

    fun openStoredSession(summary: HermesSessionSummary) {
        _tab.value = AppTab.Chat
        viewModelScope.launch {
            appendSystem("Opening session ${summary.id}…")
            // Prefer resume via RPC when connected
            val client = wsClient
            if (client != null && client.isOpen) {
                client.sendRequest(
                    "session.resume",
                    mapOf("session_id" to summary.id),
                ) { result ->
                    result.fold(
                        onSuccess = { json ->
                            val sid = json.optString("session_id").ifBlank { summary.id }
                            _runtimeSessionId.value = sid
                            _storedSessionId.value = json.optString("resumed").ifBlank {
                                json.optString("session_key").ifBlank { summary.id }
                            }
                            if (!summary.model.isNullOrBlank()) {
                                _selectedModel.value = summary.model
                            }
                            // Load history from REST for display
                            loadSessionMessages(summary.id)
                            appendSystem("Resumed $sid")
                        },
                        onFailure = { e ->
                            appendSystem("Resume failed: ${e.message}")
                            loadSessionMessages(summary.id)
                        },
                    )
                }
            } else {
                loadSessionMessages(summary.id)
            }
        }
    }

    private fun loadSessionMessages(sessionId: String) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val msgs = withContext(Dispatchers.IO) { a.sessionMessages(sessionId).getOrThrow() }
                _messages.value = msgs.mapIndexed { idx, (role, content) ->
                    ChatMessage(id = "hist-$sessionId-$idx", role = normalizeRole(role), content = content)
                }
                _storedSessionId.value = sessionId
            } catch (e: Exception) {
                appendSystem("Failed to load messages: ${e.message}")
            }
        }
    }

    private fun normalizeRole(role: String): String = when (role.lowercase()) {
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

        appendMessage(ChatMessage(newId("u"), "user", trimmed))
        val assistantId = newId("a")
        streamingAssistantId = assistantId
        appendMessage(ChatMessage(assistantId, "assistant", ""))
        _isBusy.value = true

        fun submit(sessionId: String) {
            client.sendRequest(
                "prompt.submit",
                mapOf(
                    "session_id" to sessionId,
                    "text" to trimmed,
                ),
            ) { result ->
                result.onFailure { e ->
                    _isBusy.value = false
                    appendSystem("prompt.submit failed: ${e.message}")
                }.onSuccess { json ->
                    Log.i(TAG, "prompt.submit => $json")
                    // status streaming means wait for events
                    val status = json.optString("status")
                    if (status == "error") {
                        _isBusy.value = false
                        setAssistantFinal(json.optString("error", "submit error"))
                    }
                }
            }
        }

        val existing = _runtimeSessionId.value
        if (!existing.isNullOrBlank()) {
            submit(existing)
        } else {
            createSession { sid -> submit(sid) }
        }
    }

    fun interrupt() {
        val client = wsClient ?: return
        val sid = _runtimeSessionId.value ?: return
        client.sendRequest("session.interrupt", mapOf("session_id" to sid)) { result ->
            result.onSuccess {
                _isBusy.value = false
                appendSystem("Interrupted")
            }.onFailure { e ->
                appendSystem("Interrupt failed: ${e.message}")
            }
        }
    }

    fun reconnect() {
        val baseUrl = currentBaseUrl
        val user = lastUsername
        val pass = lastPassword
        if (baseUrl == null || user == null || pass == null) {
            _state.value = AppState.Unauthenticated
            _loginError.value = "Session expired — log in again"
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            appendSystem("Reconnecting...")
            val result = LoginHelper(context).login(baseUrl, user, pass)
            result.fold(
                onSuccess = { ticket ->
                    currentTicket = ticket
                    api = HermesApi(baseUrl)
                    connectWebSocket(baseUrl, ticket)
                },
                onFailure = { e ->
                    _connectionState.value = ConnectionState.ERROR
                    appendSystem("Reconnect login failed: ${e.message}")
                },
            )
        }
    }

    fun logout() {
        try {
            wsClient?.disconnect()
        } catch (_: Exception) {
        }
        wsClient = null
        api = null
        SharedCookieJar.clear()
        currentBaseUrl = null
        currentTicket = null
        streamingAssistantId = null
        lastUsername = null
        lastPassword = null
        _messages.value = emptyList()
        _models.value = emptyList()
        _sessions.value = emptyList()
        _runtimeSessionId.value = null
        _storedSessionId.value = null
        _connectionState.value = ConnectionState.IDLE
        _loginError.value = null
        _isBusy.value = false
        _tab.value = AppTab.Chat
        _state.value = AppState.Unauthenticated
    }

    private fun connectWebSocket(baseUrl: String, ticket: String) {
        try {
            wsClient?.disconnect()
        } catch (_: Exception) {
        }

        _connectionState.value = ConnectionState.CONNECTING
        val client = GatewayWsClient(baseUrl = baseUrl, token = null, ticket = ticket)
        wsClient = client

        client.onOpen = {
            _connectionState.value = ConnectionState.OPEN
            appendSystem("WebSocket open")
        }

        client.onReady = {
            _connectionState.value = ConnectionState.OPEN
            appendSystem("Gateway ready")
            ensureSession(forceNew = _runtimeSessionId.value == null)
        }

        client.onMessageDelta = { sessionId, text, _ ->
            if (!sessionId.isNullOrBlank()) _runtimeSessionId.value = sessionId
            if (text.isNotEmpty() && !isThinkingStatus(text)) {
                appendAssistantDelta(text)
            }
        }

        client.onEvent = { type, sessionId, payload ->
            if (!sessionId.isNullOrBlank()) _runtimeSessionId.value = sessionId
            Log.d(TAG, "event $type ${payload.toString().take(240)}")
            when (type) {
                "status.update" -> {
                    val statusText = payload.optString("text", "")
                    if (statusText.isNotBlank()) setAssistantPlaceholder(statusText)
                }
                "message.start" -> ensureAssistantBubble()
                "message.interim" -> {
                    val text = payload.optString("text", "")
                    if (text.isNotBlank() && !isThinkingStatus(text)) setAssistantFinal(text)
                }
                "message.complete" -> {
                    val text = payload.optString("text", "").ifBlank { payload.optString("error", "") }
                    val status = payload.optString("status", "complete")
                    when {
                        status == "error" || payload.has("error") -> {
                            val err = payload.optString("error", text).ifBlank { "Turn failed" }
                            setAssistantFinal("Error: $err")
                        }
                        text.isNotBlank() -> setAssistantFinal(text)
                        else -> {
                            val current = currentAssistantText()
                            if (current.isNullOrBlank() || isThinkingStatus(current)) {
                                setAssistantFinal("(empty response)")
                            }
                        }
                    }
                    streamingAssistantId = null
                    _isBusy.value = false
                    refreshSessions()
                }
                "session.info" -> {
                    val model = payload.optString("model")
                    if (model.isNotBlank()) _selectedModel.value = model
                    val stored = payload.optString("session_key").ifBlank {
                        payload.optString("stored_session_id")
                    }
                    if (stored.isNotBlank()) _storedSessionId.value = stored
                }
                "error", "agent.error", "turn.error" -> {
                    val msg = payload.optString("message", payload.optString("error", type))
                    appendSystem("Error: $msg")
                    streamingAssistantId = null
                    _isBusy.value = false
                }
            }
        }

        client.onError = { error ->
            _connectionState.value = ConnectionState.ERROR
            _isBusy.value = false
            appendSystem(error ?: "WebSocket error")
        }

        client.onClosed = {
            _connectionState.value = ConnectionState.CLOSED
            _isBusy.value = false
            appendSystem("WebSocket closed")
        }

        try {
            client.connect()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            appendSystem("WS connect failed: ${e.message}")
            Log.e(TAG, "connectWebSocket failed", e)
        }
    }

    private fun ensureSession(forceNew: Boolean = false) {
        if (!forceNew && !_runtimeSessionId.value.isNullOrBlank()) return
        createSession { }
    }

    private fun createSession(onReady: (String) -> Unit) {
        val client = wsClient
        if (client == null || !client.isOpen) {
            appendSystem("Cannot create session — not connected")
            return
        }
        val model = _selectedModel.value?.takeIf { it.isNotBlank() } ?: "ornith-1.0-35b"
        val provider = _selectedProvider.value?.takeIf { it.isNotBlank() } ?: "lmstudio"
        val params = mutableMapOf<String, Any?>(
            "source" to "android",
            "model" to model,
            "provider" to provider,
        )
        _selectedModel.value = model
        _selectedProvider.value = provider
        client.sendRequest("session.create", params) { result ->
            result.fold(
                onSuccess = { json ->
                    val sid = json.optString("session_id").ifBlank { json.optString("id") }
                    val stored = json.optString("stored_session_id")
                    if (sid.isBlank()) {
                        appendSystem("session.create returned no session_id: $json")
                        return@fold
                    }
                    _runtimeSessionId.value = sid
                    if (stored.isNotBlank()) _storedSessionId.value = stored
                    val info = json.optJSONObject("info")
                    val infoModel = info?.optString("model")
                    if (!infoModel.isNullOrBlank()) _selectedModel.value = infoModel
                    appendSystem("Session $sid" + (model?.let { " · $it" } ?: ""))
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
        if (t.isEmpty()) return false
        val re = Regex(
            """^(?:\([^)]{0,48}\)\s*)?(?:processing|thinking|reasoning|analyzing|pondering|contemplating|musing|cogitating|ruminating|deliberating|mulling|reflecting|computing|synthesizing|formulating|brainstorming)\.\.\.?$"""
        )
        return re.matches(t)
    }

    private fun ensureAssistantBubble(): String {
        val existing = streamingAssistantId
        if (existing != null) return existing
        val id = newId("a")
        streamingAssistantId = id
        appendMessage(ChatMessage(id, "assistant", ""))
        return id
    }

    private fun setAssistantPlaceholder(text: String) {
        val id = ensureAssistantBubble()
        _messages.update { list ->
            val idx = list.indexOfLast { it.id == id }
            if (idx < 0) return@update list + ChatMessage(id, "assistant", text)
            val old = list[idx]
            if (old.content.isBlank() || isThinkingStatus(old.content)) {
                list.toMutableList().also { it[idx] = old.copy(content = text) }
            } else list
        }
    }

    private fun setAssistantFinal(text: String) {
        val id = ensureAssistantBubble()
        _messages.update { list ->
            val idx = list.indexOfLast { it.id == id }
            if (idx >= 0) list.toMutableList().also { it[idx] = list[idx].copy(content = text) }
            else list + ChatMessage(id, "assistant", text)
        }
    }

    private fun currentAssistantText(): String? {
        val id = streamingAssistantId ?: return null
        return _messages.value.firstOrNull { it.id == id }?.content
    }

    private fun appendAssistantDelta(chunk: String) {
        val id = streamingAssistantId ?: run {
            val nid = newId("a")
            streamingAssistantId = nid
            appendMessage(ChatMessage(nid, "assistant", chunk))
            return
        }
        _messages.update { list ->
            val idx = list.indexOfLast { it.id == id }
            if (idx >= 0) {
                val old = list[idx]
                val base = if (isThinkingStatus(old.content)) "" else old.content
                list.toMutableList().also { it[idx] = old.copy(content = base + chunk) }
            } else list + ChatMessage(id, "assistant", chunk)
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _messages.update { it + message }
    }

    private fun appendSystem(text: String) {
        appendMessage(ChatMessage(id = newId("s"), role = "system", content = text))
    }

    override fun onCleared() {
        super.onCleared()
        try {
            wsClient?.disconnect()
        } catch (_: Exception) {
        }
        wsClient = null
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
    }
}
