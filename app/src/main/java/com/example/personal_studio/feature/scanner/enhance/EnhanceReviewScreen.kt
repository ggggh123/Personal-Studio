package com.example.personal_studio.feature.scanner.enhance

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
import com.example.personal_studio.ui.components.rememberZoomableBoxState
import com.example.personal_studio.ui.components.zoomTransform
import com.example.personal_studio.ui.components.zoomable
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun EnhanceReviewScreen(
    capturedFilePath: String,
    cornersBitmapPx: List<PointF>,
    showSaveToLibCheckbox: Boolean = false,
    onConfirm: (filter: ScanFilter, saveToLib: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: EnhanceReviewViewModel = hiltViewModel(
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

    Column(Modifier.fillMaxSize().background(Void)) {
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

        // Filter toolbar + rotate button
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanFilter.entries.forEach { f ->
                val active = state.currentFilter == f
                Text(
                    "[${f.name.lowercase()}]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) Phosphor else FoamDim,
                    modifier = Modifier.clickable { vm.selectFilter(f) },
                )
            }
            Text(
                "[↻ rot]",
                style = MaterialTheme.typography.bodyMedium,
                color = FoamDim,
                modifier = Modifier.clickable { vm.rotateClockwise() },
            )
        }

        if (showSaveToLibCheckbox) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (saveToLib) "[x]" else "[ ]",
                    color = if (saveToLib) Phosphor else FoamDim,
                    modifier = Modifier.clickable { saveToLib = !saveToLib },
                )
                Spacer(Modifier.width(8.dp))
                Text("save to scans/", color = Foam, style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("[cancel]", color = FoamDim, modifier = Modifier.clickable(onClick = onCancel))
            Text(
                "[confirm ↵]",
                color = if (state.isLoading) FoamDim else Phosphor,
                modifier = Modifier.clickable(enabled = !state.isLoading) {
                    onConfirm(state.currentFilter, saveToLib)
                },
            )
        }
    }
}
