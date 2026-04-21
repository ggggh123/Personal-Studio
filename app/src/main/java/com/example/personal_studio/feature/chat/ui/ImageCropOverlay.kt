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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

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

            // Initialize crop rect once we know our container size
            if (cropRect == null && containerSize != Size.Zero) {
                cropRect = UiRect(
                    left = containerSize.width * 0.125f,
                    top = containerSize.height * 0.25f,
                    right = containerSize.width * 0.875f,
                    bottom = containerSize.height * 0.75f,
                )
            }

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp)
                    .drawWithContent {
                        drawContent()
                        containerSize = size
                        val rect = cropRect ?: return@drawWithContent
                        // Dim outside crop rect (even-odd fill punches a hole)
                        val path = Path().apply {
                            addRect(UiRect(0f, 0f, size.width, size.height))
                            addRect(rect)
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(path, color = Void.copy(alpha = 0.65f))
                        // Crop border
                        drawRect(
                            color = Phosphor,
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width, rect.height),
                            style = Stroke(width = 2f),
                        )
                    },
            )

            // Drag handles — four corners
            val density = LocalDensity.current
            val handleSize = 18.dp
            val handleSizePx = with(density) { handleSize.toPx() }

            cropRect?.let { rect ->
                Handle(
                    x = rect.left - handleSizePx / 2,
                    y = rect.top - handleSizePx / 2,
                    size = handleSize,
                ) { dx, dy ->
                    cropRect = rect.copy(
                        left = (rect.left + dx).coerceIn(0f, rect.right - 40f),
                        top = (rect.top + dy).coerceIn(0f, rect.bottom - 40f),
                    )
                }
                Handle(
                    x = rect.right - handleSizePx / 2,
                    y = rect.top - handleSizePx / 2,
                    size = handleSize,
                ) { dx, dy ->
                    cropRect = rect.copy(
                        right = (rect.right + dx).coerceIn(rect.left + 40f, containerSize.width),
                        top = (rect.top + dy).coerceIn(0f, rect.bottom - 40f),
                    )
                }
                Handle(
                    x = rect.left - handleSizePx / 2,
                    y = rect.bottom - handleSizePx / 2,
                    size = handleSize,
                ) { dx, dy ->
                    cropRect = rect.copy(
                        left = (rect.left + dx).coerceIn(0f, rect.right - 40f),
                        bottom = (rect.bottom + dy).coerceIn(rect.top + 40f, containerSize.height),
                    )
                }
                Handle(
                    x = rect.right - handleSizePx / 2,
                    y = rect.bottom - handleSizePx / 2,
                    size = handleSize,
                ) { dx, dy ->
                    cropRect = rect.copy(
                        right = (rect.right + dx).coerceIn(rect.left + 40f, containerSize.width),
                        bottom = (rect.bottom + dy).coerceIn(rect.top + 40f, containerSize.height),
                    )
                }
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

@Composable
private fun Handle(
    x: Float,
    y: Float,
    size: Dp,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .size(size)
            .clip(CircleShape)
            .background(Phosphor)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x, drag.y)
                }
            }
    )
}
