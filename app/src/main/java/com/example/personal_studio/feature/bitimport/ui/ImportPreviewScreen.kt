package com.example.personal_studio.feature.bitimport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.local.db.entity.TimelineItemEntity
import com.example.personal_studio.feature.bitgrades.ui.fullTermName
import com.example.personal_studio.feature.bitimport.ImportViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.theme.Tg

private val Weekdays = arrayOf("一", "二", "三", "四", "五", "六", "日")

/** "周三 第 3–5 节" for an imported course occurrence. */
internal fun importSlotText(item: TimelineItemEntity): String {
    val day = item.weekdayCode?.let { Weekdays.getOrNull(it - 1) }?.let { "周$it" } ?: ""
    val start = item.periodIndex
    val end = item.periodEndIndex
    val periods = when {
        start == null -> ""
        end == null || end == start -> "第 $start 节"
        else -> "第 $start–$end 节"
    }
    return listOf(day, periods).filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * Last step: the term, how many courses and classes will be written, what gets replaced,
 * and every course with its teacher and slot. Nothing is written until 确认导入.
 */
@Composable
fun ImportPreviewScreen(
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    val courses = remember(ui.previewItems) { ui.previewItems.distinctBy { it.seriesId } }
    val term = ui.previewTerm?.let { it.display ?: fullTermName(it.code) } ?: "当前学期"

    WizardScaffold(
        stepNumber = 3, totalSteps = 3,
        title = "导入课表",
        caption = "确认后写入",
        onBack = null,
        primaryLabel = if (ui.writing) "正在写入…" else "确认导入",
        onPrimary = vm::onConfirm,
        primaryEnabled = ui.previewItems.isNotEmpty() && !ui.writing,
        secondaryLabel = "取消",
        onSecondary = vm::onCancel,
    ) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "summary") {
                Column(Modifier.enterUp(true, 0)) {
                    Text(term, style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text("${courses.size} 门课 · 共 ${ui.previewItems.size} 次课", style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (ui.countToReplace > 0) {
                item(key = "replace") {
                    Text(
                        "会替换这个学期之前导入的 ${ui.countToReplace} 次课；自己添加的课程和其他学期不受影响。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.text,
                        modifier = Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(14.dp)).padding(14.dp).enterUp(true, 40),
                    )
                }
            }
            item(key = "list") {
                TgCard(Modifier.enterUp(true, 80)) {
                    courses.forEachIndexed { i, item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(item.instructor?.takeIf { it.isNotBlank() }, importSlotText(item).takeIf { it.isNotEmpty() }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        if (i < courses.lastIndex) {
                            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(c.line))
                        }
                    }
                }
            }
        }
    }
}
