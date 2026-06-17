package com.example.personal_studio.feature.bitimport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.domain.bitimport.model.SyncSourceState
import com.example.personal_studio.domain.bitimport.model.SyncSourceStatus
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

/** 首启/手动一键批量同步进度页。完成或跳过后调 [onFinish]。 */
@Composable
fun SyncAllScreen(
    onFinish: () -> Unit,
    vm: SyncAllViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val anyFailed = ui.states.values.any { it.status == SyncSourceStatus.FAILED }

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .scanLines()
            .vignette()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Text("$ 初始化教务数据", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))

        if (ui.noCredentials) {
            Text(
                "未保存凭据,无法自动同步。\n请到各页手动刷新,或重新登录并勾选「记住密码」。",
                color = Amber, style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            SourceRow("课表", ui.states[SyncSource.COURSES])
            SourceRow("作业", ui.states[SyncSource.DDL])
            SourceRow("考试", ui.states[SyncSource.EXAMS])
            SourceRow("成绩", ui.states[SyncSource.GRADES])
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!ui.done) {
                Text(
                    "[跳过]", color = FoamDim, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { vm.skip(); onFinish() }.padding(8.dp),
                )
            } else {
                if (anyFailed) {
                    Text(
                        "[重试失败项]", color = Amber, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { vm.retry() }.padding(8.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "[进入]", color = Phosphor, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onFinish() }.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceRow(label: String, state: SyncSourceState?) {
    val s = state ?: SyncSourceState(SyncSourceStatus.PENDING)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "[$label]", color = Foam, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.width(12.dp))
        when (s.status) {
            SyncSourceStatus.PENDING ->
                Text("○ 待拉取", color = FoamDim, style = MaterialTheme.typography.bodyMedium)
            SyncSourceStatus.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▸ 拉取中…", color = Cyan, style = MaterialTheme.typography.bodyMedium)
                BlinkingCursor()
            }
            SyncSourceStatus.OK ->
                Text("✓ ${s.detail ?: "完成"}", color = Phosphor, style = MaterialTheme.typography.bodyMedium)
            SyncSourceStatus.FAILED ->
                Text("✗ 失败 · 可在该页手动刷新", color = Carmine, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
