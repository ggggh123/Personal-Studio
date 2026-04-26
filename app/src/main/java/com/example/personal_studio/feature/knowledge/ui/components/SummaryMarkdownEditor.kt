package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

/**
 * Toggleable preview/edit pane for a markdown blob. Default mode is preview
 * (KaTeX-rendered via [MathMarkdownView]). Tap [edit] to swap to a
 * BasicTextField; tap [save] to commit, [cancel] to discard.
 *
 * Stateless wrt commit — caller persists via [onSave]. Internal local state
 * tracks the edit-mode draft so abandoned edits don't leak back to caller.
 */
@Composable
fun SummaryMarkdownEditor(
    initial: String,
    label: String = "summary",
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(initial) { mutableStateOf(initial) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "## $label",
                color = Phosphor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (!editing) {
                Text(
                    "[edit]",
                    color = FoamDim,
                    modifier = Modifier
                        .clickable { editing = true; draft = initial }
                        .padding(8.dp),
                )
            } else {
                Text(
                    "[cancel]",
                    color = FoamDim,
                    modifier = Modifier.clickable { editing = false }.padding(8.dp),
                )
                Text(
                    "[save]",
                    color = Phosphor,
                    modifier = Modifier
                        .clickable { onSave(draft); editing = false }
                        .padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MathMarkdownView(markdown = initial, modifier = Modifier.fillMaxWidth())
        }
    }
}
