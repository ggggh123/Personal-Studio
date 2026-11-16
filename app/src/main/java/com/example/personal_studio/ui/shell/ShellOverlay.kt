package com.example.personal_studio.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import kotlinx.coroutines.delay

/**
 * A layer above the whole shell, bottom bar included, for sheets that belong to a page
 * (the 今日 item detail). A page puts its sheet here with [InShellOverlay].
 */
class ShellOverlay {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

val LocalShellOverlay = staticCompositionLocalOf<ShellOverlay?> { null }

@Composable
fun ShellOverlayHost(overlay: ShellOverlay) {
    overlay.content?.invoke()
}

/** Shows [content] in the shell overlay while this call stays in the composition. */
@Composable
fun InShellOverlay(content: @Composable () -> Unit) {
    val overlay = LocalShellOverlay.current
    if (overlay == null) {
        content()
        return
    }
    val latest by rememberUpdatedState(content)
    DisposableEffect(overlay) {
        val slot: @Composable () -> Unit = { latest() }
        overlay.content = slot
        onDispose { if (overlay.content === slot) overlay.content = null }
    }
}

/** Short confirmations ("已添加，截止前会提醒你") in a pill above the bottom bar. */
class ShellToast {
    internal var message by mutableStateOf<String?>(null)
    internal var serial by mutableIntStateOf(0)

    fun show(text: String) {
        message = text
        serial++
    }
}

val LocalShellToast = staticCompositionLocalOf<ShellToast?> { null }

@Composable
fun ShellToastHost(toast: ShellToast, bottomPadding: Dp) {
    val c = Tg.colors
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(toast.serial) {
        val text = toast.message ?: return@LaunchedEffect
        shown = text
        delay(2_300)
        if (toast.message == text) toast.message = null
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = bottomPadding), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = toast.message != null,
            enter = fadeIn(tween(250)) + slideInVertically(TgMotion.standard()) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutVertically(TgMotion.exit()) { it / 3 },
        ) {
            Text(
                shown,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                color = c.bg,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(c.text, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
