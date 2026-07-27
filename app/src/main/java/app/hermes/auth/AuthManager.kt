package app.hermes.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.hermesDataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_prefs")

sealed class AuthStatus {
    data object Unauthenticated : AuthStatus()
    data class Authenticated(
        val baseUrl: String,
        val authMode: AuthMode,
        val providerId: String? = null,
    ) : AuthStatus()
}

enum class AuthMode { TOKEN, GATED_PASSWORD }

data class GatewayCredentials(
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val sessionToken: String? = null,
    val authMode: AuthMode? = null,
    val providerId: String? = null,
)

class AuthManager(private val context: Context) {

    suspend fun getAuthStatus(): AuthStatus {
        val prefs = context.hermesDataStore.data.first()
        val baseUrl = prefs[BASE_URL_KEY] ?: return AuthStatus.Unauthenticated
        val modeStr = prefs[AUTH_MODE_KEY]
        val providerId = prefs[PROVIDER_ID_KEY]

        val mode = when (modeStr) {
            "password" -> AuthMode.GATED_PASSWORD
            else -> AuthMode.TOKEN
        }

        return AuthStatus.Authenticated(baseUrl, mode, providerId)
    }

    suspend fun saveCredentials(creds: GatewayCredentials) {
        context.hermesDataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = creds.baseUrl.trimEnd('/')
            if (creds.username != null) preferences[USERNAME_KEY] = creds.username
            if (creds.password != null) preferences[PASSWORD_KEY] = creds.password
            if (creds.sessionToken != null) preferences[TOKEN_KEY] = creds.sessionToken
            if (creds.authMode != null) {
                preferences[AUTH_MODE_KEY] = when (creds.authMode) {
                    AuthMode.GATED_PASSWORD -> "password"
                    AuthMode.TOKEN -> "token"
                }
            }
            if (creds.providerId != null) preferences[PROVIDER_ID_KEY] = creds.providerId
        }
    }

    suspend fun clearCredentials() {
        context.hermesDataStore.edit { it.clear() }
    }

    private val BASE_URL_KEY = stringPreferencesKey("base_url")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val PASSWORD_KEY = stringPreferencesKey("password")
    private val TOKEN_KEY = stringPreferencesKey("session_token")
    private val AUTH_MODE_KEY = stringPreferencesKey("auth_mode")
    private val PROVIDER_ID_KEY = stringPreferencesKey("provider_id")
}
