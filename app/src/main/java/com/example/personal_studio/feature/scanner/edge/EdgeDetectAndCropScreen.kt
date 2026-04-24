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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.ui.components.CornerDragOverlay
import com.example.personal_studio.ui.components.rememberZoomableBoxState
import com.example.personal_studio.ui.components.zoomTransform
import com.example.personal_studio.ui.components.zoomable
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun EdgeDetectAndCropScreen(
    capturedFilePath: String,
    autoDetect: Boolean = true,
    /** Optional pre-detected corners in normalized [0,1]² portrait coords,
     *  forwarded from the live-preview overlay. If set, the VM skips its
     *  own post-capture detection call and uses these instead — so the
     *  quad the user saw on the viewfinder is the quad they get after
     *  tapping capture. */
    preDetectedNormalized: List<PointF>? = null,
    onConfirm: (corners: List<PointF>) -> Unit,
    onRetake: () -> Unit,
) {
    // Keying the VM by capturedFilePath means each distinct capture gets its
    // own VM instance — critical when this composable is reused inside a
    // DocumentBuilder state machine (same nav entry, multiple rounds of
    // capture→edge→enhance), where the default class-only key would leak
    // the first round's bitmap into later rounds.
    val vm: EdgeDetectViewModel = hiltViewModel(
        key = capturedFilePath,
        creationCallback = { f: EdgeDetectViewModel.Factory ->
            f.create(capturedFilePath, autoDetect, preDetectedNormalized)
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var containerSize by remember { mutableStateOf(Size.Zero) }
    val zoom = rememberZoomableBoxState()
    val density = LocalDensity.current
    val grabRadiusPx = with(density) { 36.dp.toPx() }

    // Computed below inside the content block; re-referenced from hitTest. We
    // hold it in a mutable state so the outer zoomable modifier (which takes
    // a stable lambda) sees the current corner positions without being re-keyed.
    var cornersUiLatest by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val hitTest: (Offset) -> Boolean = { pos ->
        cornersUiLatest.any { corner ->
            (pos - corner).getDistance() <= grabRadiusPx
        }
    }

    Column(Modifier.fillMaxSize().background(Void)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .zoomable(zoom, hitTest = hitTest),
            contentAlignment = Alignment.Center,
        ) {
            state.bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomTransform(zoom),
                )
                val cornersBitmap = state.corners ?: emptyList()
                if (cornersBitmap.size == 4 && containerSize.width > 0) {
                    val fit = fitTransform(bmp.width, bmp.height, containerSize)
                    // Apply the visual zoom to corners so the overlay draws
                    // them where the (zoomed) image visually shows the paper,
                    // and hit testing lines up with what the user sees.
                    val s = zoom.scale
                    val ox = zoom.offset.x
                    val oy = zoom.offset.y
                    val cornersUi = cornersBitmap.map { p ->
                        val baseX = p.x * fit.scale + fit.offsetX
                        val baseY = p.y * fit.scale + fit.offsetY
                        Offset(baseX * s + ox, baseY * s + oy)
                    }
                    cornersUiLatest = cornersUi
                    CornerDragOverlay(
                        corners = cornersUi,
                        onCornersChange = { uiCorners ->
                            vm.updateCorners(
                                uiCorners.map {
                                    val baseX = (it.x - ox) / s
                                    val baseY = (it.y - oy) / s
                                    PointF(
                                        ((baseX - fit.offsetX) / fit.scale).coerceAtLeast(0f),
                                        ((baseY - fit.offsetY) / fit.scale).coerceAtLeast(0f),
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
