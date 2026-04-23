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
    private val detector: EdgeDetector,
) : ViewModel() {
    private val _state = MutableStateFlow(EdgeDetectUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bmp = BitmapIo.decodeDownscaled(File(capturedFilePath))
            val auto = detector.detectQuadrilateral(bmp)
            val initial = auto ?: defaultInsetCorners(bmp.width, bmp.height)
            _state.value = EdgeDetectUiState(
                bitmap = bmp,
                corners = initial,
                detectedAutomatically = auto != null,
                isLoading = false,
            )
        }
    }

    fun updateCorners(new: List<PointF>) {
        _state.value = _state.value.copy(corners = new)
    }

    private fun defaultInsetCorners(w: Int, h: Int): List<PointF> {
        val ix = w * 0.1f
        val iy = h * 0.1f
        return listOf(
            PointF(ix, iy),
            PointF(w - ix, iy),
            PointF(w - ix, h - iy),
            PointF(ix, h - iy),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(capturedFilePath: String): EdgeDetectViewModel
    }
}
