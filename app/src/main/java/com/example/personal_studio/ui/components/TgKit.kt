package com.example.personal_studio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.timeline.today.pressScale
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion

/*
 * Small building blocks shared by the 天光 pages: cards, filter chips, form fields, setting
 * rows, group labels, empty states and the busy overlay. Each one reads [Tg.colors] so it
 * follows the daylight palette; none of them owns state.
 */

/** A rounded card on the page: surface fill, hairline border, 20 dp corners. Pressable when [onClick] is set. */
@Composable
fun TgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tg.colors
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = null, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                } else Modifier,
            ),
        content = content,
    )
}

/** Small grey label above a group of rows or cards ("最近", "外观"). */
@Composable
fun TgGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
        color = Tg.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * A selectable pill: filled with the text colour when selected, outlined otherwise. An
 * optional [count] follows the label in a lighter weight.
 */
@Composable
fun TgChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Tg.colors
    val shape = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (selected) c.text else Color.Transparent, tween(200), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.bg else c.text, tween(200), label = "chipFg")
    val bd by animateColorAsState(if (selected) c.text else c.textTertiary, tween(200), label = "chipBd")
    Row(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, bd, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = fg, maxLines = 1)
        if (count != null) {
            Text("$count", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

/**
 * A rounded text field: page-coloured fill, hairline border that turns accent while focused
 * and error-coloured when [error] is set. [label] sits above, [error] or [hint] below.
 */
@Composable
fun TgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Tg.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            error != null -> c.error
            focused -> c.accent
            else -> c.line
        },
        TgMotion.fast(),
        label = "fieldBorder",
    )
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(c.bg)
                .border(1.dp, border, shape)
                .padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) c.text else c.textSecondary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
                modifier = Modifier.weight(1f).padding(vertical = 13.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary.copy(alpha = 0.7f), maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                        }
                        inner()
                    }
                },
            )
            if (trailing != null) Box(Modifier.align(Alignment.CenterVertically)) { trailing() }
        }
        val note = error ?: hint
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) c.error else c.textSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

/**
 * One row of a settings card: optional icon in a soft circle, title and summary, and either
 * [trailing] (a switch, a value) or a chevron when the row opens something. Rows after the
 * first draw a hairline above themselves.
 */
@Composable
fun TgSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    first: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Tg.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                if (!first) drawLine(c.line, Offset(16.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
            }
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(36.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) c.text else c.textSecondary)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** A short value shown at the end of a [TgSettingRow] ("天光", "已配置"). */
@Composable
fun TgRowValue(text: String, accent: Boolean = false) {
    val c = Tg.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) c.accent else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.padding(start = 2.dp).size(20.dp))
    }
}

/** Serif title, a sentence of explanation and an optional action, for lists with nothing in them. */
@Composable
fun TgEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Tg.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
        if (actionLabel != null && onAction != null) {
            TgPillButton(actionLabel, onClick = onAction, icon = actionIcon, style = PillStyle.ACCENT, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

/**
 * Covers the screen while a slow operation runs: the scrim, and a raised card with [label]
 * and a breathing dot. Swallows touches so a second operation cannot start.
 */
@Composable
fun TgBusyOverlay(label: String, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Box(
        modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(c.surfaceRaised)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text)
            BlinkingCursor()
        }
    }
}

/** A thin rounded progress track in the accent colour; [fraction] is clamped to 0..1. */
@Composable
fun TgProgressTrack(fraction: Float, modifier: Modifier = Modifier, color: Color = Tg.colors.accent) {
    val c = Tg.colors
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(c.line)
            .drawBehind { drawRect(color, size = size.copy(width = size.width * f)) },
    )
}
