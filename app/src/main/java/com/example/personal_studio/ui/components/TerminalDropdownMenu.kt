package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void

/** 终端风下拉菜单:Popup + Void 底 + 0dp 直角 + Rule 边框,锚在触发处右上、下落 ~44dp。 */
@Composable
fun TerminalDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 44.dp.roundToPx()) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(180.dp)
                .background(Void, RectangleShape)
                .border(1.dp, Rule, RectangleShape)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

/** 下拉项:`▸ label`。 */
@Composable
fun TerminalDropdownItem(label: String, color: Color = Foam, onClick: () -> Unit) {
    Text(
        "▸ $label",
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
