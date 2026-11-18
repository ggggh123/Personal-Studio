package com.example.personal_studio.ui.shell

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion

/** A bottom-bar destination of the 天光 shell. */
data class TgTab(val route: String, val label: String, val icon: ImageVector)

val TgTabs = listOf(
    TgTab(NavRoutes.TIMELINE, "今日", Icons.Outlined.WbSunny),
    TgTab(NavRoutes.CHAT, "问答", Icons.Outlined.ChatBubbleOutline),
    TgTab(NavRoutes.LIBRARY, "资料", Icons.Outlined.AutoStories),
    TgTab(NavRoutes.PROFILE, "校园", Icons.Outlined.AccountBalance),
)

/** Title row of a tab page: serif title on the left, account and settings on the right. */
@Composable
fun TgTopBar(title: String, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = c.text, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "设置", onClick = onOpenSettings)
                .semantics { contentDescription = "设置" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(36.dp).background(c.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * 今日 · 问答 · ＋ · 资料 · 校园. The active tab gets the text colour, a medium label and an
 * accent dot; the ＋ rotates into × while the new-item sheet is open.
 */
@Composable
fun TgBottomBar(
    selectedRoute: String?,
    plusOpen: Boolean,
    onTabClick: (TgTab) -> Unit,
    onPlusClick: () -> Unit,
) {
    val c = Tg.colors
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TgTabs.take(2).forEach { TabItem(it, it.route == selectedRoute, onTabClick) }
            val rotation by animateFloatAsState(if (plusOpen) 45f else 0f, TgMotion.fast(), label = "plus")
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = if (plusOpen) "关闭新建面板" else "新建") {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onPlusClick()
                    }
                    .semantics { contentDescription = if (plusOpen) "关闭新建面板" else "新建" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(52.dp).background(c.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onAccent,
                        modifier = Modifier.size(26.dp).rotate(rotation))
                }
            }
            TgTabs.drop(2).forEach { TabItem(it, it.route == selectedRoute, onTabClick) }
        }
    }
}

@Composable
private fun TabItem(tab: TgTab, selected: Boolean, onClick: (TgTab) -> Unit) {
    val c = Tg.colors
    val tint = if (selected) c.text else c.textSecondary
    val dot by animateDpAsState(if (selected) 4.dp else 0.dp, TgMotion.fast(), label = "dot")
    Column(
        modifier = Modifier
            .width(64.dp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Tab) { onClick(tab) }
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = tint,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(dot).background(c.accent, CircleShape))
    }
}

/** One entry of the ＋ sheet. */
data class NewItemAction(val label: String, val hint: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The ＋ sheet: scan, photo question, task, course. Rises with the standard spring over a
 * scrim; tiles follow one by one; leaves with the faster exit curve. Sits under the bottom
 * bar so the ＋ keeps working as the close button.
 */
@Composable
fun NewItemSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onPhotoQuestion: () -> Unit,
    onNewTask: () -> Unit,
    onNewCourse: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val c = Tg.colors
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭新建面板",
                    onClick = onDismiss,
                ),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(TgMotion.standard<IntOffset>()) { it },
            exit = slideOutVertically(TgMotion.exit<IntOffset>()) { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.surfaceRaised)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = bottomInset + 16.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                        .background(c.line, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("新建", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.weight(1f))
                    Text("拍下来，问出来，记下来", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Spacer(Modifier.height(16.dp))
                val actions = listOf(
                    NewItemAction("扫描文档", "拍照、矫正、导出 PDF", Icons.Outlined.DocumentScanner, onScan),
                    NewItemAction("拍题提问", "拍下题目，直接问 AI", Icons.Outlined.PhotoCamera, onPhotoQuestion),
                    NewItemAction("新建待办", "截止前自动提醒", Icons.Outlined.TaskAlt, onNewTask),
                    NewItemAction("添加课程", "手动添加，或从教务导入", Icons.AutoMirrored.Outlined.MenuBook, onNewCourse),
                )
                actions.chunked(2).forEachIndexed { row, pair ->
                    if (row > 0) Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { col, action ->
                            val index = row * 2 + col
                            Box(
                                Modifier
                                    .weight(1f)
                                    .animateEnterExit(
                                        enter = fadeIn(tween(240, delayMillis = 60 + index * 50)) +
                                            slideInVertically(TgMotion.standard<IntOffset>()) { it / 4 },
                                        exit = fadeOut(tween(120)),
                                    ),
                            ) {
                                NewItemTile(action)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewItemTile(action: NewItemAction) {
    val c = Tg.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.bg)
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(action.icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(action.label, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(action.hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}
