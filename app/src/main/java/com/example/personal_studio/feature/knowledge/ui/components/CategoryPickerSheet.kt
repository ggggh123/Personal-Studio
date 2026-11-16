package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.theme.Tg

/**
 * Choose a category for an entry, or type a new one. Picking or creating closes the sheet.
 * Categories are few, so they are laid out directly rather than in a lazy list (a lazy list
 * inside the sheet's column would have unbounded height).
 */
@Composable
fun CategoryPickerSheet(
    categories: List<KbCategory>,
    selectedId: Long?,
    onPick: (KbCategory) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    var newName by remember { mutableStateOf("") }
    val create = {
        val name = newName.trim()
        if (name.isNotEmpty()) {
            val existing = categories.firstOrNull { it.name == name }
            if (existing != null) onPick(existing) else onCreate(name)
            newName = ""
            onDismiss()
        }
    }
    TerminalBottomSheet(onDismiss = onDismiss, header = "选择分类") {
        categories.forEach { cat ->
            val sel = cat.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { onPick(cat); onDismiss() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cat.name, style = MaterialTheme.typography.bodyLarge, color = if (sel) c.accent else c.text, modifier = Modifier.weight(1f))
                if (sel) Icon(Icons.Rounded.Check, contentDescription = "当前分类", tint = c.accent, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TgTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "新建分类",
                placeholder = "例如 线性代数",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier.weight(1f),
            )
            TgPillButton("新建", onClick = create, enabled = newName.isNotBlank())
        }
    }
}
