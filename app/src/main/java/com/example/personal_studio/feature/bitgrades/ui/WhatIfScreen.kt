package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitgrades.WhatIfMode
import com.example.personal_studio.feature.bitgrades.WhatIfViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfScreen(onBack: () -> Unit, vm: WhatIfViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← 返回", color = FoamMute) }
            Spacer(Modifier.width(4.dp))
            Text("$ gpa-planner", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "当前  总学分 ${fmt1(st.book.totalCredits)}   总 GPA ${fmt2(st.book.overallGpa)}",
            color = FoamMute,
        )
        Spacer(Modifier.height(16.dp))

        Row {
            FilterChip(st.mode == WhatIfMode.REVERSE, { vm.onModeChange(WhatIfMode.REVERSE) }, { Text("目标反推") })
            Spacer(Modifier.width(8.dp))
            FilterChip(st.mode == WhatIfMode.PREDICT, { vm.onModeChange(WhatIfMode.PREDICT) }, { Text("预测") })
        }
        Spacer(Modifier.height(16.dp))

        val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        OutlinedTextField(st.remainingCredits, vm::onRemainingChange, label = { Text("剩余学分") },
            singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        if (st.mode == WhatIfMode.REVERSE) {
            OutlinedTextField(st.targetGpa, vm::onTargetChange, label = { Text("目标总 GPA") },
                singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            val r = vm.reverseResult(st)
            when {
                r == null -> Text("> 填入目标 GPA 与剩余学分", color = FoamMute)
                r.value <= 0.0 -> Text("> 目标已达成，剩余课程随便考都能保持 ✓", color = Phosphor)
                r.achievable -> Text("> 剩余课程平均需达 ${fmt2(r.value)} 绩点  ✓可行", color = Phosphor)
                else -> Text("> 需平均 ${fmt2(r.value)} 绩点 — 已超过 4.0 上限，✗ 不可达", color = Carmine)
            }
        } else {
            OutlinedTextField(st.assumedPoint, vm::onAssumedChange, label = { Text("假设剩余课程平均绩点") },
                singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            val p = vm.predictResult(st)
            if (p == null) Text("> 填入剩余学分与假设绩点", color = FoamMute)
            else Text("> 预计总 GPA 将变为 ${fmt2(p)}", color = Amber)
        }
    }
}

private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)
private fun fmt2(v: Double) = String.format(Locale.US, "%.2f", v)
