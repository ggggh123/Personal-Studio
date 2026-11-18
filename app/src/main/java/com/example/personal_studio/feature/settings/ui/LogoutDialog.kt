package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.personal_studio.ui.components.PillStyle
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.theme.Tg

/**
 * 退出学校账户: clears the saved account and the school's web sign-in, with an opt-in to also
 * delete the school data on this phone (grades, imported courses, assignments, exams).
 */
@Composable
internal fun LogoutDialog(onConfirm: (clearData: Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = Tg.colors
    var clear by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surfaceRaised)
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
        ) {
            Text("退出学校账户？", style = MaterialTheme.typography.headlineMedium, color = c.text)
            Text(
                "会清除保存的账户密码和学校网页登录状态，后台检查也会关闭。",
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.bg)
                    .clickable(role = Role.Switch) { clear = !clear }
                    .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("同时删除本机的学校数据", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text(
                        "成绩、导入的课表、乐学和新乐学作业、考试；手动添加的课程和待办保留",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TgSwitch(checked = clear, onCheckedChange = { clear = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TgPillButton("取消", onClick = onDismiss, style = PillStyle.SECONDARY, modifier = Modifier.weight(1f))
                TgPillButton(if (clear) "退出并删除" else "退出登录", onClick = { onConfirm(clear) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
