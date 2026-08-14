package app.hermes.ui.util

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

object Haptics {
    /**
     * Perform light haptic feedback (tick).
     * Fails soft if unavailable.
     */
    fun performLight(view: View) {
        try {
            view.performHapticFeedback(1) // HapticFeedbackConstants.VIRTUAL_KEY
        } catch (_: Exception) {
            // Fail silently - haptics are optional
        }
    }

    fun performLight(context: Context) {
        try {
            val view = View(context)
            performLight(view)
        } catch (_: Exception) {
            // Fail silently
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    return remember { Haptics }
}
