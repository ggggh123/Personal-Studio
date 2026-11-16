package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.personal_studio.ui.theme.Tg

/*
 * 天光 dialogs: rounded raised cards with a serif title and pill buttons.
 */

/** Dialog card: raised surface, 24 dp corners, optional serif title. */
@Composable
fun TgDialog(onDismiss: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    val c = Tg.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surfaceRaised)
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text)
                Box(Modifier.padding(top = 8.dp))
            }
            content()
        }
    }
}

/** A "删除" style confirm gets the destructive button; anything else the primary one. */
@Composable
fun TgConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    TgDialog(onDismiss = onDismiss, title = title) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TgPillButton("取消", onClick = onDismiss, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            TgPillButton(
                confirmLabel,
                onClick = onConfirm,
                style = if (confirmLabel.contains("删除") || confirmLabel.contains("清除")) PillStyle.DESTRUCTIVE else PillStyle.PRIMARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Single-line input with the text selected, ready to replace. */
@Composable
fun TgInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TgDialog(onDismiss = onDismiss, title = title) {
        val shape = RoundedCornerShape(14.dp)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm(value.text) }),
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .focusRequester(focus)
                .background(c.bg, shape)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TgPillButton("取消", onClick = onDismiss, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
            TgPillButton("确认", onClick = { onConfirm(value.text) }, modifier = Modifier.weight(1f))
        }
    }
}
