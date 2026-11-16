package com.example.personal_studio.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.theme.Tg
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.data.network.bit.BitBrowserHeaders
import com.example.personal_studio.data.network.bit.BitBrowserSessions
import com.example.personal_studio.data.network.bit.BitAuthUrls
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The school handles passwords, risk checks and SMS in its own HTTPS page.
 * Completion only retries native verification; it never marks the app logged in. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BitWebVerificationDialog(url: String, username: String, onFinished: (Boolean) -> Unit) {
    val context = LocalContext.current
    var loading by remember(url) { mutableStateOf(true) }
    var loadError by remember(url) { mutableStateOf<String?>(null) }
    var finished by remember(url) { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val finish: (WebView, Boolean) -> Unit = { view, verified ->
        if (!finished) {
            finished = true
            view.stopLoading()
            CookieManager.getInstance().flush()
            currentOnFinished(verified)
        }
    }
    val webView = remember(url) {
        WebView(context).apply {
            // WebView treats WRAP_CONTENT height as a zero-height initial containing
            // block. The school's 100%-height layout then loses its dark background.
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = BitBrowserHeaders.USER_AGENT
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                // Keep the default JavaScript dialogs available. The school logs
                // authentication payloads to its console; never forward those to logcat.
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
            }
            webViewClient = object : WebViewClient() {
                private var recoveredGatewayRelay = false
                private var mainFrameFailed = false

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString().toHttpUrlOrNull() ?: return true
                    val blocked = !target.isHttps || !BitBrowserSessions.isSchoolHost(target)
                    if (blocked && request.isForMainFrame) {
                        mainFrameFailed = true
                        loading = false
                        loadError = "学校登录跳转暂不支持，请重新加载后重试。"
                    }
                    return blocked
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    mainFrameFailed = false
                    loading = true
                    loadError = null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    if (!mainFrameFailed && view.url == url &&
                        url.toHttpUrlOrNull()?.let { BitAuthUrls().isBrowserCompletionCandidate(it) } == true) {
                        finish(view, true)
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        mainFrameFailed = true
                        loading = false
                        loadError = "学校页面暂时无法连接，请检查网络后重新加载。"
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (!request.isForMainFrame) return
                    mainFrameFailed = true
                    val recovery = request.url.toString().toHttpUrlOrNull()?.let {
                        BitAuthUrls().browserRecoveryUrl(it, errorResponse.statusCode)
                    }
                    if (recovery != null && !recoveredGatewayRelay) {
                        recoveredGatewayRelay = true
                        view.post { view.loadUrl(recovery) }
                        return
                    }
                    // CAS also uses HTTP 401 for a rendered login error. Keep that
                    // page visible so the user can read the school's explanation.
                    if (errorResponse.statusCode >= 500 || errorResponse.statusCode == 404) {
                        loading = false
                        loadError = "学校认证服务暂时异常，请稍后重新加载。"
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    handler.cancel()
                    mainFrameFailed = true
                    loading = false
                    loadError = "无法验证学校页面的安全连接，请稍后重试。"
                }
            }
            loadUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }
    Dialog(
        onDismissRequest = { finish(webView, false) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler {
            if (webView.canGoBack()) webView.goBack() else finish(webView, false)
        }
        val c = Tg.colors
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { finish(webView, false) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "取消验证", tint = c.text)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("学校统一身份认证", style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Text("学号 $username · 完成后会自动继续", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                IconButton(onClick = {
                    webView.stopLoading()
                    // Restart the GET flow instead of resubmitting a password or SMS POST.
                    webView.loadUrl(url)
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "重新加载", tint = c.text)
                }
            }
            Text(
                "已完成但没有自动返回？",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            )
            TgPillButton(
                "验证完成，继续",
                onClick = { finish(webView, true) },
                style = PillStyle.SECONDARY,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
            )
            loadError?.let {
                Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = c.error)
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.line)
            } else {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            }
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}
