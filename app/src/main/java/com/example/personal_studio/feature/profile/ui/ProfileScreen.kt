package com.example.personal_studio.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.profile.ProfileUiState
import com.example.personal_studio.feature.profile.ProfileViewModel
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

@Composable
fun ProfileScreen(
    onOpenImport: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenGradesPoll: () -> Unit,
    onOpenDdlPoll: () -> Unit,
    onLogin: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        AccountCard(st, onLogin = onLogin, onLogout = vm::onLogout)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard(Modifier.weight(1f), "课表导入", null, !st.loggedIn, onOpenImport)
            GridCard(Modifier.weight(1f), "成绩", st.gpa?.let { "GPA %.2f".format(it) }, !st.loggedIn, onOpenGrades)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GridCard(Modifier.weight(1f), "作业 DDL", if (st.ddlCount > 0) "${st.ddlCount} 待办" else null, !st.loggedIn, onOpenAssignments)
            GridCard(Modifier.weight(1f), "考试安排", if (st.examCount > 0) "${st.examCount} 即将" else null, !st.loggedIn, onOpenExams)
        }

        Spacer(Modifier.height(2.dp))
        Text("// 后台提醒", color = FoamDim, style = MaterialTheme.typography.labelMedium)
        PollRow("出分提醒", if (st.gradesPollEnabled) "已开 ${st.gradesPollInterval}h" else "已关", onOpenGradesPoll)
        PollRow("作业自动同步", if (st.ddlPollEnabled) "已开" else "已关", onOpenDdlPoll)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountCard(st: ProfileUiState, onLogin: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Rule).background(Deep)
            .let { if (!st.loggedIn) it.clickable { onLogin() } else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (st.loggedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◆ ", color = Phosphor, style = MaterialTheme.typography.headlineSmall)
                Text(st.username ?: "", color = Foam, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onLogout) { Text("退出登录", color = FoamMute, style = MaterialTheme.typography.labelMedium) }
            }
            Text(
                "已登录 · " + when (st.networkMode) { NetworkMode.LOCAL -> "校园网"; null -> "—"; else -> "外网" },
                color = FoamMute, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 22.dp),
            )
        } else {
            Text("◆ 未登录", color = FoamMute, style = MaterialTheme.typography.headlineSmall)
            Text("点此登录 — 成绩 / 课表 / 作业 / 考试免再输密码", color = FoamDim,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun GridCard(modifier: Modifier, name: String, status: String?, dimmed: Boolean, onClick: () -> Unit) {
    Column(
        modifier.border(1.dp, Rule).background(Deep).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text("▦ $name", color = if (dimmed) FoamMute else Foam, style = MaterialTheme.typography.headlineSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(status ?: " ", color = Cyan, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun PollRow(name: String, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▸ $name", color = Foam, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(status, color = if (status.startsWith("已开")) Phosphor else FoamMute, style = MaterialTheme.typography.labelMedium)
        Text("  ›", color = FoamMute, style = MaterialTheme.typography.bodyMedium)
    }
}
