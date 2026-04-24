package com.example.personal_studio.feature.scanner.edge

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.scanner.BitmapIo
import com.example.personal_studio.data.scanner.EdgeDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class EdgeDetectUiState(
    val bitmap: Bitmap? = null,
    val corners: List<PointF>? = null,  // in bitmap px space
    val detectedAutomatically: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel(assistedFactory = EdgeDetectViewModel.Factory::class)
class EdgeDetectViewModel @AssistedInject constructor(
    @Assisted private val capturedFilePath: String,
    @Assisted private val autoDetect: Boolean,
    /** Normalized [0,1]² portrait corners snapshotted from the live-preview
     *  overlay at capture instant. If present, we use these instead of
     *  re-running detection on the captured JPEG — the post-capture ONNX
     *  path produces geometrically different heatmaps than the live path
     *  (different resolution / orientation) and sometimes disagrees with
     *  what the user visually confirmed before tapping the shutter. */
    @Assisted private val preDetectedNormalized: List<PointF>?,
    private val detector: EdgeDetector,
) : ViewModel() {
    private val _state = MutableStateFlow(EdgeDetectUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bmp = BitmapIo.decodeDownscaled(File(capturedFilePath))
            val auto = when {
                !autoDetect -> null
                preDetectedNormalized != null -> {
                    // Multiply normalized coords by the portrait bitmap's
                    // dimensions. Then **re-order** the 4 points by portrait
                    // sum/diff: ONNX sorted them canonically in ANALYZER
                    // (landscape) space, but after the 90° CW rotation into
                    // portrait their labels shifted cyclically by one (what
                    // was landscape-TL is now portrait-TR, etc.). If we
                    // handed them to the warp as-is it would rotate the
                    // output 90° CCW, which is exactly the bug we're fixing.
                    val scaled = preDetectedNormalized.map {
                        PointF(it.x * bmp.width, it.y * bmp.height)
                    }
                    orderTlTrBrBl(scaled)
                }
                else -> detector.detectQuadrilateral(bmp)
            }
            val initial = auto ?: fullImageCorners(bmp.width, bmp.height)
            _state.value = EdgeDetectUiState(
                bitmap = bmp,
                corners = initial,
                detectedAutomatically = auto != null,
                isLoading = false,
            )
        }
    }

    /** Sort 4 arbitrary points into canonical TL/TR/BR/BL by sum/diff of x,y. */
    private fun orderTlTrBrBl(pts: List<PointF>): List<PointF> {
        val sums = pts.sortedBy { it.x + it.y }
        val diffs = pts.sortedBy { it.x - it.y }
        return listOf(sums.first(), diffs.last(), sums.last(), diffs.first())
    }

    fun updateCorners(new: List<PointF>) {
        _state.value = _state.value.copy(corners = new)
    }

    /** Default quadrilateral = full image corners (per the "draggable corners
     *  default at screen corners" UX the user requested for manual mode).
     *  When detection actually fails in auto mode, this also provides a
     *  graceful fallback the user can drag inward. */
    private fun fullImageCorners(w: Int, h: Int): List<PointF> = listOf(
        PointF(0f, 0f),
        PointF(w.toFloat(), 0f),
        PointF(w.toFloat(), h.toFloat()),
        PointF(0f, h.toFloat()),
    )

    @AssistedFactory
    interface Factory {
        fun create(
            capturedFilePath: String,
            autoDetect: Boolean,
            preDetectedNormalized: List<PointF>?,
        ): EdgeDetectViewModel
    }
}
