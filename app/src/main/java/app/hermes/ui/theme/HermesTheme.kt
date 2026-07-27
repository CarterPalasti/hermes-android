package app.hermes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Nous Research brand accents — content stays readable white/black like the site body.
val NousBlue = Color(0xFF0000F2)
val NousBlueDeep = Color(0xFF0000C4)
val NousSnow = Color(0xFFF5F5F5)
val NousWhite = Color(0xFFFFFFFF)
val NousInk = Color(0xFF0A0A12)
val NousGray = Color(0xFF5C5C70)
val NousWash = Color(0xFFF7F7FF) // faint blue-white page wash

private val LightColors = lightColorScheme(
    primary = NousBlue,
    onPrimary = NousWhite,
    primaryContainer = Color(0xFFE8E8FF),
    onPrimaryContainer = NousBlueDeep,
    secondary = NousBlueDeep,
    onSecondary = NousWhite,
    secondaryContainer = Color(0xFFDCDCFF),
    onSecondaryContainer = NousBlue,
    background = NousWhite,
    onBackground = NousInk,
    surface = NousWhite,
    onSurface = NousInk,
    surfaceVariant = Color(0xFFF0F0FA),
    onSurfaceVariant = NousGray,
    error = Color(0xFFBA1A1A),
    onError = NousWhite,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A8AA8),
    outlineVariant = Color(0xFFD0D0E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9A9AFF),
    onPrimary = Color(0xFF00006A),
    primaryContainer = Color(0xFF0000C4),
    onPrimaryContainer = NousSnow,
    secondary = Color(0xFFB8B8FF),
    onSecondary = Color(0xFF10103A),
    secondaryContainer = Color(0xFF22225A),
    onSecondaryContainer = NousSnow,
    background = Color(0xFF0A0A12),
    onBackground = NousSnow,
    surface = Color(0xFF12121C),
    onSurface = NousSnow,
    surfaceVariant = Color(0xFF1C1C2A),
    onSurfaceVariant = Color(0xFFC8C8DC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6E6E88),
    outlineVariant = Color(0xFF333348),
)

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NousBlue.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
