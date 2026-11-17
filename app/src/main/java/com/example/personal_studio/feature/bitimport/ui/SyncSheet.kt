package com.example.personal_studio.feature.bitimport.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitimport.model.SyncAllProgress
import com.example.personal_studio.domain.bitimport.model.SyncFailure
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.domain.bitimport.model.SyncSourceState
import com.example.personal_studio.domain.bitimport.model.SyncSourceStatus
import com.example.personal_studio.feature.timeline.today.DetailAction
import com.example.personal_studio.feature.timeline.today.PillButton
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion

/** Wording of the sync panel, kept apart from the composables so it is tested. */
object SyncText {
    fun name(src: SyncSource): String = when (src) {
        SyncSource.COURSES -> "课表"
        SyncSource.DDL -> "作业"
        SyncSource.EXAMS -> "考试"
        SyncSource.GRADES -> "成绩"
    }

    fun running(src: SyncSource): String = when (src) {
        SyncSource.COURSES -> "登录教学中心，读取本学期课表"
        SyncSource.DDL -> "读取乐学和新乐学作业"
        SyncSource.EXAMS -> "登录教学中心，读取考试安排"
        SyncSource.GRADES -> "登录教务系统，读取成绩表"
    }

    /** What a source brings in, shown before the first sync. */
    fun what(src: SyncSource): String = when (src) {
        SyncSource.COURSES -> "本学期课表"
        SyncSource.DDL -> "乐学和新乐学的作业"
        SyncSource.EXAMS -> "考试安排"
        SyncSource.GRADES -> "全部学期的成绩"
    }

    fun ok(src: SyncSource): String = when (src) {
        SyncSource.COURSES -> "本学期课表 · 已更新到日程"
        SyncSource.DDL -> "乐学和新乐学的作业 · 已写入日程"
        SyncSource.EXAMS -> "考试安排 · 已写入日程"
        SyncSource.GRADES -> "成绩单已更新"
    }

    fun failure(reason: SyncFailure?): String = when (reason) {
        SyncFailure.VERIFY -> "学校要求验证，其余各项不受影响"
        SyncFailure.PASSWORD -> "学校没有接受保存的密码，请重新登录"
        SyncFailure.LOCKED -> "账户被学校暂时锁定，请稍后再试"
        SyncFailure.REVIEW -> "需要先在教务系统完成评教"
        SyncFailure.NETWORK -> "连不上学校服务器，请检查网络后重试"
        SyncFailure.EMPTY -> "学校返回为空，本机数据未改动"
        SyncFailure.OTHER, null -> "学校返回了意外的内容，稍后再试"
    }

    /** Failures fixed by signing in again (the login page opens the school's own verification). */
    fun needsLogin(reason: SyncFailure?): Boolean = reason == SyncFailure.VERIFY || reason == SyncFailure.PASSWORD

    fun subtitle(progress: SyncAllProgress?, lastSyncAt: Long?, now: Long): String {
        if (progress == null || progress.noCredentials) {
            return lastSyncAt?.let { "上次同步 · ${TodayText.ago(it, now)}" } ?: "还没有同步过"
        }
        progress.running?.let { return "第 ${it.ordinal + 1} 项，共 ${SyncSource.values().size} 项" }
        if (!progress.done) return "准备中"
        val failed = progress.failed.size
        return if (failed == 0) "全部完成 · ${lastSyncAt?.let { TodayText.ago(it, now) } ?: "刚刚"}"
        else "${SyncSource.values().size - failed} 项完成 · $failed 项需要处理"
    }
}

/** The four sources with their live state; failed ones say why and how to fix it. */
@Composable
fun SyncSourceRows(progress: SyncAllProgress?, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tg.colors
    Column(modifier) {
        SyncSource.values().forEachIndexed { i, src ->
            val state = progress?.states?.get(src) ?: SyncSourceState(SyncSourceStatus.PENDING)
            val idle = progress == null
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .drawBehind { if (i > 0) drawLine(c.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusIcon(state.status)
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(SyncText.name(src), style = MaterialTheme.typography.bodyLarge, color = c.text)
                    val detail = when (state.status) {
                        SyncSourceStatus.PENDING -> if (idle) SyncText.what(src) else "等待中"
                        SyncSourceStatus.RUNNING -> SyncText.running(src)
                        SyncSourceStatus.OK -> SyncText.ok(src)
                        SyncSourceStatus.FAILED -> SyncText.failure(state.failure)
                    }
                    AnimatedContent(detail, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "syncDetail") {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                when {
                    state.status == SyncSourceStatus.OK && state.detail != null ->
                        Text(state.detail, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    state.status == SyncSourceStatus.FAILED && SyncText.needsLogin(state.failure) ->
                        SmallPill(if (state.failure == SyncFailure.VERIFY) "去验证" else "重新登录", onLogin)
                }
            }
        }
    }
}

@Composable
private fun SmallPill(label: String, onClick: () -> Unit) {
    val c = Tg.colors
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.text)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.bg)
    }
}

/** Ring (waiting), spinning arc (running), drawn tick (done), "!" (failed). */
@Composable
private fun StatusIcon(status: SyncSourceStatus) {
    val c = Tg.colors
    AnimatedContent(
        targetState = status,
        transitionSpec = { (fadeIn(tween(200)) + scaleIn(TgMotion.fast(), initialScale = 0.7f)) togetherWith fadeOut(tween(120)) },
        label = "syncIcon",
        modifier = Modifier.size(28.dp),
    ) { s ->
        when (s) {
            SyncSourceStatus.PENDING -> Canvas(Modifier.size(28.dp)) {
                drawCircle(c.textTertiary, radius = size.minDimension / 2 - 3.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
            SyncSourceStatus.RUNNING -> {
                val spin = rememberInfiniteTransition(label = "spin")
                val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
                Canvas(Modifier.size(28.dp)) {
                    val stroke = 2.dp.toPx()
                    val inset = 3.dp.toPx()
                    drawCircle(c.line, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
                    drawArc(
                        c.accent, startAngle = angle - 90f, sweepAngle = 100f, useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            SyncSourceStatus.OK -> {
                val draw = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(Unit) { draw.animateTo(1f, tween(450)) }
                Canvas(Modifier.size(28.dp)) {
                    drawCircle(c.accentSoft)
                    val s = size.width / 28f
                    val path = Path().apply {
                        moveTo(8.6f * s, 14.5f * s)
                        lineTo(12.4f * s, 18.2f * s)
                        lineTo(19.6f * s, 10f * s)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * draw.value, part, true)
                    drawPath(part, c.accent, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            SyncSourceStatus.FAILED -> Box(Modifier.size(28.dp).background(c.text, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", style = MaterialTheme.typography.titleMedium, color = c.bg)
            }
        }
    }
}

/**
 * The "同步全部" panel: rises over everything (bottom bar included), shows each source live,
 * and offers "只重试失败项" when some failed. Closing it does not stop the sync.
 */
@Composable
fun SyncSheet(
    visible: Boolean,
    progress: SyncAllProgress?,
    running: Boolean,
    lastSyncAt: Long?,
    now: Long,
    onStart: () -> Unit,
    onRetryFailed: () -> Unit,
    onLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "关闭同步面板", onClick = onDismiss),
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
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = "同步全部" }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(c.line, RoundedCornerShape(2.dp)))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("同步全部", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.weight(1f))
                    AnimatedContent(SyncText.subtitle(progress, lastSyncAt, now), transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "syncSub") {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                if (progress?.noCredentials == true) {
                    Text(
                        "还没有保存学校账户。登录后，课表、作业、考试和成绩可以一次同步。",
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(DetailAction("关闭", primary = false, onClick = onDismiss), Modifier.weight(1f))
                        PillButton(DetailAction("去登录", primary = true, onClick = onLogin), Modifier.weight(1f))
                    }
                    return@Column
                }
                SyncSourceRows(progress, onLogin = onLogin, modifier = Modifier.padding(top = 10.dp))
                if (running) {
                    Text(
                        "依次同步；某一项失败不影响其余各项。关闭面板不会中断同步。",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    val failed = progress?.failed.orEmpty()
                    val partial = failed.isNotEmpty() && failed.size < SyncSource.values().size
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            progress == null -> {
                                PillButton(DetailAction("关闭", primary = false, onClick = onDismiss), Modifier.weight(1f))
                                PillButton(DetailAction("开始同步", primary = true, onClick = onStart), Modifier.weight(1f))
                            }
                            partial -> {
                                PillButton(DetailAction("只重试失败项", primary = false, onClick = onRetryFailed), Modifier.weight(1f))
                                PillButton(DetailAction("完成", primary = true, onClick = onDismiss), Modifier.weight(1f))
                            }
                            else -> {
                                PillButton(DetailAction("重新同步全部", primary = false, onClick = onStart), Modifier.weight(1f))
                                PillButton(DetailAction("完成", primary = true, onClick = onDismiss), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
