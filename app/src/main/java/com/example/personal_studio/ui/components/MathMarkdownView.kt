package com.example.personal_studio.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Renders a mixed-Markdown + LaTeX string through KaTeX in a WebView. Auto-sizes to
 * content height. Not for streaming — call only when the message is complete.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember(markdown) { mutableStateOf(60) }
    val density = LocalDensity.current

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() }),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRendered(contentHeightPx: Int) {
                        post { heightPx = contentHeightPx + 12 }
                    }
                }, "Android")

                loadUrl("file:///android_asset/katex/render.html")
            }
        },
        update = { web ->
            val encoded = JSONObject.quote(toHtml(markdown))
            web.post {
                web.evaluateJavascript("setContent($encoded)", null)
            }
        },
    )
}

// Minimal Markdown → HTML for what Gemini actually emits in our prompts:
// paragraphs, bold, italic, inline code, newlines. Keeps LaTeX delimiters intact.
private fun toHtml(md: String): String {
    val escaped = md
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    val paragraphs = escaped.split(Regex("\\n{2,}"))
    return paragraphs.joinToString("") { p ->
        val line = p
            .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "<em>$1</em>")
            .replace("\n", "<br>")
        "<p>$line</p>"
    }
}
