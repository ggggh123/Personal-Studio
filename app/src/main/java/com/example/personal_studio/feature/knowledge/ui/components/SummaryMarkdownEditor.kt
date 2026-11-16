package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.theme.Tg

/**
 * A titled Markdown section that reads as rendered text (formulas included) and switches
 * to a source editor on 编辑. The draft stays local until 保存, so an abandoned edit never
 * reaches the caller.
 */
@Composable
fun SummaryMarkdownEditor(
    initial: String,
    label: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    var editing by remember { mutableStateOf(false) }
    var draft by remember(initial) { mutableStateOf(initial) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f))
            if (!editing) {
                Row(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button, onClickLabel = "编辑$label") { draft = initial; editing = true }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                    Text("编辑", style = MaterialTheme.typography.bodyMedium, color = c.accent)
                }
            }
        }
        AnimatedContent(
            targetState = editing,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "summaryMode",
            modifier = Modifier.padding(top = 8.dp),
        ) { isEditing ->
            if (isEditing) {
                Column {
                    TgTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = false,
                        minLines = 6,
                        hint = "支持 Markdown 与 \$公式\$",
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                        TgPillButton("取消", onClick = { editing = false }, style = PillStyle.SECONDARY)
                        TgPillButton("保存", onClick = { onSave(draft); editing = false })
                    }
                }
            } else if (initial.isBlank()) {
                Text("还没有内容，点“编辑”补充。", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            } else {
                MathMarkdownView(markdown = initial, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
