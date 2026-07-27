package app.hermes

import android.app.Application
import app.hermes.auth.AuthManager

class HermesApplication : Application() {
    val authManager: AuthManager by lazy {
        AuthManager(this)
    }
}
