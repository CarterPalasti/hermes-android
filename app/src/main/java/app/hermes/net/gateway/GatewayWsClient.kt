package app.hermes.net.gateway

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * JSON-RPC WebSocket client for Hermes dashboard `/api/ws`.
 *
 * Wire protocol matches tui_gateway / desktop:
 * - Connect with ws(s)://host/api/ws?ticket=...
 * - Server immediately emits {"method":"event","params":{"type":"gateway.ready",...}}
 * - Requests: {"jsonrpc":"2.0","id":N,"method":"...","params":{...}}
 * - Events:  {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"...","payload":{"text":"..."}}}
 */
class GatewayWsClient(
    private val baseUrl: String,
    private val token: String? = null,
    private val ticket: String? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // long-lived stream
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, (Result<JSONObject>) -> Unit>()

    var onOpen: (() -> Unit)? = null
    var onReady: ((String?) -> Unit)? = null
    var onMessageDelta: ((sessionId: String?, text: String, payload: JSONObject) -> Unit)? = null
    var onEvent: ((type: String, sessionId: String?, payload: JSONObject) -> Unit)? = null
    var onError: ((String?) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    @Volatile
    var isOpen: Boolean = false
        private set

    fun connect() {
        val wsUrl = buildWsUrl(baseUrl, token, ticket)
        Log.i(TAG, "Connecting WS: ${redactTicket(wsUrl)}")
        val request = Request.Builder().url(wsUrl).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WS opened code=${response.code}")
                isOpen = true
                post { onOpen?.invoke() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    parseFrame(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}", e)
                    post { onError?.invoke("Parse error: ${e.message}") }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isOpen = false
                val detail = buildString {
                    append(t.message ?: t.javaClass.simpleName)
                    if (response != null) append(" (HTTP ${response.code})")
                }
                Log.e(TAG, "WS failure: $detail", t)
                failAllPending(detail)
                post { onError?.invoke("Connection failed: $detail") }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closed: $code $reason")
                isOpen = false
                failAllPending("WebSocket closed ($code)")
                post { onClosed?.invoke() }
            }
        })
    }

    fun sendRequest(method: String, params: Map<String, Any?> = emptyMap(), callback: ((Result<JSONObject>) -> Unit)? = null) {
        val id = nextId.getAndIncrement()
        val frame = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", paramsToJson(params))
        }
        if (callback != null) {
            pending[id] = callback
        }
        val socket = ws
        if (socket == null || !isOpen) {
            pending.remove(id)
            callback?.invoke(Result.failure(IllegalStateException("WebSocket not connected")))
            return
        }
        val ok = socket.send(frame.toString())
        Log.i(TAG, "Sent $method id=$id ok=$ok")
        if (!ok) {
            pending.remove(id)
            callback?.invoke(Result.failure(IllegalStateException("WebSocket send failed")))
        }
    }

    fun disconnect() {
        isOpen = false
        try {
            ws?.close(1000, "Client disconnect")
        } catch (_: Exception) {
        }
        ws = null
        failAllPending("Disconnected")
    }

    private fun parseFrame(message: String) {
        val obj = JSONObject(message)

        // RPC response
        if (obj.has("id") && (obj.has("result") || obj.has("error"))) {
            val id = obj.optLong("id", -1L)
            val cb = pending.remove(id)
            if (obj.has("error")) {
                val err = obj.getJSONObject("error")
                val msg = err.optString("message", "RPC error")
                post { cb?.invoke(Result.failure(Exception(msg))) }
            } else {
                val result = obj.optJSONObject("result") ?: JSONObject()
                post { cb?.invoke(Result.success(result)) }
            }
            return
        }

        // Event frame: {"method":"event","params":{"type":"...","session_id":"...","payload":{...}}}
        if (obj.optString("method") == "event") {
            val params = obj.optJSONObject("params") ?: JSONObject()
            val type = params.optString("type", "")
            val sessionId = params.optString("session_id", null).takeUnless { it.isNullOrBlank() || it == "null" }
            val payload = params.optJSONObject("payload") ?: JSONObject()

            when (type) {
                "gateway.ready" -> {
                    Log.i(TAG, "gateway.ready")
                    post {
                        onReady?.invoke(sessionId)
                        onEvent?.invoke(type, sessionId, payload)
                    }
                }
                "message.delta", "reasoning.delta", "thinking.delta" -> {
                    val text = payload.optString("text", "")
                        .ifBlank { params.optString("text", "") }
                    if (text.isNotEmpty()) {
                        post { onMessageDelta?.invoke(sessionId, text, payload) }
                    }
                    post { onEvent?.invoke(type, sessionId, payload) }
                }
                else -> {
                    Log.d(TAG, "event type=$type")
                    post { onEvent?.invoke(type, sessionId, payload) }
                }
            }
            return
        }

        // Legacy direct method events (older sketches)
        if (obj.has("method")) {
            val method = obj.getString("method")
            if (method == "gateway.ready" || method == "ready") {
                post { onReady?.invoke(obj.optString("session_id", null)) }
            }
        }
    }

    private fun paramsToJson(params: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        params.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Double -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is JSONObject -> json.put(key, value)
                else -> json.put(key, value.toString())
            }
        }
        return json
    }

    private fun buildWsUrl(baseUrl: String, token: String?, ticket: String?): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        val wsBase = when {
            trimmed.startsWith("https://", ignoreCase = true) ->
                "wss://" + trimmed.removePrefix("https://").removePrefix("HTTPS://")
            trimmed.startsWith("http://", ignoreCase = true) ->
                "ws://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
            trimmed.startsWith("wss://", ignoreCase = true) ||
                trimmed.startsWith("ws://", ignoreCase = true) -> trimmed
            else -> "ws://$trimmed"
        }
        val auth = when {
            !ticket.isNullOrBlank() -> "ticket=${URLEncoder.encode(ticket, "UTF-8")}"
            !token.isNullOrBlank() -> "token=${URLEncoder.encode(token, "UTF-8")}"
            else -> throw IllegalArgumentException("Either token or ticket required")
        }
        return "$wsBase/api/ws?$auth"
    }

    private fun failAllPending(message: String) {
        val snapshot = pending.values.toList()
        pending.clear()
        post {
            snapshot.forEach { it.invoke(Result.failure(Exception(message))) }
        }
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun redactTicket(url: String): String =
        url.replace(Regex("""(ticket|token)=[^&]+"""), "$1=[REDACTED]")

    companion object {
        private const val TAG = "GatewayWsClient"
    }
}
