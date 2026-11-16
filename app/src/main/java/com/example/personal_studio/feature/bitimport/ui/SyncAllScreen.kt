package com.example.personal_studio.feature.bitimport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitimport.model.SyncSource
import com.example.personal_studio.feature.timeline.today.DetailAction
import com.example.personal_studio.feature.timeline.today.PillButton
import com.example.personal_studio.ui.theme.Tg

/**
 * First sync right after signing in: the four sources one after another, same rows as the
 * sync panel. "跳过" stops what is left; "进入" goes to 今日.
 */
@Composable
fun SyncAllScreen(
    onFinish: () -> Unit,
    onLogin: () -> Unit,
    vm: SyncAllViewModel = hiltViewModel(),
) {
    val c = Tg.colors
    val progress by vm.progress.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (!vm.isRunning) vm.startAll() }
    val done = progress?.done == true

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("同步学校数据", style = MaterialTheme.typography.displaySmall, color = c.text, modifier = Modifier.padding(top = 24.dp))
        Text(
            "课表、作业、考试和成绩依次下载到本机；某一项失败不影响其余各项，之后可在校园页重试。",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (progress?.noCredentials == true) {
            Text(
                "没有保存学校账户，无法自动同步。可以稍后在校园页登录后再同步。",
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
            )
        } else {
            SyncSourceRows(progress, onLogin = onLogin)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!done) {
                PillButton(DetailAction("跳过", primary = false) { vm.skip(); onFinish() }, Modifier.weight(1f))
            } else {
                val failed = progress?.failed.orEmpty()
                if (failed.isNotEmpty() && failed.size < SyncSource.values().size) {
                    PillButton(DetailAction("只重试失败项", primary = false, onClick = vm::retryFailed), Modifier.weight(1f))
                }
                PillButton(DetailAction("进入", primary = true, onClick = onFinish), Modifier.weight(1f))
            }
        }
    }
}
