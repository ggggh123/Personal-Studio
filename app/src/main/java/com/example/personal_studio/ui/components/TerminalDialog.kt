package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

/** 终端风弹窗骨架:0dp 直角 + Deep 背景 + Rule 边框 + 可选 `── TITLE ──` 头。 */
@Composable
fun TerminalDialog(onDismiss: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Deep, RectangleShape)
                .border(1.dp, Rule, RectangleShape)
                .padding(16.dp),
        ) {
            if (title != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = FoamDim)) { append("── ") }
                        withStyle(SpanStyle(color = Phosphor)) { append(title) }
                        withStyle(SpanStyle(color = FoamDim)) { append(" ──") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(top = 10.dp))
            }
            content()
        }
    }
}

/** 确认弹窗:`[取消] [confirmLabel]`。 */
@Composable
fun TerminalConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TerminalDialog(onDismiss = onDismiss, title = title) {
        Text(message, color = Foam, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("[取消]", color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Text("[$confirmLabel]", color = Carmine, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onConfirm() }.padding(8.dp))
        }
    }
}

/** 输入弹窗:终端风单行输入 + `[取消] [确认]`。 */
@Composable
fun TerminalInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    TerminalDialog(onDismiss = onDismiss, title = title) {
        Row(Modifier.fillMaxWidth()) {
            Text("> ", color = Phosphor, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onConfirm(text) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("[取消]", color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Text("[确认]", color = Phosphor, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onConfirm(text) }.padding(8.dp))
        }
    }
}
