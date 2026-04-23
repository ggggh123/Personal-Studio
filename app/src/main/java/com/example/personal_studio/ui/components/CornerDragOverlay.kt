package com.example.personal_studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

enum class CornerId { TL, TR, BR, BL }

/**
 * Four-corner draggable quadrilateral overlay. Coordinates are in the overlay's
 * Compose px space. Parent is responsible for translating to bitmap px.
 */
@Composable
fun CornerDragOverlay(
    corners: List<Offset>,             // TL, TR, BR, BL — in Compose px
    onCornersChange: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
    strokeColor: Color = Color(0xFF41FF8F),
    handleColor: Color = Color(0xFF41FF8F),
) {
    require(corners.size == 4) { "corners must have 4 points" }
    var boxSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val handlePx = with(density) { 18.dp.toPx() }
    val grabRadiusPx = with(density) { 36.dp.toPx() }
    var dragging by remember { mutableStateOf<CornerId?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        dragging = nearestCorner(pos, corners, grabRadiusPx)
                    },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                    onDrag = { change, drag ->
                        val which = dragging ?: return@detectDragGestures
                        change.consume()
                        val idx = which.ordinal
                        val clamped = Offset(
                            (corners[idx].x + drag.x).coerceIn(0f, boxSize.width),
                            (corners[idx].y + drag.y).coerceIn(0f, boxSize.height),
                        )
                        onCornersChange(corners.toMutableList().also { it[idx] = clamped })
                    },
                )
            },
    ) {
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            lineTo(corners[1].x, corners[1].y)
            lineTo(corners[2].x, corners[2].y)
            lineTo(corners[3].x, corners[3].y)
            close()
        }
        drawPath(path, color = strokeColor, style = Stroke(width = 3f))
        corners.forEach { c ->
            drawRect(
                color = handleColor,
                topLeft = Offset(c.x - handlePx / 2, c.y - handlePx / 2),
                size = Size(handlePx, handlePx),
            )
        }
    }
}

private fun nearestCorner(pos: Offset, corners: List<Offset>, grabRadius: Float): CornerId? {
    var best: CornerId? = null
    var bestDist = grabRadius
    CornerId.entries.forEachIndexed { i, id ->
        val d = kotlin.math.hypot(pos.x - corners[i].x, pos.y - corners[i].y)
        if (d < bestDist) {
            bestDist = d
            best = id
        }
    }
    return best
}
