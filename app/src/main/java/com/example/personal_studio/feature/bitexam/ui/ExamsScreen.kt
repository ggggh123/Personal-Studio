package com.example.personal_studio.feature.bitexam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitexam.ExamRow
import com.example.personal_studio.feature.bitexam.ExamsEvent
import com.example.personal_studio.feature.bitexam.ExamsViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Dim
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExamsScreen(onBack: () -> Unit, onNeedLogin: () -> Unit, vm: ExamsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showPast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { if (it is ExamsEvent.NeedLogin) onNeedLogin() }
    }

    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(cornerDim = 0.42f, centerGlow = 0.03f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onBack) { Text("←", color = FoamMute) }
                Text("$ exams", color = Cyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::onRefresh, enabled = !st.syncing) {
                    Text(if (st.syncing) "同步中…" else "↻ 刷新", color = if (st.syncing) FoamDim else Phosphor)
                }
            }
            st.error?.let { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.height(10.dp))

            if (st.upcoming.isEmpty() && st.past.isEmpty()) {
                Text(
                    "还没有考试安排 —— 点右上角刷新,或学校尚未发布",
                    color = FoamDim, style = MaterialTheme.typography.labelMedium,
                )
                return@Column
            }

            val upMin = st.upcoming.minOfOrNull { it.startAt }
            val upMax = st.upcoming.maxOfOrNull { it.startAt }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(st.upcoming, key = { it.id }) { row ->
                    ExamCard(row, past = false, fraction = seasonFraction(row.startAt, upMin, upMax))
                }
                if (st.past.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showPast = !showPast }) {
                            Text((if (showPast) "▾ 收起" else "▸ 已考") + " (${st.past.size})", color = FoamMute)
                        }
                    }
                    if (showPast) items(st.past, key = { it.id }) { row -> ExamCard(row, past = true) }
                }
            }
        }
    }
}

@Composable
private fun ExamCard(row: ExamRow, past: Boolean, fraction: Float = 0f) {
    val remaining = row.startAt - System.currentTimeMillis()
    val accent = when {
        past -> FoamDim
        remaining in 0..DAY_MS -> Amber // 24 小时内:琥珀色提醒
        else -> Cyan
    }
    Column(
        Modifier.fillMaxWidth()
            .border(1.dp, if (past) Dim else Rule)
            .background(if (past) Void else Deep)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.course,
                color = if (past) FoamDim else Foam,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            if (past) {
                Text("已结束", color = FoamDim, style = MaterialTheme.typography.labelMedium)
            } else {
                Text(countdown(remaining), color = accent, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                CountdownBar(fraction = fraction, color = accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoLine("时间", timeRange(row.startAt, row.endAt), past)
        row.location?.let { InfoLine("地点", it, past) }
        SeatInvigilatorLine(row.seat, row.invigilator, past)
    }
}

@Composable
private fun InfoLine(label: String, value: String, past: Boolean) {
    Row(Modifier.padding(top = 3.dp)) {
        Text(
            label,
            color = if (past) Dim else FoamDim,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(38.dp),
        )
        Text(value, color = if (past) FoamDim else FoamMute, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SeatInvigilatorLine(seat: String?, invigilator: String?, past: Boolean) {
    if (seat == null && invigilator == null) return
    val labelColor = if (past) Dim else FoamDim
    val valueColor = if (past) FoamDim else FoamMute
    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (seat != null) {
            Text("座位", color = labelColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
            Text(seat, color = valueColor, style = MaterialTheme.typography.bodySmall)
        }
        if (invigilator != null) {
            if (seat != null) Spacer(Modifier.width(18.dp))
            Text("监考", color = labelColor, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(38.dp))
            Text(invigilator, color = valueColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 倒计时进度条:fraction 越大填充越满,颜色随紧迫度(Cyan / 24h 内 Amber)。 */
@Composable
private fun CountdownBar(fraction: Float, color: Color) {
    Box(Modifier.width(44.dp).height(4.dp).background(Rule)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

private const val DAY_MS = 86_400_000L

/**
 * 相对「考试季」的紧迫度:最近一场=满,最远一场≈15%,其余线性渐变,单场居中。
 * 比固定时间窗更实用 —— 考试普遍提前数周公布,绝对窗口会让所有条都空或都满。
 */
private fun seasonFraction(start: Long, min: Long?, max: Long?): Float {
    if (min == null || max == null || max <= min) return 0.55f
    val rel = 1f - (start - min).toFloat() / (max - min)
    return (0.15f + rel * 0.85f).coerceIn(0.15f, 1f)
}

private fun countdown(remaining: Long): String {
    if (remaining <= 0) return "即将"
    val min = remaining / 60_000
    return when {
        min < 60 -> "${min}分"
        min < 1440 -> "${min / 60}小时"
        else -> "${min / 1440}天"
    }
}

private fun timeRange(start: Long, end: Long?): String {
    val d = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(start))
    val e = end?.let { SimpleDateFormat("HH:mm", Locale.US).format(Date(it)) }
    return if (e != null) "$d~$e" else d
}
