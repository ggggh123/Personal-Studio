package com.example.personal_studio.core.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

/** 单点序列。label 显示在 x 轴下方（如学期短名）。 */
data class LinePoint(val label: String, val value: Double)

@Composable
fun GpaLineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    maxPoint: Double = 4.0,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(start = 28.dp, end = 8.dp, top = 8.dp, bottom = 22.dp)) {
            if (points.isEmpty()) return@Canvas
            val (lo, hi) = ChartMath.gpaBounds(points.map { it.value }, maxPoint)
            val span = (hi - lo).coerceAtLeast(0.001)
            val w = size.width; val h = size.height
            fun x(i: Int) = if (points.size == 1) w / 2 else w * i / (points.size - 1)
            fun y(v: Double) = h - ((v - lo) / span * h).toFloat()

            // 网格线（3 条）
            for (g in 0..2) {
                val gy = h * g / 2
                drawLine(Rule, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            // 折线 + 点（phosphor 辉光：先粗后细两道）
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                val px = x(i); val py = y(p.value)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Phosphor.copy(alpha = 0.25f), style = Stroke(width = 6f))
            drawPath(path, Phosphor, style = Stroke(width = 2f))
            points.forEachIndexed { i, p ->
                drawCircle(Phosphor, radius = 4f, center = Offset(x(i), y(p.value)))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E0C)
@Composable
private fun GpaLineChartPreview() {
    GpaLineChart(points = listOf(
        LinePoint("大一上", 3.0), LinePoint("大一下", 3.4),
        LinePoint("大二上", 3.6), LinePoint("大二下", 3.8),
    ))
}
