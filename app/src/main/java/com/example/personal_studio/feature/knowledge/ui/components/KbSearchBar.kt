package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void

/**
 * 知识库搜索框:0dp 直角 Rule 边框输入框(聚焦时边框变 Phosphor)+ `$ grep -r "…" kb/`
 * 提示骨架 + 空态占位 `搜索条目…`。给裸输入一个容器与可输入的视觉暗示。
 */
@Composable
fun KbSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, if (focused) Phosphor else Rule, RectangleShape)
            .background(Void)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("$ grep -r ") }
                withStyle(SpanStyle(color = Phosphor)) { append("\"") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
            cursorBrush = SolidColor(Phosphor),
            interactionSource = interaction,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("搜索条目…", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    inner()
                }
            },
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("\"") }
                withStyle(SpanStyle(color = FoamDim)) { append(" kb/") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
