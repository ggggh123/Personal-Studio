package com.example.personal_studio.feature.chat.ui

import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

private enum class Corner { TL, TR, BL, BR, NONE }

/**
 * Full-screen crop dialog. Key invariants:
 *  - The displayed image respects the bitmap's aspect ratio (no letterboxing inside
 *    the Image composable itself). All crop coordinates are therefore in the image's
 *    displayed coordinate space; mapping back to bitmap pixel space is a single
 *    uniform scale factor.
 *  - A single parent-level `detectDragGestures` owns all 4 corner handles. Each gesture
 *    picks the nearest corner at `onDragStart` and applies drag deltas only to that
 *    corner, so handles never "snap back".
 */
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
                    "无法解码图片", color = Foam,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Dialog
            }

            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

            // `imageSize` is the Image's actual displayed size (aspect-constrained).
            // All crop math happens in this coordinate space.
            var imageSize by remember { mutableStateOf(Size.Zero) }
            var cropRect by remember { mutableStateOf<UiRect?>(null) }

            // Initialize crop box to 75% × 50% of the displayed image, centered.
            if (cropRect == null && imageSize != Size.Zero) {
                cropRect = UiRect(
                    left = imageSize.width * 0.125f,
                    top = imageSize.height * 0.25f,
                    right = imageSize.width * 0.875f,
                    bottom = imageSize.height * 0.75f,
                )
            }

            val density = LocalDensity.current
            val handleSizePx = with(density) { 18.dp.toPx() }
            val minSizePx = with(density) { 48.dp.toPx() }
            val grabRadiusPx = with(density) { 36.dp.toPx() }

            var draggingCorner by remember { mutableStateOf(Corner.NONE) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        // Gestures attached HERE so the pointer coordinate system
                        // matches the image's displayed space.
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    draggingCorner = nearestCorner(pos, cropRect, grabRadiusPx)
                                },
                                onDragEnd = { draggingCorner = Corner.NONE },
                                onDragCancel = { draggingCorner = Corner.NONE },
                                onDrag = { change, drag ->
                                    val rect = cropRect ?: return@detectDragGestures
                                    val corner = draggingCorner
                                    if (corner == Corner.NONE) return@detectDragGestures
                                    change.consume()

                                    cropRect = when (corner) {
                                        Corner.TL -> rect.copy(
                                            left = (rect.left + drag.x)
                                                .coerceIn(0f, rect.right - minSizePx),
                                            top = (rect.top + drag.y)
                                                .coerceIn(0f, rect.bottom - minSizePx),
                                        )
                                        Corner.TR -> rect.copy(
                                            right = (rect.right + drag.x)
                                                .coerceIn(rect.left + minSizePx, imageSize.width),
                                            top = (rect.top + drag.y)
                                                .coerceIn(0f, rect.bottom - minSizePx),
                                        )
                                        Corner.BL -> rect.copy(
                                            left = (rect.left + drag.x)
                                                .coerceIn(0f, rect.right - minSizePx),
                                            bottom = (rect.bottom + drag.y)
                                                .coerceIn(rect.top + minSizePx, imageSize.height),
                                        )
                                        Corner.BR -> rect.copy(
                                            right = (rect.right + drag.x)
                                                .coerceIn(rect.left + minSizePx, imageSize.width),
                                            bottom = (rect.bottom + drag.y)
                                                .coerceIn(rect.top + minSizePx, imageSize.height),
                                        )
                                        Corner.NONE -> rect
                                    }
                                },
                            )
                        }
                        .drawWithContent {
                            drawContent()
                            imageSize = size   // authoritative displayed image size
                            val rect = cropRect ?: return@drawWithContent
                            // Dim outside the crop rect
                            val dimPath = Path().apply {
                                addRect(UiRect(0f, 0f, size.width, size.height))
                                addRect(rect)
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(dimPath, color = Void.copy(alpha = 0.65f))
                            // Crop border
                            drawRect(
                                color = Phosphor,
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width, rect.height),
                                style = Stroke(width = 2f),
                            )
                            // 4 corner markers — purely visual; gesture is on parent
                            val half = handleSizePx / 2f
                            listOf(
                                Offset(rect.left, rect.top),
                                Offset(rect.right, rect.top),
                                Offset(rect.left, rect.bottom),
                                Offset(rect.right, rect.bottom),
                            ).forEach { c ->
                                drawRect(
                                    color = Phosphor,
                                    topLeft = Offset(c.x - half, c.y - half),
                                    size = Size(handleSizePx, handleSizePx),
                                )
                            }
                        },
                )
            }

            // Bottom action bar
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Void),
            ) {
                Text(
                    "# 拖动四角裁剪",
                    color = FoamDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "[取消]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Foam,
                        modifier = Modifier.clickable { onDismiss() },
                    )
                    Spacer(Modifier)
                    Text(
                        text = "[确认 ↵]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Phosphor,
                        modifier = Modifier.clickable {
                            val rect = cropRect ?: return@clickable
                            if (imageSize == Size.Zero) return@clickable
                            // `imageSize` is the Image's displayed size — ALREADY the
                            // actual image area (no letterbox). Scale is uniform.
                            val scale = bitmap.width.toFloat() / imageSize.width
                            val imageRect = Rect(
                                (rect.left * scale).toInt(),
                                (rect.top * scale).toInt(),
                                (rect.right * scale).toInt(),
                                (rect.bottom * scale).toInt(),
                            )
                            onConfirm(cropImageToFile(imagePath, imageRect))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Picks the closest corner to [pos]. Returns [Corner.NONE] if no corner is within
 * [grabRadius] pixels — a drag inside the crop rectangle with no corner nearby is
 * ignored (P1 doesn't support translating the whole crop box).
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
