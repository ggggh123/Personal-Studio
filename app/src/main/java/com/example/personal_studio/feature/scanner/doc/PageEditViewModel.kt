package com.example.personal_studio.feature.scanner.doc

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ScanRepository
import com.example.personal_studio.data.scanner.BitmapIo
import com.example.personal_studio.data.scanner.EnhancePipeline
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.domain.scanner.CaptureAndEnhancePageUseCase
import com.example.personal_studio.domain.scanner.RecapturePageUseCase
import com.example.personal_studio.domain.scanner.RemovePageUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PageEditUiState(
    val page: ScanPage? = null,
    val currentFilter: ScanFilter = ScanFilter.COLOR,
    val displayedBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
)

/**
 * Edits a single scan page: swap filter, retake, or delete.
 *
 * The page's `originalImagePath` on disk is the COLOR-warped (un-filtered)
 * JPEG saved at capture time — so filter swaps re-apply on that cached
 * warp in memory. Confirming writes out a new cooked JPEG (or reuses the
 * original when the new filter is COLOR) and calls `updatePageFilter`;
 * the prior cooked file, if distinct, is deleted to avoid disk leak.
 *
 * Retake hosts the Camera → Edge → Enhance sub-flow from the screen layer
 * and funnels the new capture through [recapture], which reuses the
 * existing CaptureAndEnhancePageUseCase to write orig+cooked into the
 * doc's dir, then swaps the page's paths via RecapturePageUseCase.
 */
@HiltViewModel(assistedFactory = PageEditViewModel.Factory::class)
class PageEditViewModel @AssistedInject constructor(
    @Assisted private val pageId: Long,
    private val repo: ScanRepository,
    private val pipeline: EnhancePipeline,
    private val captureAndEnhance: CaptureAndEnhancePageUseCase,
    private val recapturePageUc: RecapturePageUseCase,
    private val removePageUc: RemovePageUseCase,
) : ViewModel() {

    // Cache by filter. Since the on-disk `originalImagePath` is a 2000-long-
    // side COLOR warp, we don't need the HIGH/LOW dual-resolution dance that
    // EnhanceReviewViewModel has for fresh captures — everything is already
    // at BW-safe size.
    private val cache = mutableMapOf<ScanFilter, Bitmap>()
    private var warpedColor: Bitmap? = null

    private val _state = MutableStateFlow(PageEditUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val page = repo.getPage(pageId) ?: return@launch
            val color = BitmapIo.decodeDownscaled(File(page.originalImagePath))
            warpedColor = color
            cache[ScanFilter.COLOR] = color
            val initial = if (page.filter == ScanFilter.COLOR) {
                color
            } else {
                pipeline.applyFilter(color, page.filter).also { cache[page.filter] = it }
            }
            _state.value = PageEditUiState(
                page = page,
                currentFilter = page.filter,
                displayedBitmap = initial,
                isLoading = false,
            )
        }
    }

    fun selectFilter(filter: ScanFilter) = viewModelScope.launch {
        cache[filter]?.let {
            _state.value = _state.value.copy(currentFilter = filter, displayedBitmap = it, isLoading = false)
            return@launch
        }
        _state.value = _state.value.copy(currentFilter = filter, isLoading = true)
        val base = warpedColor ?: return@launch
        val bmp = pipeline.applyFilter(base, filter)
        cache[filter] = bmp
        _state.value = _state.value.copy(displayedBitmap = bmp, isLoading = false)
    }

    /**
     * Persist the currently-displayed filter choice. No-op when the user
     * hasn't actually changed filter. Cleans up the prior cooked JPEG when
     * the new target path differs from the previous enhancedImagePath.
     */
    fun confirmFilterChange() = viewModelScope.launch {
        val page = _state.value.page ?: return@launch
        val filter = _state.value.currentFilter
        if (filter == page.filter) return@launch
        val bmp = cache[filter] ?: return@launch
        val dir = File(page.originalImagePath).parentFile ?: return@launch
        val newEnhancedPath = if (filter == ScanFilter.COLOR) {
            // No separate cooked file for COLOR — point enhanced back at the
            // original warp on disk.
            page.originalImagePath
        } else {
            val stem = UUID.randomUUID().toString().take(8)
            BitmapIo.writeJpeg(bmp, File(dir, "page-$stem-cooked.jpg")).absolutePath
        }
        if (page.enhancedImagePath != page.originalImagePath &&
            page.enhancedImagePath != newEnhancedPath
        ) {
            runCatching { File(page.enhancedImagePath).delete() }
        }
        repo.updatePageFilter(pageId, newEnhancedPath, filter)
    }

    fun deletePage() = viewModelScope.launch { removePageUc(pageId) }

    /**
     * Completes the retake sub-flow: warps+filters the new tmp capture and
     * swaps the page's paths via RecapturePageUseCase, which cleans up the
     * old files.
     */
    fun recapture(
        tmpFile: File,
        corners: List<PointF>,
        filter: ScanFilter,
    ) = viewModelScope.launch {
        val page = _state.value.page ?: return@launch
        val result = captureAndEnhance(page.docId, tmpFile, corners, filter)
        recapturePageUc(
            page.id,
            result.originalImagePath,
            result.enhancedImagePath,
            result.filter,
            result.cornersJson,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(pageId: Long): PageEditViewModel
    }
}
