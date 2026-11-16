package com.example.personal_studio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/*
 * Controls for the always-dark photo screens (camera, crop, enhance, page view): light
 * pills on a dark canvas so they never tint the photo.
 */

/** A pill that is filled light when [selected] (filters, toggles), translucent otherwise. */
@Composable
fun PhotoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    role: Role = Role.RadioButton,
) {
    val c = Tg.colors
    val bg by animateColorAsState(if (selected) c.text else c.text.copy(alpha = 0.12f), tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.bg else c.text, tween(200), label = "chipFg")
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(role = role, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = fg)
    }
}

/** A round translucent icon button (rotate, close…). */
@Composable
fun PhotoIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.text.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
    }
}

/** Bottom bar of a photo step: a secondary pill and the primary one, above the gesture bar. */
@Composable
fun PhotoActionBar(
    secondary: String,
    onSecondary: () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    above: (@Composable RowScope.() -> Unit)? = null,
) {
    if (above != null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = above,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TgPillButton(secondary, onClick = onSecondary, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
        TgPillButton(primary, onClick = onPrimary, style = PillStyle.PRIMARY, enabled = primaryEnabled, modifier = Modifier.weight(1f))
    }
}
