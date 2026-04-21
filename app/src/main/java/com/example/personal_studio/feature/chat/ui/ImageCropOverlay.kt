package com.example.personal_studio.feature.chat.ui

import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as UiRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/**
 * Identifies which corner handle is being actively dragged. Computed once per gesture
 * in `onDragStart` and cleared on end / cancel.
 */
private enum class Corner { TL, TR, BL, BR, NONE }

@Composable
fun ImageCropOverlay(
    imagePath: String,
    onDismiss: () -> Unit,
    onConfirm: (croppedPath: String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Void)) {
            val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
            if (bitmap == null) {
                Text(
                    "could not decode image", color = Foam,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Dialog
            }

            var containerSize by remember { mutableStateOf(Size.Zero) }
            var cropRect by remember { mutableStateOf<UiRect?>(null) }

            if (cropRect == null && containerSize != Size.Zero) {
                cropRect = UiRect(
                    left = containerSize.width * 0.125f,
                    top = containerSize.height * 0.25f,
                    right = containerSize.width * 0.875f,
                    bottom = containerSize.height * 0.75f,
                )
            }

            val density = LocalDensity.current
            val handleSizePx = with(density) { 18.dp.toPx() }
            val minSizePx = with(density) { 48.dp.toPx() }
            val grabRadiusPx = with(density) { 36.dp.toPx() }

            // Which corner is currently being dragged. Only set during a gesture.
            var draggingCorner by remember { mutableStateOf(Corner.NONE) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                draggingCorner = nearestCorner(pos, cropRect, grabRadiusPx)
                            },
                            onDragEnd = { draggingCorner = Corner.NONE },
                            onDragCancel = { draggingCorner = Corner.NONE },
                            onDrag = { change, drag ->
                                val rect = cropRect ?: return@detectDragGestures
                                val c = draggingCorner
                                if (c == Corner.NONE) return@detectDragGestures
                                change.consume()

                                cropRect = when (c) {
                                    Corner.TL -> rect.copy(
                                        left = (rect.left + drag.x).coerceIn(0f, rect.right - minSizePx),
                                        top = (rect.top + drag.y).coerceIn(0f, rect.bottom - minSizePx),
                                    )
                                    Corner.TR -> rect.copy(
                                        right = (rect.right + drag.x).coerceIn(rect.left + minSizePx, containerSize.width),
                                        top = (rect.top + drag.y).coerceIn(0f, rect.bottom - minSizePx),
                                    )
                                    Corner.BL -> rect.copy(
                                        left = (rect.left + drag.x).coerceIn(0f, rect.right - minSizePx),
                                        bottom = (rect.bottom + drag.y).coerceIn(rect.top + minSizePx, containerSize.height),
                                    )
                                    Corner.BR -> rect.copy(
                                        right = (rect.right + drag.x).coerceIn(rect.left + minSizePx, containerSize.width),
                                        bottom = (rect.bottom + drag.y).coerceIn(rect.top + minSizePx, containerSize.height),
                                    )
                                    Corner.NONE -> rect
                                }
                            },
                        )
                    },
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            containerSize = size
                            val rect = cropRect ?: return@drawWithContent
                            // Dim outside crop rect
                            val path = Path().apply {
                                addRect(UiRect(0f, 0f, size.width, size.height))
                                addRect(rect)
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(path, color = Void.copy(alpha = 0.65f))
                            // Border
                            drawRect(
                                color = Phosphor,
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width, rect.height),
                                style = Stroke(width = 2f),
                            )
                            // 4 corner markers (visual only — gesture is handled by parent)
                            val half = handleSizePx / 2f
                            val cornerColor = if (draggingCorner == Corner.NONE) Phosphor else Phosphor
                            listOf(
                                Offset(rect.left, rect.top),
                                Offset(rect.right, rect.top),
                                Offset(rect.left, rect.bottom),
                                Offset(rect.right, rect.bottom),
                            ).forEach { c ->
                                drawRect(
                                    color = cornerColor,
                                    topLeft = Offset(c.x - half, c.y - half),
                                    size = Size(handleSizePx, handleSizePx),
                                )
                            }
                        },
                )
            }

            // Bottom action bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Void)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "[cancel]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Foam,
                    modifier = Modifier.clickable { onDismiss() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "[confirm ↵]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Phosphor,
                    modifier = Modifier.clickable {
                        val rect = cropRect ?: return@clickable
                        if (containerSize == Size.Zero) return@clickable
                        val scaleX = bitmap.width.toFloat() / containerSize.width
                        val scaleY = bitmap.height.toFloat() / containerSize.height
                        val imageRect = Rect(
                            (rect.left * scaleX).toInt(),
                            (rect.top * scaleY).toInt(),
                            (rect.right * scaleX).toInt(),
                            (rect.bottom * scaleY).toInt(),
                        )
                        onConfirm(cropImageToFile(imagePath, imageRect))
                    },
                )
            }
        }
    }
}

/**
 * Picks the closest corner to [pos]. Returns [Corner.NONE] if no corner is within
 * [grabRadius] pixels — a drag inside the crop rectangle with no corner nearby is
 * ignored (rather than accidentally moving the whole box, which we don't support in P1).
 */
private fun nearestCorner(pos: Offset, rect: UiRect?, grabRadius: Float): Corner {
    if (rect == null) return Corner.NONE
    val corners = listOf(
        Corner.TL to Offset(rect.left, rect.top),
        Corner.TR to Offset(rect.right, rect.top),
        Corner.BL to Offset(rect.left, rect.bottom),
        Corner.BR to Offset(rect.right, rect.bottom),
    )
    var best: Corner = Corner.NONE
    var bestDist = grabRadius
    for ((c, cornerPos) in corners) {
        val d = distance(pos, cornerPos)
        if (d < bestDist) {
            bestDist = d
            best = c
        }
    }
    return best
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
