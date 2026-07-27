package app.hermes.net.gateway

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Gateway connection manager: handles WS lifecycle, ticket refresh, reconnect.
 */
class GatewayConnectionManager(
    private val baseUrl: String,
    private val token: String? = null,
    private val ticketProvider: () -> suspend () -> String?,
) {

    enum class State { IDLE, CONNECTING, OPEN, CLOSED, ERROR }

    private val stateRef = AtomicReference(State.IDLE)
    val state: State get() = stateRef.get()

    var onReady: ((String?) -> Unit)? = null
    var onMessageDelta: ((String?, String?, JSONObject) -> Unit)? = null
    var onToolStart: ((JSONObject) -> Unit)? = null
    var onToolComplete: ((JSONObject) -> Unit)? = null
    var onError: ((String?) -> Unit)? = null
    var onStateChanged: ((State) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var ws: WebSocket? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = 2000L
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun connect() {
        if (state == State.CONNECTING || state == State.OPEN) return

        setState(State.CONNECTING)

        scope.launch {
            try {
                val ticketFn = ticketProvider()
                val currentTicket = try {
                    ticketFn()
                } catch (e: Exception) {
                    onError?.invoke("Failed to mint ticket: ${e.message}")
                    setState(State.ERROR)
                    return@launch
                }

                val wsUrl = buildWsUrl(baseUrl, token, currentTicket)
                val request = Request.Builder().url(wsUrl).build()

                ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        reconnectAttempts = 0
                        setState(State.OPEN)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            parseJsonRpc(text)
                        } catch (e: Exception) {
                            onError?.invoke("Parse error: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        onError?.invoke("Connection failed: ${t.message}")
                        setState(State.ERROR)
                        attemptReconnect()
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        ws = null
                        setState(State.CLOSED)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        ws = null
                        setState(State.CLOSED)
                    }
                })
            } catch (e: Exception) {
                onError?.invoke("Connect error: ${e.message}")
                setState(State.ERROR)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            ws?.close(1000, "Client disconnect")
            ws = null
            setState(State.CLOSED)
        }
    }

    suspend fun sendRequest(method: String, params: Map<String, Any?>): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val payload = buildJsonRpcRequest(method, params)
            ws?.send(payload.toString())
            scope.launch {
                delay(100)
                if (continuation.isActive) {
                    continuation.resume(JSONObject().put("ok", true))
                }
            }
        }

    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            onError?.invoke("Max reconnect attempts reached")
            setState(State.ERROR)
            return
        }

        reconnectAttempts++
        scope.launch {
            delay(reconnectDelayMs * reconnectAttempts)
            connect()
        }
    }

    private fun buildWsUrl(baseUrl: String, token: String?, ticket: String?): String {
        val base = baseUrl.trimEnd('/')
        return when {
            ticket != null ->
                "$base/api/ws?ticket=${java.net.URLEncoder.encode(ticket, "UTF-8")}"
            token != null ->
                "$base/api/ws?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
            else -> throw IllegalArgumentException("Either token or ticket required")
        }
    }

    private fun buildJsonRpcRequest(method: String, params: Map<String, Any?>): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params.isNotEmpty()) {
                val jsonParams = JSONObject()
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> jsonParams.put(key, value)
                        is Boolean -> jsonParams.put(key, value)
                        is Number -> jsonParams.put(key, value)
                        null -> jsonParams.put(key, JSONObject.NULL)
                        else -> jsonParams.put(key, value.toString())
                    }
                }
                put("params", jsonParams)
            }
        }
    }

    private fun parseJsonRpc(message: String) {
        val obj = JSONObject(message)
        if (!obj.has("method")) return

        when (obj.getString("method")) {
            "gateway.ready" -> onReady?.invoke(obj.optString("session_id"))
            "message.delta" -> onMessageDelta?.invoke(
                obj.optString("session_id"),
                obj.optString("text", ""),
                obj.optJSONObject("payload") ?: JSONObject(),
            )
            "tool.start" -> onToolStart?.invoke(obj.optJSONObject("payload") ?: JSONObject())
            "tool.complete" -> onToolComplete?.invoke(obj.optJSONObject("payload") ?: JSONObject())
        }
    }

    private fun setState(newState: State) {
        val previous = stateRef.getAndSet(newState)
        if (previous != newState) {
            onStateChanged?.invoke(newState)
        }
    }
}
