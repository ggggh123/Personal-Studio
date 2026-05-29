package com.example.personal_studio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
import kotlin.math.abs

private const val LOGO = "PERSONAL // STUDIO"

/** 字符波浪扫过的窗口宽度(以字符为单位)与放大幅度。 */
private const val WAVE_SPREAD = 2.2f
private const val WAVE_AMP = 0.55f

/**
 * 冷启动品牌屏。整体淡入后,一道波浪从左到右**扫过一遍**:每个字符依次放大上跳
 * (graphicsLayer scale + translationY,三角窗驱动)后归位,不循环;CRT 扫描线 + 暗角延续终端美学。
 * 由 MainScreen 在冷启动时停留约 1.3s 后 crossfade 渐变进入。
 */
@Composable
fun TerminalSplash() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "splash-enter",
    )
    // 波头位置 0→1 跑一遍(不循环):从最左扫到最右,每个字符依次跳动一次后归位。
    val wave by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
        label = "splash-wave",
    )

    Box(
        Modifier.fillMaxSize().background(Void).scanLines().vignette(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { this.alpha = enterAlpha },
        ) {
            val n = LOGO.length
            val headPos = wave * (n + 4f) - 2f   // 波头从 -2 扫到 n+2
            Row {
                LOGO.forEachIndexed { i, ch ->
                    val bump = (1f - abs(headPos - i) / WAVE_SPREAD).coerceAtLeast(0f)
                    val scale = 1f + WAVE_AMP * bump
                    Text(
                        text = ch.toString(),
                        color = Phosphor,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = -bump * 14f   // 放大时上跳
                        },
                    )
                }
            }
            Text("booting…", color = FoamDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}
