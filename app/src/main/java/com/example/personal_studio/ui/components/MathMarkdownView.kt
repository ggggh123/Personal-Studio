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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.personal_studio.ui.theme.Tg
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
    var heightDp by remember { mutableIntStateOf(initialHeightDp) }

    // 天光 tokens for the page's CSS variables. Pushed separately from the content so the
    // minute-by-minute palette drift restyles formulas without re-rendering them.
    val colors = Tg.colors
    val themeJson = remember(colors) {
        JSONObject()
            .put("text", colors.text.cssHex())
            .put("text2", colors.textSecondary.cssHex())
            .put("accent", colors.accent.cssHex())
            .put("line", colors.line.cssHex())
            .put("error", colors.error.cssHex())
            .toString()
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        factory = { ctx -> MathWebView(ctx) },
        update = { web ->
            // The listener is replaced on every update, so a recycled view reports to the
            // row now showing it.
            web.onHeight = { h -> heightDp = h; onHeightChanged(h) }
            web.show(markdown, themeJson)
        },
        // Lazy lists recycle the view for the next row instead of building a new WebView and
        // loading the KaTeX page again (tens of milliseconds each while scrolling a chat).
        onReset = { web -> web.onHeight = null },
        // Without this, a view that leaves for good is dropped without releasing its native
        // resources (several MB of native heap and a JS context each).
        onRelease = { web -> web.destroy() },
    )
}

/**
 * The KaTeX page in a WebView that remembers what it shows, so recycling it for another row
 * only sends the new content. `setContent` is sent only after the page has loaded (before
 * that the function does not exist and the content would vanish); the page's ResizeObserver
 * keeps reporting the height as KaTeX fonts load.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
private class MathWebView(context: android.content.Context) : WebView(context) {
    var onHeight: ((Int) -> Unit)? = null
    private var pageReady = false
    private var wantMarkdown: String? = null
    private var wantTheme: String? = null
    private var shownMarkdown: String? = null
    private var shownTheme: String? = null

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = true
        addJavascriptInterface(object {
            @JavascriptInterface
            fun onRendered(contentHeightCssPx: Int) {
                // CSS px equal dp under the page's viewport (device-width, scale 1).
                post { onHeight?.invoke(contentHeightCssPx + 12) }
            }
        }, "Android")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                pageReady = true
                sync()
            }
        }
        loadUrl("file:///android_asset/katex/render.html")
    }

    fun show(markdown: String, themeJson: String) {
        wantMarkdown = markdown
        wantTheme = themeJson
        sync()
    }

    private fun sync() {
        if (!pageReady) return
        val theme = wantTheme
        if (theme != null && theme != shownTheme) {
            evaluateJavascript("setTheme($theme)", null)
            shownTheme = theme
        }
        val md = wantMarkdown
        if (md != null && md != shownMarkdown) {
            evaluateJavascript("setContent(${JSONObject.quote(toHtml(md))})", null)
            shownMarkdown = md
        }
    }
}

private fun Color.cssHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
