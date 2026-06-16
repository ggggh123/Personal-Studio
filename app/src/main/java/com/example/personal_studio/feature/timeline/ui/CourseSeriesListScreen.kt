package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.CourseSeriesSummary
import com.example.personal_studio.feature.timeline.vm.CourseSeriesListViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void

/**
 * 设置 → 课程列表。课程管理中心:新建([+新建课程])/编辑(点行进编辑页)/删除(编辑页内)。
 * 头部对齐 chat/scanner 的 user@study 提示;每行三段式显示 课名 / 星期·节次·周次·学分 / 老师·地点。
 */
@Composable
fun CourseSeriesListScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onAddCourse: () -> Unit,
    vm: CourseSeriesListViewModel = hiltViewModel(),
) {
    val series by vm.series.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Void)) {
        TerminalTopBar(route = "courses", subtitle = "# 课程管理", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        // 内联头部:user@study:~$ ls courses/ + [+新建课程] + total N(对齐 scanner)
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Amber)) { append("user@study") }
                        withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                        withStyle(SpanStyle(color = Foam)) { append("ls courses/") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "[+新建课程]",
                    color = Cyan,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onAddCourse).padding(4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("total ${series.size}", style = MaterialTheme.typography.bodySmall, color = FoamMute)
        }
        Spacer(Modifier.height(14.dp))
        if (series.isEmpty()) {
            CourseEmptyState(onAddCourse = onAddCourse)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(series, key = { it.seriesId }) { s ->
                    CourseRow(s, onClick = { onOpenSeries(s.seriesId) })
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                }
            }
        }
    }
}

/** 三段式行:▸ 课名 / 星期·节次·周次·共N节·学分 / 老师·地点(都空则省第三行)。整行可点进编辑页。 */
@Composable
private fun CourseRow(s: CourseSeriesSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("▸ ") }
                withStyle(SpanStyle(color = Phosphor)) { append(s.title) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatCourseSchedule(
                weekdays = s.weekdays,
                periodStart = s.periodStart,
                periodEnd = s.periodEnd,
                minWeek = s.minWeek,
                maxWeek = s.maxWeek,
                occurrenceCount = s.occurrenceCount,
                credits = s.credits,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = FoamMute,
        )
        val who = listOfNotNull(
            s.instructor?.takeIf { it.isNotBlank() },
            s.location?.takeIf { it.isNotBlank() },
        )
        if (who.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(who.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = FoamDim)
        }
    }
}

/** 空态:对齐 chat/scanner(`# 暂无课程` + `▓ 点 [新建课程] 或 day 界面的 [+] 录入` + 闪烁光标)。 */
@Composable
private fun CourseEmptyState(onAddCourse: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("# 暂无课程", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("点 ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[新建课程]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" 或 day 界面的 [+] 录入") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onAddCourse),
            )
            BlinkingCursor()
        }
    }
}
