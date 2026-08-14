package app.hermes.net

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SharedCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val map = store.getOrPut(host) { ConcurrentHashMap() }
        cookies.forEach { map[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val map = store[url.host] ?: return emptyList()
        return map.values.filter { it.matches(url) }
    }

    fun clear() = store.clear()
}

object SharedHttp {
    private const val TAG = "SharedHttp"
    
    val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SharedCookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun get(url: String): Pair<Int, String> {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun postJson(url: String, body: JSONObject): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun putJson(url: String, body: JSONObject): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .put(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun postEmpty(url: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun delete(url: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .delete()
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun patchJson(url: String, body: JSONObject): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .patch(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
        }
    }

    fun postMultipart(url: String, filename: String, mimeType: String, data: ByteArray): Pair<Int, String> {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    filename,
                    data.toRequestBody(mimeType.toMediaType())
                )
                .build()
            
            val req = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
                
            client.newCall(req).execute().use { resp ->
                return resp.code to (resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Multipart upload failed: ${e.message}", e)
            return 500 to "{\"error\": \"${e.message}\"}"
        }
    }
}

data class HermesModelOption(
    val provider: String,
    val providerName: String,
    val model: String,
    val isCurrentProvider: Boolean,
) {
    val label: String get() = if (provider.isBlank()) model else "$provider / $model"
}

data class ModelInfoUi(
    val provider: String,
    val model: String,
    val contextLength: Int?,
)

data class HermesSessionSummary(
    val id: String,
    val title: String?,
    val preview: String?,
    val model: String?,
    val messageCount: Int,
    val lastActive: Double?,
    val source: String?,
    val profileName: String?,
)

data class SessionMessageDto(
    val id: String?,
    val role: String,
    val content: String,
    val toolName: String? = null,
    val toolCallSummary: String? = null,
    val reasoning: String? = null,
)

class HermesApi(private val baseUrl: String) {
    private val root get() = baseUrl.trimEnd('/')
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    fun modelInfo(): Result<ModelInfoUi> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/model/info")
        if (code !in 200..299) error("model/info HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        ModelInfoUi(
            provider = json.optString("provider"),
            model = json.optString("model"),
            contextLength = json.optInt(
                "effective_context_length",
                json.optInt("auto_context_length", 0),
            ).takeIf { it > 0 },
        )
    }

    fun modelOptions(): Result<List<HermesModelOption>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/model/options")
        if (code !in 200..299) error("model/options HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        val providers = json.optJSONArray("providers") ?: JSONArray()
        buildList {
            for (i in 0 until providers.length()) {
                val p = providers.optJSONObject(i) ?: continue
                val slug = p.optString("slug", "")
                val name = p.optString("name", slug)
                val isCurrent = p.optBoolean("is_current", false)
                val models = p.optJSONArray("models") ?: JSONArray()
                for (j in 0 until models.length()) {
                    val m = models.optString(j)
                    if (m.isNotBlank()) {
                        add(
                            HermesModelOption(
                                provider = slug,
                                providerName = name,
                                model = m,
                                isCurrentProvider = isCurrent,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun setModel(provider: String, model: String, scope: String = "main"): Result<Unit> = runCatching {
        val body = JSONObject()
            .put("scope", scope)
            .put("provider", provider)
            .put("model", model)
        val (code, resp) = SharedHttp.postJson("$root/api/model/set", body)
        if (code !in 200..299) {
            val (c2, r2) = SharedHttp.postJson(
                "$root/api/model",
                JSONObject().put("provider", provider).put("model", model),
            )
            if (c2 !in 200..299) error("set model HTTP $code/$c2: ${resp.take(120)} ${r2.take(120)}")
        }
    }

    fun listSessions(
        limit: Int = 100,
        query: String? = null,
        profile: String = "all",
        minMessages: Int = 0,
    ): Result<List<HermesSessionSummary>> = runCatching {
        val pageLimit = limit.coerceIn(1, 200)
        val out = mutableListOf<HermesSessionSummary>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (out.size < limit && offset < total) {
            val url = buildString {
                append("$root/api/profiles/sessions?profile=${enc(profile)}")
                append("&limit=$pageLimit&offset=$offset&order=recent")
                if (minMessages > 0) append("&min_messages=$minMessages")
                if (!query.isNullOrBlank()) append("&q=${enc(query)}")
            }
            val (code, body) = SharedHttp.get(url)
            if (code !in 200..299) {
                // Fallback process-scoped
                val (c2, b2) = SharedHttp.get("$root/api/sessions?limit=$pageLimit&order=recent")
                if (c2 !in 200..299) error("sessions HTTP $code/$c2: ${body.take(120)}")
                return@runCatching parseSessions(JSONObject(b2)).take(limit)
            }
            val json = JSONObject(body)
            val batch = parseSessions(json)
            total = json.optJSONObject("pagination")?.optInt("total", total) ?: total
            if (batch.isEmpty()) break
            out += batch
            offset += batch.size
            if (batch.size < pageLimit) break
        }
        out.take(limit)
    }

    private fun parseSessions(json: JSONObject): List<HermesSessionSummary> {
        val arr = json.optJSONArray("sessions") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val id = s.optString("id")
                if (id.isBlank()) continue
                add(
                    HermesSessionSummary(
                        id = id,
                        title = s.optString("title").ifBlank { null },
                        preview = s.optString("preview").ifBlank { s.optString("last_activity_description").ifBlank { null } },
                        model = s.optString("model").ifBlank { null },
                        messageCount = s.optInt("message_count", 0),
                        lastActive = when {
                            s.has("last_activity_at") && !s.isNull("last_activity_at") -> s.optDouble("last_activity_at")
                            s.has("last_active") && !s.isNull("last_active") -> s.optDouble("last_active")
                            else -> null
                        },
                        source = s.optString("source").ifBlank { null },
                        profileName = s.optString("profile_name").ifBlank {
                            s.optString("profile").ifBlank { null }
                        },
                    ),
                )
            }
        }
    }

    fun sessionMessages(sessionId: String, profile: String? = null): Result<List<SessionMessageDto>> = runCatching {
        val out = mutableListOf<SessionMessageDto>()
        var offset = 0
        val page = 100
        while (true) {
            val url = buildString {
                append("$root/api/sessions/${enc(sessionId)}/messages?limit=$page&offset=$offset&order=oldest")
                if (!profile.isNullOrBlank()) append("&profile=${enc(profile)}")
            }
            val (code, body) = SharedHttp.get(url)
            if (code !in 200..299) error("session messages HTTP $code: ${body.take(160)}")
            val json = JSONObject(body)
            val arr = json.optJSONArray("messages") ?: json.optJSONArray("items") ?: JSONArray()
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val role = m.optString("role", m.optString("type", "assistant"))
                val content = when {
                    m.has("content") && m.get("content") is String -> m.optString("content")
                    m.has("text") -> m.optString("text")
                    else -> ""
                }
                val toolCalls = m.optJSONArray("tool_calls")
                val toolSummary = if (toolCalls != null && toolCalls.length() > 0) {
                    buildList {
                        for (t in 0 until toolCalls.length()) {
                            val tc = toolCalls.optJSONObject(t) ?: continue
                            val fn = tc.optJSONObject("function")
                            val name = fn?.optString("name") ?: tc.optString("name")
                            if (name.isNotBlank()) add(name)
                        }
                    }.joinToString(", ").ifBlank { null }
                } else null
                out += SessionMessageDto(
                    id = m.optString("id").ifBlank { null },
                    role = role,
                    content = content,
                    toolName = m.optString("name").ifBlank { null },
                    toolCallSummary = toolSummary,
                    reasoning = m.optString("reasoning").ifBlank {
                        m.optString("thinking").ifBlank { null }
                    },
                )
            }
            val returned = json.optJSONObject("pagination")?.optInt("returned", arr.length()) ?: arr.length()
            if (returned < page) break
            offset += returned
            if (offset > 5000) break
        }
        out
    }

    fun listProfiles(): Result<List<Pair<String, String?>>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/profiles")
        if (code !in 200..299) error("profiles HTTP $code")
        val json = JSONObject(body)
        val arr = json.optJSONArray("profiles") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val name = p.optString("name")
                if (name.isNotBlank()) add(name to p.optString("path").ifBlank { null })
            }
        }
    }

    fun getActiveProfiles(): Result<Pair<String?, String?>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/profiles/active")
        if (code !in 200..299) error("profiles/active HTTP $code")
        val json = JSONObject(body)
        json.optString("active").ifBlank { null } to json.optString("current").ifBlank { null }
    }

    fun setActiveProfile(name: String): Result<Pair<String?, String?>> = runCatching {
        val (code, body) = SharedHttp.postJson(
            "$root/api/profiles/active",
            JSONObject().put("name", name),
        )
        if (code !in 200..299) error("set active profile HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        json.optString("active").ifBlank { name } to json.optString("current").ifBlank { null }
    }

    fun mintWsTicket(): Result<String> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/auth/ws-ticket")
        if (code !in 200..299) error("ws-ticket HTTP $code: ${body.take(160)}")
        val ticket = JSONObject(body).optString("ticket")
        if (ticket.isBlank()) error("missing ticket")
        ticket
    }

    // Status endpoint
    fun status(): Result<JSONObject> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/status")
        if (code !in 200..299) error("status HTTP $code: ${body.take(160)}")
        JSONObject(body)
    }

    // Config endpoints
    fun getConfigRaw(): Result<String> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/config/raw")
        if (code !in 200..299) error("config/raw HTTP $code: ${body.take(160)}")
        body
    }

    fun saveConfigRaw(yaml: String): Result<Unit> = runCatching {
        val body = JSONObject().put("yaml", yaml)
        val (code, resp) = SharedHttp.putJson("$root/api/config/raw", body)
        if (code !in 200..299) error("config/raw PUT HTTP $code: ${resp.take(160)}")
    }

    // Cron endpoints
    fun listCronJobs(): Result<List<CronJobDto>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/cron/jobs")
        if (code !in 200..299) error("cron/jobs HTTP $code: ${body.take(160)}")
        val trimmed = body.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val json = JSONObject(trimmed)
                json.optJSONArray("jobs") ?: json.optJSONArray("items") ?: JSONArray()
            }
        }
        buildList {
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                val id = j.optString("id")
                if (id.isBlank()) continue
                add(
                    CronJobDto(
                        id = id,
                        name = j.optString("name").ifBlank { j.optString("title", id) },
                        schedule = j.optString("schedule", ""),
                        enabled = j.optBoolean("enabled", true),
                        lastStatus = j.optString("last_status").ifBlank {
                            j.optString("lastStatus").ifBlank { null }
                        },
                    ),
                )
            }
        }
    }

    fun triggerCronJob(id: String): Result<Unit> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/cron/jobs/${enc(id)}/trigger")
        if (code !in 200..299) error("cron/trigger HTTP $code: ${body.take(160)}")
    }

    fun pauseCronJob(id: String): Result<Unit> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/cron/jobs/${enc(id)}/pause")
        if (code !in 200..299) error("cron/pause HTTP $code: ${body.take(160)}")
    }

    fun resumeCronJob(id: String): Result<Unit> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/cron/jobs/${enc(id)}/resume")
        if (code !in 200..299) error("cron/resume HTTP $code: ${body.take(160)}")
    }

    // Logs endpoint
    fun getLogs(limit: Int = 100, file: String? = null): Result<List<String>> = runCatching {
        val url = buildString {
            append("$root/api/logs?limit=$limit")
            if (!file.isNullOrBlank()) append("&file=${enc(file)}")
        }
        val (code, body) = SharedHttp.get(url)
        if (code !in 200..299) error("logs HTTP $code: ${body.take(160)}")
        val trimmed = body.trim()
        when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
            }
            else -> {
                val json = JSONObject(trimmed)
                val arr = json.optJSONArray("lines") ?: json.optJSONArray("logs") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is String -> add(item)
                            is JSONObject -> add(item.optString("message", item.toString()))
                            else -> add(item?.toString().orEmpty())
                        }
                    }
                }
            }
        }
    }

    // Pairing endpoints
    fun listPairing(): Result<List<JSONObject>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/pairing")
        if (code !in 200..299) error("pairing HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        val arr = json.optJSONArray("pending") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) add(arr.optJSONObject(i))
        }
    }

    fun approvePairing(id: String): Result<Unit> = runCatching {
        val body = JSONObject().put("id", id)
        val (code, resp) = SharedHttp.postJson("$root/api/pairing/approve", body)
        if (code !in 200..299) error("pairing/approve HTTP $code: ${resp.take(160)}")
    }

    fun revokePairing(id: String): Result<Unit> = runCatching {
        val body = JSONObject().put("id", id)
        val (code, resp) = SharedHttp.postJson("$root/api/pairing/revoke", body)
        if (code !in 200..299) error("pairing/revoke HTTP $code: ${resp.take(160)}")
    }

    fun clearPendingPairing(): Result<Unit> = runCatching {
        val (code, resp) = SharedHttp.postEmpty("$root/api/pairing/clear-pending")
        if (code !in 200..299) error("pairing/clear-pending HTTP $code: ${resp.take(160)}")
    }

    // Ops endpoints
    fun runDoctor(): Result<String> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/ops/doctor")
        if (code !in 200..299) error("ops/doctor HTTP $code: ${body.take(160)}")
        body
    }

    fun runBackup(): Result<String> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/ops/backup")
        if (code !in 200..299) error("ops/backup HTTP $code: ${body.take(160)}")
        body
    }

    fun restartGateway(): Result<Unit> = runCatching {
        val (code, resp) = SharedHttp.postEmpty("$root/api/gateway/restart")
        if (code !in 200..299) error("gateway/restart HTTP $code: ${resp.take(160)}")
    }

    // Sessions management endpoints
    fun getSession(id: String): Result<SessionDetailDto> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/sessions/${enc(id)}")
        if (code !in 200..299) error("sessions/$id HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        SessionDetailDto(
            id = json.optString("id"),
            title = json.optString("title").ifBlank { null },
            preview = json.optString("preview", null).ifBlank { null },
            model = json.optString("model", null).ifBlank { null },
            messageCount = json.optInt("message_count", 0),
            lastActive = when {
                json.has("last_activity_at") && !json.isNull("last_activity_at") -> json.optDouble("last_activity_at")
                else -> null
            },
            profileName = json.optString("profile_name", null).ifBlank { null },
        )
    }

    fun deleteSession(id: String): Result<Unit> = runCatching {
        val (code, resp) = SharedHttp.delete("$root/api/sessions/${enc(id)}")
        if (code !in 200..299) error("sessions/$id DELETE HTTP $code: ${resp.take(160)}")
    }

    fun renameSession(id: String, title: String): Result<Unit> = runCatching {
        val body = JSONObject().put("title", title)
        val (code, resp) = SharedHttp.patchJson("$root/api/sessions/${enc(id)}", body)
        if (code !in 200..299) error("sessions/$id PATCH HTTP $code: ${resp.take(160)}")
    }

    fun exportSession(id: String): Result<String> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/sessions/${enc(id)}/export")
        if (code !in 200..299) error("sessions/$id/export HTTP $code: ${body.take(160)}")
        body
    }

    fun searchSessions(query: String): Result<List<HermesSessionSummary>> = runCatching {
        val url = "$root/api/sessions/search?q=${enc(query)}"
        val (code, body) = SharedHttp.get(url)
        if (code !in 200..299) error("sessions/search HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        parseSessions(json)
    }
    
    // File upload endpoint - best-effort, logs and returns empty on failure
    fun uploadFile(filename: String, mimeType: String, data: ByteArray): Result<String> = runCatching {
        val url = "$root/api/files/upload"
        val (code, body) = SharedHttp.postMultipart(url, filename, mimeType, data)
        if (code !in 200..299) {
            android.util.Log.i("HermesApi", "File upload failed HTTP $code: ${body.take(160)}")
            // Best-effort: don't fail the operation, just log
            return@runCatching ""
        }
        val json = JSONObject(body)
        json.optString("path", json.optString("url", ""))
    }
}

// DTOs for REST responses
data class CronJobDto(
    val id: String,
    val name: String,
    val schedule: String,
    val enabled: Boolean,
    val lastStatus: String?,
)

data class SessionDetailDto(
    val id: String,
    val title: String?,
    val preview: String?,
    val model: String?,
    val messageCount: Int,
    val lastActive: Double?,
    val profileName: String?,
)
