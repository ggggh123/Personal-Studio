package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion

/** How a [TgPillButton] is filled. */
enum class PillStyle { PRIMARY, SECONDARY, DESTRUCTIVE, ACCENT }

/**
 * The 天光 action button: a 48 dp pill. Primary is filled with the text colour, secondary is
 * outlined, destructive is filled with the error colour, accent with the accent. Sinks a
 * little while pressed.
 */
@Composable
fun TgPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, TgMotion.fast(), label = "pill")
    val shape = RoundedCornerShape(24.dp)
    val (bg, fg, border) = when (style) {
        PillStyle.PRIMARY -> Triple(c.text, c.bg, c.text)
        PillStyle.SECONDARY -> Triple(Color.Transparent, c.text, c.textTertiary)
        PillStyle.DESTRUCTIVE -> Triple(c.error, c.bg, c.error)
        PillStyle.ACCENT -> Triple(c.accent, c.onAccent, c.accent)
    }
    Box(
        modifier
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = if (style == PillStyle.SECONDARY) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                color = fg,
                maxLines = 1,
            )
        }
    }
}
