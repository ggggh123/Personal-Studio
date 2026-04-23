package com.example.personal_studio.feature.scanner.enhance

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.scanner.BitmapIo
import com.example.personal_studio.data.scanner.EnhancePipeline
import com.example.personal_studio.domain.model.ScanFilter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class EnhanceReviewUiState(
    // GRAYSCALE is our CamScanner-style "enhanced+sharp" mode — the sensible
    // default for document scans (keeps gray levels so small fonts survive).
    val currentFilter: ScanFilter = ScanFilter.GRAYSCALE,
    val displayedBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val warpedColor: Bitmap? = null,  // the common pre-filter base (COLOR output)
)

@HiltViewModel(assistedFactory = EnhanceReviewViewModel.Factory::class)
class EnhanceReviewViewModel @AssistedInject constructor(
    @Assisted private val capturedFilePath: String,
    @Assisted private val cornersBitmapPx: List<PointF>,
    private val pipeline: EnhancePipeline,
) : ViewModel() {

    private val cache = mutableMapOf<ScanFilter, Bitmap>()
    private val _state = MutableStateFlow(EnhanceReviewUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = BitmapIo.decodeDownscaled(File(capturedFilePath))
            val warped = pipeline.warpAndFilter(raw, cornersBitmapPx, ScanFilter.COLOR)
            cache[ScanFilter.COLOR] = warped
            val initial = pipeline.applyFilter(warped, ScanFilter.GRAYSCALE)
            cache[ScanFilter.GRAYSCALE] = initial
            _state.value = EnhanceReviewUiState(
                currentFilter = ScanFilter.GRAYSCALE,
                displayedBitmap = initial,
                isLoading = false,
                warpedColor = warped,
            )
        }
    }

    fun selectFilter(filter: ScanFilter) = viewModelScope.launch {
        val cached = cache[filter]
        if (cached != null) {
            _state.value = _state.value.copy(
                currentFilter = filter,
                displayedBitmap = cached,
                isLoading = false,
            )
            return@launch
        }
        _state.value = _state.value.copy(currentFilter = filter, isLoading = true)
        val warped = _state.value.warpedColor ?: return@launch
        val bmp = pipeline.applyFilter(warped, filter)
        cache[filter] = bmp
        _state.value = _state.value.copy(displayedBitmap = bmp, isLoading = false)
    }

    @AssistedFactory
    interface Factory {
        fun create(capturedFilePath: String, cornersBitmapPx: List<PointF>): EnhanceReviewViewModel
    }
}
