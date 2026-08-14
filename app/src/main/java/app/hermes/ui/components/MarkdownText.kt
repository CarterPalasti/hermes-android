package app.hermes.ui.components

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme

/**
 * Renders GitHub-flavored-ish markdown (bold, italics, code, lists, headers, links)
 * via Markwon inside Compose.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color,
    modifier: Modifier = Modifier,
    textSizeSp: Float = MaterialTheme.typography.bodyMedium.fontSize.value,
    isMonospace: Boolean = false,
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    // Keep theme defaults; color is applied on the TextView.
                    builder.headingBreakHeight(0)
                }
            })
            .build()
    }
    val argb = color.toArgb()
    val body = markdown.ifBlank { " " }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setLineSpacing(0f, 1.2f)
                setIncludeFontPadding(false)
                if (isMonospace) typeface = Typeface.MONOSPACE
            }
        },
        update = { tv ->
            tv.setTextColor(argb)
            tv.setLinkTextColor(argb)
            if (isMonospace) {
                tv.typeface = Typeface.MONOSPACE
            }
            markwon.setMarkdown(tv, body)
        },
    )
}
