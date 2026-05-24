package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun GradesScreen(
    onBack: () -> Unit,
    onSync: () -> Unit,
    onOpenChat: (Long) -> Unit,
    vm: GradesViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Void).statusBarsPadding().padding(16.dp)
        .verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ transcript", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onSync) { Text("↻ 同步") }
        }

        if (st.book.isEmpty) {
            Spacer(Modifier.height(48.dp))
            Text("还没有成绩数据", color = FoamMute)
            Button(onSync, Modifier.padding(top = 12.dp)) { Text("从教务系统查询成绩") }
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        GpaOverviewCard(st.book)

        Spacer(Modifier.height(20.dp))
        Text("GPA 趋势", color = FoamMute)
        val points = st.book.terms.reversed().map { LinePoint(shortTerm(it.termName), it.weightedGpa) }
        GpaLineChart(points)

        Spacer(Modifier.height(20.dp))
        Text("成绩分布", color = FoamMute)
        val allCourses = st.book.terms.flatMap { it.courses }
        GradeBarChart(GradeBucketer.bucket(allCourses))

        Spacer(Modifier.height(20.dp))
        st.book.terms.forEachIndexed { i, t -> TermGradeSection(t, initiallyExpanded = i == 0) }

        Spacer(Modifier.height(20.dp))
        Button({ showSheet = true; vm.onAnalyze() }, Modifier.fillMaxWidth()) { Text("生成 AI 分析") }
    }

    if (showSheet) {
        AiAnalysisSheet(
            text = st.analysis, analyzing = st.analyzing, error = st.analysisError,
            onAskInChat = { vm.onAskInChat { sid -> showSheet = false; onOpenChat(sid) } },
            onDismiss = { showSheet = false },
        )
    }
}

/** "2024-2025学年 第二学期" → "24-25下" 之类的短名（尽力而为）。 */
private fun shortTerm(name: String): String =
    name.replace("学年", "").replace("第一学期", "上").replace("第二学期", "下")
        .replace(" ", "").take(8)
