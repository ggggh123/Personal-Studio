package com.example.personal_studio.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Renders a mixed-Markdown + LaTeX string through KaTeX in a WebView. Auto-sizes to
 * content height via a JS `ResizeObserver`. Not intended for streaming — call only
 * when the message is complete.
 *
 * Two subtleties this implementation gets right:
 *
 * 1. `evaluateJavascript` is only sent AFTER `onPageFinished`. Before that, the
 *    `setContent` function doesn't exist yet, which causes content to silently
 *    vanish on the first render.
 *
 * 2. The JS side uses `ResizeObserver` to keep reporting height as KaTeX's custom
 *    math fonts finish loading (which is async and causes late reflow). Without
 *    this, the bottom half of long messages gets cut off.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathMarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    /**
     * Initial height in dp to use before the WebView has finished rendering — typically
     * a cached value from a previous render of this same message. Without a cache, the
     * placeholder is a fixed 60dp and the item visibly pops to its real size once the
     * WebView's ResizeObserver reports, which causes other rows to shift. Passing a
     * prior measurement keeps LazyColumn rows stable when items re-enter the viewport.
     */
    initialHeightDp: Int = 60,
    /** Invoked when the WebView reports a new content height (in dp). */
    onHeightChanged: (Int) -> Unit = {},
) {
    // Height values from JS are in CSS px, which with our viewport (device-width,
    // initial-scale=1) are equivalent to dp. Do NOT convert via density — that treats
    // the value as physical px and under-sizes by the display density factor,
    // clipping the bottom of the content.
    //
    // We deliberately do NOT key remember on `markdown`: when content changes
    // (e.g. regenerate overwrites the summary), the WebView is reused via the
    // update block calling setContent, and the JS-side ResizeObserver will fire
    // onRendered with the new height. Resetting heightDp to initialHeightDp
    // here would make the host LazyColumn briefly measure the item at 60dp and,
    // because measurement only re-runs on state change, the item could remain
    // at 60dp until the next ResizeObserver tick — visually clipping the
    // re-rendered content. Keeping the previous height as the smooth-update
    // hint avoids that.
    var heightDp by remember { mutableStateOf(initialHeightDp) }
    var pageReady by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRendered(contentHeightCssPx: Int) {
                        // JS reports CSS pixels ≈ dp under our viewport config.
                        post {
                            val h = contentHeightCssPx + 12
                            heightDp = h
                            onHeightChanged(h)
                        }
                    }
                }, "Android")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        pageReady = true
                    }
                }

                loadUrl("file:///android_asset/katex/render.html")
            }
        },
        update = { web ->
            if (pageReady) {
                val encoded = JSONObject.quote(toHtml(markdown))
                web.evaluateJavascript("setContent($encoded)", null)
            }
            // If page isn't ready yet, the state flip from WebViewClient will trigger
            // another recomposition and this `update` lambda will run again with
            // pageReady=true. No explicit retry needed.
        },
        // Without this, every time the composable leaves composition (e.g. user
        // toggles a sibling edit/preview view) the WebView is dropped without
        // releasing its native resources. Each instance is several MB of native
        // heap + a JS context — observable as steady native-heap growth.
        onRelease = { web -> web.destroy() },
    )
}

// Minimal Markdown → HTML for what the LLM typically emits: paragraphs, bold, italic,
// inline code, newlines. LaTeX delimiters pass through untouched for KaTeX to render.
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
