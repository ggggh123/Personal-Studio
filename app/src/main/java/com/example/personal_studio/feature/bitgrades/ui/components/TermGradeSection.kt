package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.CreditFormat
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

@Composable
fun TermGradeSection(
    term: TermGrades,
    initiallyExpanded: Boolean = false,
    excludedIds: Set<Long> = emptySet(),
    onToggleCourse: (Long) -> Unit = {},
    onToggleTerm: (List<Long>) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val credits = term.courses.sumOf { it.credit }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = term.courses.none { it.id in excludedIds },
                onCheckedChange = { onToggleTerm(term.courses.map { c -> c.id }) },
                colors = CheckboxDefaults.colors(checkedColor = Phosphor, uncheckedColor = FoamMute),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(4.dp))
            Row(Modifier.weight(1f).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "▾ ${term.termName}" else "▸ ${term.termName}",
                    color = Phosphor, maxLines = 1,
                )
                Text(
                    "  ${term.courses.size}门·${CreditFormat.format(credits)}学分",
                    color = FoamMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "GPA ${String.format(Locale.US, "%.2f", term.weightedGpa)}",
                    color = FoamMute, maxLines = 1, softWrap = false,
                )
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                term.courses.forEach { c ->
                    var courseExpanded by remember(c.courseCode + c.score) { mutableStateOf(false) }
                    val excluded = c.id in excludedIds
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = !excluded,
                                onCheckedChange = { onToggleCourse(c.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Phosphor, uncheckedColor = FoamMute),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Row(
                                Modifier.weight(1f).clickable { courseExpanded = !courseExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text((if (courseExpanded) "▾ " else "▸ ") + c.courseName,
                                    color = if (!c.isPass) Carmine else if (excluded) FoamDim else Foam,
                                    modifier = Modifier.weight(1f))
                                Text(CreditFormat.format(c.credit), color = FoamMute,
                                    modifier = Modifier.width(40.dp))
                                Text(c.score, color = if (!c.isPass) Carmine else if (excluded) FoamDim else Foam,
                                    modifier = Modifier.width(48.dp))
                                if (!c.isPass) Text("⚠挂科", color = Carmine)
                                else if (c.attemptType != "正常") Text(c.attemptType, color = Amber)
                            }
                        }
                        AnimatedVisibility(courseExpanded) {
                            val parts = buildList {
                                c.courseAvg?.let { add("平均分 " + String.format(Locale.US, "%.1f", it)) }
                                c.classRankText?.let { add("班级 $it") }
                                c.majorRankText?.let { add("专业 $it") }
                            }
                            Text(
                                if (parts.isEmpty()) "无详情" else parts.joinToString("  ·  "),
                                color = if (parts.isEmpty()) FoamDim else Cyan,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 28.dp, top = 1.dp, bottom = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
