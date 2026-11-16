package com.example.personal_studio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/**
 * Knob travel: bouncier than the fast tier (damping 0.8 overshoots ~0.3 dp, invisible) so the
 * flip reads as a physical click — about 2.5 dp past the end, then settles.
 */
private val KnobSpring = spring<Dp>(dampingRatio = 0.55f, stiffness = 520f)

/** 天光 switch: 48 × 28 track in the accent when on; the knob springs across with a visible bounce. */
@Composable
fun TgSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Tg.colors
    val track by animateColorAsState(if (checked) c.accent else c.textTertiary, tween(250), label = "track")
    val knob by animateColorAsState(if (checked) c.onAccent else c.surfaceRaised, tween(250), label = "knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, KnobSpring, label = "knobX")
    Box(
        modifier
            .size(56.dp, 44.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp, 28.dp).background(track, RoundedCornerShape(14.dp))) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .offset { IntOffset(x.roundToPx(), 0) }
                    .size(22.dp)
                    .background(knob, CircleShape),
            )
        }
    }
}
