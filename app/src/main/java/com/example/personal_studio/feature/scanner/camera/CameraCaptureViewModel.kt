package com.example.personal_studio.feature.scanner.camera

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.scanner.EdgeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class CameraCaptureUiState(
    /** When true, an ImageAnalysis pipeline runs corner detection on each
     *  preview frame and [liveCorners] are drawn over the preview. When false,
     *  no analysis runs and post-capture the quadrilateral defaults to the
     *  full image corners for manual adjustment. */
    val autoDetect: Boolean = true,
    /** Torch (flash) on/off. */
    val flashOn: Boolean = false,
    /** Last corners the analyzer produced, in analyzer-frame coordinate space.
     *  Null means no recent valid detection (or autoDetect is off). */
    val liveCorners: List<PointF>? = null,
    /** Dimensions of the analyzer frame that [liveCorners] is relative to —
     *  used by the preview overlay to map into Compose-px coords. */
    val analyzedWidth: Int = 0,
    val analyzedHeight: Int = 0,
)

@HiltViewModel
class CameraCaptureViewModel @Inject constructor(
    private val detector: EdgeDetector,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraCaptureUiState())
    val state = _state.asStateFlow()

    // Drops frames while a previous inference is still running. Critical for
    // live preview: ImageAnalysis delivers at ~30 FPS but our ONNX detector
    // takes ~270 ms, so without back-pressure we'd queue dozens of stale
    // frames behind a single in-flight inference.
    private val inFlight = AtomicBoolean(false)

    fun setAutoDetect(enabled: Boolean) {
        _state.value = _state.value.copy(
            autoDetect = enabled,
            // Clear stale overlay when disabling.
            liveCorners = if (enabled) _state.value.liveCorners else null,
        )
    }

    fun setFlash(enabled: Boolean) {
        _state.value = _state.value.copy(flashOn = enabled)
    }

    /**
     * Called from the ImageAnalysis analyzer on the analyzer thread. If an
     * inference is already running, drops this frame. Otherwise kicks off a
     * detection on Dispatchers.Default and updates [state.liveCorners] when
     * it finishes.
     */
    fun analyzeFrame(bitmap: Bitmap) {
        if (!_state.value.autoDetect) return
        if (!inFlight.compareAndSet(false, true)) return
        val w = bitmap.width
        val h = bitmap.height
        viewModelScope.launch {
            try {
                val corners = withContext(Dispatchers.Default) {
                    detector.detectQuadrilateral(bitmap)
                }
                _state.value = _state.value.copy(
                    liveCorners = corners,
                    analyzedWidth = w,
                    analyzedHeight = h,
                )
            } finally {
                inFlight.set(false)
            }
        }
    }
}
