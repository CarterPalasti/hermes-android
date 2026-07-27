package app.hermes.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Process-wide cookie jar so login cookies are reused for REST + ticket mint. */
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

    fun postEmpty(url: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string().orEmpty())
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

class HermesApi(private val baseUrl: String) {
    private val root get() = baseUrl.trimEnd('/')

    fun modelInfo(): Result<Pair<String, String>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/model/info")
        if (code !in 200..299) error("model/info HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        json.optString("provider") to json.optString("model")
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
                            )
                        )
                    }
                }
            }
        }
    }

    fun listSessions(limit: Int = 50): Result<List<HermesSessionSummary>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/sessions?limit=$limit&order=recent")
        if (code !in 200..299) error("sessions HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        val arr = json.optJSONArray("sessions") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val id = s.optString("id")
                if (id.isBlank()) continue
                add(
                    HermesSessionSummary(
                        id = id,
                        title = s.optString("title").ifBlank { null },
                        preview = s.optString("preview").ifBlank { null },
                        model = s.optString("model").ifBlank { null },
                        messageCount = s.optInt("message_count", 0),
                        lastActive = if (s.has("last_active") && !s.isNull("last_active")) s.optDouble("last_active") else null,
                        source = s.optString("source").ifBlank { null },
                        profileName = s.optString("profile_name").ifBlank { null },
                    )
                )
            }
        }
    }

    fun sessionMessages(sessionId: String): Result<List<Pair<String, String>>> = runCatching {
        val (code, body) = SharedHttp.get("$root/api/sessions/${java.net.URLEncoder.encode(sessionId, "UTF-8")}/messages")
        if (code !in 200..299) error("session messages HTTP $code: ${body.take(160)}")
        val json = JSONObject(body)
        val arr = json.optJSONArray("messages")
            ?: json.optJSONArray("items")
            ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val role = m.optString("role", m.optString("type", "assistant"))
                val content = when {
                    m.has("content") && m.get("content") is String -> m.optString("content")
                    m.has("text") -> m.optString("text")
                    else -> m.toString()
                }
                if (content.isNotBlank()) add(role to content)
            }
        }
    }

    fun mintWsTicket(): Result<String> = runCatching {
        val (code, body) = SharedHttp.postEmpty("$root/api/auth/ws-ticket")
        if (code !in 200..299) error("ws-ticket HTTP $code: ${body.take(160)}")
        val ticket = JSONObject(body).optString("ticket")
        if (ticket.isBlank()) error("missing ticket")
        ticket
    }
}
