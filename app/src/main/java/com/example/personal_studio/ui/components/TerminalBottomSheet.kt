package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/** 终端风底部弹层:Void + 0dp 直角 + 无拖柄 + 可选 `── header ──`。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalBottomSheet(
    onDismiss: () -> Unit,
    header: String? = null,
    showHandle: Boolean = false,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Void,
        shape = RectangleShape,
        dragHandle = { if (showHandle) TerminalDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (header != null) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = FoamDim)) { append("── ") }
                        withStyle(SpanStyle(color = Phosphor)) { append(header) }
                        withStyle(SpanStyle(color = FoamDim)) { append(" ──") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.padding(top = 14.dp))
            }
            content()
        }
    }
}

/** 终端风拖动柄:居中的 Phosphor 短条(0dp 直角)。 */
@Composable
private fun TerminalDragHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(3.dp).background(Phosphor, RectangleShape))
    }
}
