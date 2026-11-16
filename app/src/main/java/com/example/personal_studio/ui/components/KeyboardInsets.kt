package com.example.personal_studio.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * While [active], the window neither pans nor shrinks for the keyboard (the system default,
 * which differs per ROM); the screen lifts its own content by the IME insets instead
 * (`imePadding`). The previous mode is restored afterwards, so other screens are unaffected.
 */
@Composable
fun KeyboardHandledByInsets(active: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(active) {
        val window = view.context.findActivity()?.window
        if (!active || window == null) return@DisposableEffect onDispose {}
        val previous = window.attributes.softInputMode
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                (previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE),
        )
        onDispose { window.setSoftInputMode(previous) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
