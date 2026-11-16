package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.bitgrades.WhatIfMode
import com.example.personal_studio.feature.bitgrades.WhatIfViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.KeyboardHandledByInsets
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.components.TgTextField
import com.example.personal_studio.ui.theme.Tg
import java.util.Locale

/**
 * 目标绩点: 反推 — what the remaining credits must average to reach a target; 预测 — where
 * the overall GPA lands if they average a given point. Both read the current grade book.
 */
@Composable
fun WhatIfScreen(onBack: () -> Unit, vm: WhatIfViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    KeyboardHandledByInsets()

    Column(Modifier.fillMaxSize().imePadding()) {
        TgTopBar(
            route = "目标绩点",
            subtitle = "目标绩点\n现在 GPA ${fmt2(st.book.overallGpa)} · 已修 ${fmtCredits(st.book.totalCredits)} 学分",
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TgSegmented(
                options = listOf("反推", "预测"),
                selectedIndex = if (st.mode == WhatIfMode.REVERSE) 0 else 1,
                onSelect = { vm.onModeChange(if (it == 0) WhatIfMode.REVERSE else WhatIfMode.PREDICT) },
                modifier = Modifier.enterUp(true, 0),
            )
            TgCard(Modifier.enterUp(true, 40)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TgTextField(st.remainingCredits, vm::onRemainingChange, label = "还要修的学分", placeholder = "例如 40", keyboardOptions = num)
                    if (st.mode == WhatIfMode.REVERSE) {
                        TgTextField(st.targetGpa, vm::onTargetChange, label = "想达到的总 GPA", placeholder = "例如 3.6", keyboardOptions = num)
                    } else {
                        TgTextField(st.assumedPoint, vm::onAssumedChange, label = "剩下的课平均能拿", placeholder = "绩点，例如 3.5", keyboardOptions = num)
                    }
                }
            }
            TgCard(Modifier.enterUp(true, 80)) {
                AnimatedContent(st.mode, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "whatIf") { mode ->
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        if (mode == WhatIfMode.REVERSE) {
                            val r = vm.reverseResult(st)
                            when {
                                r == null -> Hint("填好还要修的学分和目标 GPA，这里会算出剩下的课平均要拿多少。")
                                r.value <= 0.0 -> Result("已经达到", "剩下的课怎么考，总 GPA 都不会低于目标。", accent = true)
                                r.achievable -> Result(fmt2(r.value), "剩下的课平均要拿到这个绩点，目标可以达到。", label = "剩下的课平均需要", accent = true)
                                else -> Result(fmt2(r.value), "超过了 4.0 的上限，这个目标达不到；可以降低目标或增加学分再算。", label = "剩下的课平均需要", error = true)
                            }
                        } else {
                            val p = vm.predictResult(st)
                            if (p == null) {
                                Hint("填好还要修的学分和预计平均绩点，这里会算出毕业时的总 GPA。")
                            } else {
                                val delta = p - st.book.overallGpa
                                val sign = if (delta >= 0) "+" else "−"
                                Result(fmt2(p), "比现在 $sign${fmt2(kotlin.math.abs(delta))}", label = "预计总 GPA", accent = delta >= 0)
                            }
                        }
                    }
                }
            }
            Text(
                "按学分加权计算，只是估计；重修、不计绩点的课程以学校规则为准。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Tg.colors.textSecondary)
}

@Composable
private fun Result(value: String, note: String, label: String? = null, accent: Boolean = false, error: Boolean = false) {
    val c = Tg.colors
    if (label != null) Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    Text(
        value,
        style = MaterialTheme.typography.displayMedium,
        color = when { error -> c.error; accent -> c.accent; else -> c.text },
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(note, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
}

private fun fmt2(v: Double) = String.format(Locale.US, "%.2f", v)
