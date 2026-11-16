package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

private val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

/** Seven round toggles, Monday first; codes are 1 (Monday) to 7 (Sunday). */
@Composable
fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        WeekdayNames.forEachIndexed { i, name ->
            val code = i + 1
            val on = code in selected
            val bg by animateColorAsState(if (on) c.text else Color.Transparent, tween(180), label = "dayBg")
            val fg by animateColorAsState(if (on) c.bg else c.text, tween(180), label = "dayFg")
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (on) c.text else c.textTertiary, CircleShape)
                    .clickable(role = Role.Checkbox, onClickLabel = "周$name") { onToggle(code) },
                contentAlignment = Alignment.Center,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = fg)
            }
        }
    }
}

/** "开始节次  − 3 +": a label and a compact stepper clamped to [min]..[max]. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unit: (Int) -> String = { "$it" },
) {
    val c = Tg.colors
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        StepButton(Icons.Rounded.Remove, "减少$label", enabled = value > min) { onChange(value - 1) }
        Text(
            unit(value), style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 4.dp),
        )
        StepButton(Icons.Rounded.Add, "增加$label", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Tg.colors
    Box(
        Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .border(1.dp, c.textTertiary, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
    }
}

/** A tappable value box ("11 月 18 日", "23:59"), outlined, or with a placeholder when [text] is null. */
@Composable
fun ValueBox(text: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val c = Tg.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(c.bg)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text ?: placeholder, style = MaterialTheme.typography.bodyLarge, color = if (text != null) c.text else c.textSecondary)
    }
}
