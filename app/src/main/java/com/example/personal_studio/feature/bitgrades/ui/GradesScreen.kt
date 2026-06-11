package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.charts.GpaLineChart
import com.example.personal_studio.core.charts.GradeBarChart
import com.example.personal_studio.core.charts.LinePoint
import com.example.personal_studio.core.util.GradeBucketer
import com.example.personal_studio.feature.bitgrades.GradesViewModel
import com.example.personal_studio.feature.bitgrades.ui.components.GpaOverviewCard
import com.example.personal_studio.feature.bitgrades.ui.components.TermGradeSection
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun GradesScreen(
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenWhatIf: () -> Unit,
    onOpenShare: () -> Unit,
    vm: GradesViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Void)
        .systemBarsPadding()                    // 同时让出顶部状态栏 + 底部导航栏
        .padding(16.dp)
        .verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ transcript", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (st.loggedIn) vm.onSyncNow() else onNeedLogin() }) {
                Text(if (st.syncing) "同步中…" else "↻ 同步")
            }
        }

        // 内联同步进度
        st.syncSteps.forEach {
            Text("> $it", color = Phosphor, style = MaterialTheme.typography.labelMedium)
        }
        st.syncError?.let { err ->
            Text("⚠ ${err.userMessage()}", color = Carmine, style = MaterialTheme.typography.labelMedium)
        }

        if (st.book.isEmpty) {
            Spacer(Modifier.height(48.dp))
            Text("还没有成绩数据", color = FoamMute)
            Button(
                onClick = { if (st.loggedIn) vm.onSyncNow() else onNeedLogin() },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("从教务系统查询成绩") }
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        Panel {
            GpaOverviewCard(
                gpa = if (st.filtering) st.selectedGpa else st.book.overallGpa,
                avgScore = if (st.filtering) st.selectedAvgScore else st.book.overallAvgScore,
                credits = if (st.filtering) st.selectedCredits else st.book.totalCredits,
                peerGpa = if (st.filtering) st.selectedPeerAvgGpa else st.book.overallPeerAvgGpa,
                peerAvgScore = if (st.filtering) st.selectedPeerAvgScore else st.book.overallPeerAvgScore,
                rankEst = st.book.overallMajorRankEst,
                filtering = st.filtering,
            )
            if (st.filtering) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "选中 ${st.selectedCount} 门",
                        color = Amber, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::onClearSelection) { Text("全选", color = FoamMute) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Panel {
            Text("GPA 趋势", color = FoamMute)
            val points = st.book.terms.reversed().map { LinePoint(shortTerm(it.termName), it.weightedGpa) }
            GpaLineChart(points)
        }

        Spacer(Modifier.height(8.dp))
        Panel {
            Text(if (st.filtering) "成绩分布(选中)" else "成绩分布", color = FoamMute)
            val allCourses = st.book.terms.flatMap { it.courses }
            val courses = if (st.filtering) allCourses.filter { it.id !in st.excludedIds } else allCourses
            GradeBarChart(GradeBucketer.bucket(courses))
        }

        Spacer(Modifier.height(8.dp))
        st.book.terms.forEachIndexed { i, t ->
            TermGradeSection(
                t,
                initiallyExpanded = i == 0,
                excludedIds = st.excludedIds,
                onToggleCourse = vm::onToggleCourse,
                onToggleTerm = vm::onToggleTerm,
            )
        }

        Spacer(Modifier.height(20.dp))
        Button({ showSheet = true; vm.onAnalyze() }, Modifier.fillMaxWidth()) { Text("生成 AI 分析") }
        Spacer(Modifier.height(8.dp))
        Button(onOpenWhatIf, Modifier.fillMaxWidth()) { Text("目标 GPA 计算器") }
        Spacer(Modifier.height(8.dp))
        Button(onOpenShare, Modifier.fillMaxWidth()) { Text("↗ 分享成绩单") }
    }

    if (showSheet) {
        AiAnalysisSheet(
            text = st.analysis, analyzing = st.analyzing, error = st.analysisError,
            generatedAt = st.analysisAt, fromCache = st.analysisFromCache,
            onAskInChat = { vm.onAskInChat { sid -> showSheet = false; onOpenChat(sid) } },
            onRegenerate = vm::onRegenerate,
            onDismiss = { showSheet = false },
        )
    }
}

/** 学期短名（x 轴标签）。正方学期形如 "2024-2025-1" → "24秋"/"25春"/"25夏"；
 *  退化到带"学年/学期"的命名格式时尽力缩写。 */
private fun shortTerm(name: String): String {
    val m = Regex("""(\d{4})-(\d{4})-(\d)""").find(name)
    if (m != null) {
        val (y1, y2, sem) = m.destructured
        return when (sem) {
            "1" -> "${y1.takeLast(2)}秋"
            "2" -> "${y2.takeLast(2)}春"
            "3" -> "${y2.takeLast(2)}夏"
            else -> "${y1.takeLast(2)}-${y2.takeLast(2)}"
        }
    }
    return name.replace("学年", "").replace("第一学期", "上").replace("第二学期", "下")
        .replace(" ", "").take(6)
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, com.example.personal_studio.ui.theme.Rule, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(com.example.personal_studio.ui.theme.Deep, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(12.dp),
        content = content,
    )
}
