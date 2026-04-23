package com.example.personal_studio.feature.scanner.edge

import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.ui.components.CornerDragOverlay
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun EdgeDetectAndCropScreen(
    capturedFilePath: String,
    onConfirm: (corners: List<PointF>) -> Unit,
    onRetake: () -> Unit,
) {
    val vm: EdgeDetectViewModel = hiltViewModel(
        creationCallback = { f: EdgeDetectViewModel.Factory -> f.create(capturedFilePath) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var containerSize by remember { mutableStateOf(Size.Zero) }

    Column(Modifier.fillMaxSize().background(Void)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) },
            contentAlignment = Alignment.Center,
        ) {
            state.bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                val cornersBitmap = state.corners ?: emptyList()
                if (cornersBitmap.size == 4 && containerSize.width > 0) {
                    val fit = fitTransform(bmp.width, bmp.height, containerSize)
                    val cornersUi = cornersBitmap.map { p ->
                        Offset(p.x * fit.scale + fit.offsetX, p.y * fit.scale + fit.offsetY)
                    }
                    CornerDragOverlay(
                        corners = cornersUi,
                        onCornersChange = { uiCorners ->
                            vm.updateCorners(
                                uiCorners.map {
                                    PointF(
                                        ((it.x - fit.offsetX) / fit.scale).coerceAtLeast(0f),
                                        ((it.y - fit.offsetY) / fit.scale).coerceAtLeast(0f),
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("[↻ retake]", color = FoamDim, modifier = Modifier.clickable(onClick = onRetake))
            val info = if (state.detectedAutomatically) "✓ corners auto-detected" else "! drag corners to fit"
            Text(
                info,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.detectedAutomatically) Phosphor else Amber,
            )
            Text(
                "[confirm ↵]",
                color = Phosphor,
                modifier = Modifier.clickable { state.corners?.let(onConfirm) },
            )
        }
    }
}

private data class FitTransform(val offsetX: Float, val offsetY: Float, val scale: Float)

private fun fitTransform(bmpW: Int, bmpH: Int, container: Size): FitTransform {
    val sx = container.width / bmpW
    val sy = container.height / bmpH
    val s = minOf(sx, sy)
    val dw = bmpW * s
    val dh = bmpH * s
    return FitTransform((container.width - dw) / 2f, (container.height - dh) / 2f, s)
}
