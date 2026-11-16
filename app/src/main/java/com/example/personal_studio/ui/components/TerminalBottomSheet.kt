package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/**
 * Bottom sheet in the 天光 style: raised surface, 28 dp top corners, a short handle and an
 * optional serif header. (Historical name kept; used by screens not yet redesigned too.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalBottomSheet(
    onDismiss: () -> Unit,
    header: String? = null,
    showHandle: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = Tg.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceRaised,
        contentColor = c.text,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            if (showHandle) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(c.line, RoundedCornerShape(2.dp)))
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp)) {
            if (header != null) {
                Text(header, style = MaterialTheme.typography.headlineMedium, color = c.text, maxLines = 2)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** A row of a sheet menu: optional icon in a soft circle, label, optional hint; [destructive] uses the error colour. */
@Composable
fun SheetAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    hint: String? = null,
    destructive: Boolean = false,
) {
    val c = Tg.colors
    val tint = if (destructive) c.error else c.text
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).background(if (destructive) Color.Transparent else c.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (destructive) c.error else c.accent, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
