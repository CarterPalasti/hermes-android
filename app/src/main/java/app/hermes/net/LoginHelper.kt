package app.hermes.net

import android.content.Context
import android.util.Log
import app.hermes.auth.AuthManager
import app.hermes.auth.AuthMode
import app.hermes.auth.GatewayCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GatewayStatus(
    val version: String?,
    val auth_required: Boolean?,
)

class LoginHelper(private val context: Context) {
    private val authManager = AuthManager(context)

    suspend fun login(baseUrl: String, username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val root = baseUrl.trim().trimEnd('/')
                SharedCookieJar.clear()

                val provider = resolvePasswordProvider(root)
                    ?: return@withContext Result.failure(
                        Exception("No password auth provider at $root (expected basic)")
                    )

                val loginBody = JSONObject()
                    .put("provider", provider)
                    .put("username", username)
                    .put("password", password)

                val (code, body) = SharedHttp.postJson("$root/auth/password-login", loginBody)
                when (code) {
                    200, 201 -> Unit
                    401 -> return@withContext Result.failure(Exception("Invalid username or password"))
                    404 -> return@withContext Result.failure(
                        Exception("Auth provider '$provider' rejected (HTTP 404). Wrong URL/port?")
                    )
                    429 -> return@withContext Result.failure(Exception("Too many login attempts"))
                    else -> return@withContext Result.failure(
                        Exception("Login failed HTTP $code: ${body.take(200)}")
                    )
                }

                val ticket = HermesApi(root).mintWsTicket().getOrElse {
                    return@withContext Result.failure(it)
                }

                authManager.saveCredentials(
                    GatewayCredentials(
                        baseUrl = root,
                        username = username,
                        password = password,
                        sessionToken = null,
                        authMode = AuthMode.GATED_PASSWORD,
                        providerId = provider,
                    )
                )
                Result.success(ticket)
            } catch (e: Exception) {
                Log.e(TAG, "login failed", e)
                Result.failure(e)
            }
        }

    private fun resolvePasswordProvider(root: String): String? {
        return try {
            val (code, body) = SharedHttp.get("$root/api/auth/providers")
            if (code !in 200..299) return "basic"
            val providers = JSONObject(body).optJSONArray("providers") ?: return "basic"
            for (i in 0 until providers.length()) {
                val p = providers.optJSONObject(i) ?: continue
                if (p.optBoolean("supports_password", false)) {
                    val name = p.optString("name")
                    if (name.isNotBlank()) return name
                }
            }
            "basic"
        } catch (_: Exception) {
            "basic"
        }
    }

    companion object {
        private const val TAG = "LoginHelper"
    }
}
