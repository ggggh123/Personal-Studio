package com.example.personal_studio.core.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import java.util.Locale

/** 单点序列。label = x 轴标签（学期短名）。 */
data class LinePoint(val label: String, val value: Double)

@Composable
fun GpaLineChart(points: List<LinePoint>, modifier: Modifier = Modifier, maxPoint: Double = 4.0) {
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = FoamDim, fontSize = 9.sp)
    val valStyle = TextStyle(color = Phosphor, fontSize = 9.sp)
    val xStyle = TextStyle(color = FoamMute, fontSize = 9.sp)
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (points.isEmpty()) return@Canvas
        val (lo, hi) = ChartMath.gpaBounds(points.map { it.value }, maxPoint)
        val span = (hi - lo).coerceAtLeast(0.001)
        val leftPad = 32f
        val topPad = 18f
        val bottomPad = 18f
        val plotW = size.width - leftPad
        val plotH = size.height - topPad - bottomPad
        val baseY = topPad + plotH
        fun x(i: Int) = leftPad + if (points.size == 1) plotW / 2 else plotW * i / (points.size - 1)
        fun y(v: Double) = topPad + (plotH - ((v - lo) / span * plotH)).toFloat()

        // gridlines + y-axis value labels at lo / mid / hi
        listOf(lo, (lo + hi) / 2.0, hi).forEach { gv ->
            val gy = y(gv)
            drawLine(Rule, Offset(leftPad, gy), Offset(size.width, gy), strokeWidth = 1f)
            val lay = measurer.measure(String.format(Locale.US, "%.1f", gv), axisStyle)
            drawText(lay, topLeft = Offset(0f, gy - lay.size.height / 2f))
        }
        // area fill under the line
        val area = Path().apply {
            moveTo(x(0), baseY)
            points.forEachIndexed { i, p -> lineTo(x(i), y(p.value)) }
            lineTo(x(points.size - 1), baseY)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(Phosphor.copy(alpha = 0.22f), Color.Transparent), startY = topPad, endY = baseY),
        )
        // line: glow then solid
        val line = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.value)) else lineTo(x(i), y(p.value)) }
        }
        drawPath(line, Phosphor.copy(alpha = 0.25f), style = Stroke(width = 6f))
        drawPath(line, Phosphor, style = Stroke(width = 2f))
        // points + per-point value labels + x-axis labels
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.value)
            drawCircle(Phosphor, radius = 4f, center = Offset(px, py))
            val v = measurer.measure(String.format(Locale.US, "%.2f", p.value), valStyle)
            drawText(v, topLeft = Offset((px - v.size.width / 2f).coerceIn(leftPad, size.width - v.size.width), py - 15f))
            val xl = measurer.measure(p.label, xStyle)
            drawText(xl, topLeft = Offset((px - xl.size.width / 2f).coerceIn(0f, size.width - xl.size.width), baseY + 3f))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E0C)
@Composable
private fun GpaLineChartPreview() {
    GpaLineChart(points = listOf(
        LinePoint("大一上", 3.43), LinePoint("大一下", 3.42), LinePoint("大二上", 3.40),
    ))
}
