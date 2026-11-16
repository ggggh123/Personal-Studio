package com.example.personal_studio.feature.scanner.enhance

import com.example.personal_studio.ui.theme.Tg
import androidx.compose.material.icons.outlined.RotateRight
import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.personal_studio.ui.theme.DarkCanvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanFilter
import com.example.personal_studio.feature.scanner.scanFilterLabel
import com.example.personal_studio.ui.components.rememberZoomableBoxState
import com.example.personal_studio.ui.components.zoomTransform
import com.example.personal_studio.ui.components.zoomable

@Composable
fun EnhanceReviewScreen(
    capturedFilePath: String,
    cornersBitmapPx: List<PointF>,
    showSaveToLibCheckbox: Boolean = false,
    onConfirm: (filter: ScanFilter, saveToLib: Boolean) -> Unit,
    onCancel: () -> Unit,
) = DarkCanvas {

    // Same reasoning as EdgeDetectAndCropScreen: key by the tmp path so nested
    // use inside DocumentBuilder doesn't reuse VMs across captures.
    val vm: EnhanceReviewViewModel = hiltViewModel(
        key = capturedFilePath,
        creationCallback = { f: EnhanceReviewViewModel.Factory -> f.create(capturedFilePath, cornersBitmapPx) }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var saveToLib by remember { mutableStateOf(false) }
    val zoom = rememberZoomableBoxState()

    // Filter switches and rotations reset the zoom — rotation swaps W/H so a
    // lingering pan/scale from the previous orientation would feel disorienting.
    remember(state.currentFilter, state.rotationQuarters) {
        zoom.reset()
        state.currentFilter to state.rotationQuarters
    }

    Column(Modifier.fillMaxSize().background(Tg.colors.bg)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .zoomable(zoom),
            contentAlignment = Alignment.Center,
        ) {
            state.displayedBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomTransform(zoom),
                )
            }
        }

        if (showSaveToLibCheckbox) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "同时存入扫描文档",
                    color = com.example.personal_studio.ui.theme.Tg.colors.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                com.example.personal_studio.ui.components.TgSwitch(checked = saveToLib, onCheckedChange = { saveToLib = it })
            }
        }
        com.example.personal_studio.ui.components.PhotoActionBar(
            secondary = "重拍",
            onSecondary = onCancel,
            primary = if (state.isLoading) "处理中…" else "完成",
            onPrimary = { onConfirm(state.currentFilter, saveToLib) },
            primaryEnabled = !state.isLoading,
            above = {
                ScanFilter.entries.forEach { f ->
                    com.example.personal_studio.ui.components.PhotoChip(scanFilterLabel(f), selected = state.currentFilter == f, onClick = { vm.selectFilter(f) })
                }
                com.example.personal_studio.ui.components.PhotoIconButton(
                    androidx.compose.material.icons.Icons.Outlined.RotateRight, "顺时针旋转", onClick = { vm.rotateClockwise() },
                )
            },
        )
    }
}
