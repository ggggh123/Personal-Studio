package com.example.personal_studio.feature.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.ChatImageThumbnail
import com.example.personal_studio.ui.components.ChatImageThumbnailSmall
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion

/** The user's turn: a right-aligned raised bubble, images above the text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserBubble(text: String, images: List<String>, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Column(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.End) {
        if (images.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 48.dp, bottom = if (text.isNotBlank()) 6.dp else 0.dp),
            ) {
                images.forEach { ChatImageThumbnail(path = it, maxHeight = if (images.size == 1) 200.dp else 120.dp) }
            }
        }
        if (text.isNotBlank()) {
            val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
                modifier = Modifier
                    .padding(start = 48.dp)
                    .clip(shape)
                    .background(c.surfaceRaised)
                    .border(1.dp, c.line, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The model's turn: no bubble, the answer sits on the page. A small model line above;
 * time / tokens and the actions below once it is finished.
 */
@Composable
fun AiAnswer(
    model: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    onSave: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = Tg.colors
    Column(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(c.accent, CircleShape))
            Text(model, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(top = 6.dp)) { content() }
        if (footer != null || onSave != null || onCopy != null) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (footer != null) {
                    Text(footer, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                }
                onCopy?.let { SmallAction(Icons.Outlined.ContentCopy, "复制", it) }
                onSave?.let { SmallAction(Icons.Outlined.BookmarkAdd, "存入知识库", it) }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Tg.colors
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(15.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

/** Error above the input, dismissible. */
@Composable
fun ChatErrorNote(message: String?, onDismiss: () -> Unit) {
    val c = Tg.colors
    AnimatedVisibility(visible = message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.accentSoft)
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("出错了：${message.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "关闭", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = c.textSecondary, modifier = Modifier.size(16.dp)) }
        }
    }
}

/**
 * The input: images waiting to be sent, then a rounded field with the attach button on the
 * left and a round send button on the right (accent when there is something to send,
 * a stop square while the answer streams).
 */
@Composable
fun ChatComposer(
    input: String,
    images: List<String>,
    sending: Boolean,
    canAttach: Boolean,
    onInput: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = Tg.colors
    val canSend = input.isNotBlank() || images.isNotEmpty()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
        AnimatedVisibility(images.isNotEmpty(), enter = expandVertically(TgMotion.standard()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                images.forEach { p ->
                    Box {
                        ChatImageThumbnailSmall(path = p)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(22.dp)
                                .background(c.text, CircleShape)
                                .clickable(role = Role.Button, onClickLabel = "移除图片") { onRemoveImage(p) }
                                .semantics { contentDescription = "移除图片" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, contentDescription = null, tint = c.bg, modifier = Modifier.size(13.dp)) }
                    }
                }
            }
        }
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            RoundIcon(
                icon = Icons.Rounded.Add,
                label = "添加图片",
                bg = Color.Transparent,
                tint = if (canAttach) c.textSecondary else c.textTertiary,
                enabled = canAttach,
                onClick = onAttach,
            )
            BasicTextField(
                value = input,
                onValueChange = onInput,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 5,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(horizontal = 6.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) Text("问点什么…", style = MaterialTheme.typography.bodyLarge, color = c.textTertiary)
                        inner()
                    }
                },
            )
            val bg by animateColorAsState(
                when { sending -> c.text; canSend -> c.accent; else -> c.line }, tween(200), label = "sendBg",
            )
            AnimatedContent(
                targetState = sending,
                transitionSpec = { (fadeIn(tween(160)) + scaleIn(TgMotion.fast(), initialScale = 0.7f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.7f)) },
                label = "sendIcon",
            ) { isSending ->
                if (isSending) {
                    RoundIcon(Icons.Rounded.Stop, "停止生成", bg = bg, tint = c.bg, onClick = onStop)
                } else {
                    RoundIcon(Icons.Rounded.ArrowUpward, "发送", bg = bg, tint = if (canSend) c.onAccent else c.textTertiary, enabled = canSend, onClick = onSend)
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Shown in the answer slot before the first token: a rotating line, seconds waited, a breathing dot. */
@Composable
fun ThinkingLine(phrase: String, seconds: Int, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Row(modifier.widthIn(max = 520.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedContent(phrase, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "phrase") {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
        }
        Text("${seconds}s", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        BlinkingCursor()
    }
}
