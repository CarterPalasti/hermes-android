package app.hermes.net.gateway

import org.json.JSONObject

/** JSON-RPC 2.0 error object. */
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null,
) {
    override fun toString(): String = "JsonRpcError(code=$code, message='$message')"
}

/** JSON-RPC 2.0 types for Hermes WebSocket gateway. */
sealed class JsonRpcMessage {
    data class Request(
        val id: Long,
        val method: String,
        val params: Map<String, Any?> = emptyMap(),
    ) : JsonRpcMessage()

    data class Response(
        val id: Long? = null,
        val result: JSONObject?,
        val error: JsonRpcError?,
        val raw: String = "",
    ) : JsonRpcMessage()

    data class Notification(
        val method: String,
        val params: JSONObject = JSONObject(),
        val raw: String = "",
    ) : JsonRpcMessage()

    data class ServerError(
        val code: Int? = null,
        val message: String,
        val raw: String = "",
    ) : JsonRpcMessage()

    companion object {
        @Volatile
        private var nextIdCounter: Long = 1L

        fun nextId(): Long = synchronized(this) { nextIdCounter++ }

        fun parse(raw: String): JsonRpcMessage {
            val obj = try {
                JSONObject(raw)
            } catch (_: Exception) {
                return ServerError(message = "unparseable json", raw = raw)
            }

            val hasId = obj.has("id") && !obj.isNull("id")
            val hasMethod = obj.has("method") && obj.optString("method").isNotBlank()
            val hasResult = obj.has("result")
            val hasError = obj.has("error")

            // RPC response (id + result/error)
            if (hasId && (hasResult || hasError) && !hasMethod) {
                val idVal = obj.get("id")
                val id: Long = when (idVal) {
                    is Number -> idVal.toLong()
                    is String -> idVal.toLongOrNull() ?: 0L
                    else -> obj.optLong("id", 0L)
                }
                val error = if (hasError) {
                    val e = obj.optJSONObject("error")
                    JsonRpcError(
                        code = e?.optInt("code", 0) ?: 0,
                        message = e?.optString("message", "unknown") ?: "unknown",
                    )
                } else null
                val result = when {
                    hasResult && obj.opt("result") is JSONObject -> obj.getJSONObject("result")
                    hasResult -> JSONObject().put("value", obj.opt("result"))
                    else -> null
                }
                return Response(id = id, result = result, error = error, raw = raw)
            }

            // Notification / event
            if (hasMethod) {
                val method = obj.getString("method")
                val paramsObj = when (val p = obj.opt("params")) {
                    is JSONObject -> p
                    null -> JSONObject()
                    else -> JSONObject().put("value", p)
                }
                return Notification(method = method, params = paramsObj, raw = raw)
            }

            if (hasError) {
                val e = obj.optJSONObject("error")
                return ServerError(
                    code = e?.optInt("code"),
                    message = e?.optString("message", "unknown json-rpc error") ?: "unknown",
                    raw = raw,
                )
            }

            return ServerError(message = "unexpected json-rpc payload", raw = raw)
        }

        fun buildRequest(id: Long, method: String, params: Map<String, Any?>): String {
            val obj = JSONObject()
            obj.put("jsonrpc", "2.0")
            obj.put("id", id)
            obj.put("method", method)
            obj.put("params", paramsToJson(params))
            return obj.toString()
        }

        fun paramsToJson(params: Map<String, Any?>): JSONObject {
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
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        json.put(key, paramsToJson(value as Map<String, Any?>))
                    }
                    else -> json.put(key, value.toString())
                }
            }
            return json
        }
    }
}
