package com.example.personal_studio.feature.scanner.chat

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.scanner.camera.CameraCaptureScreen
import com.example.personal_studio.feature.scanner.edge.EdgeDetectAndCropScreen
import com.example.personal_studio.feature.scanner.enhance.EnhanceReviewScreen
import java.io.File

/**
 * State machine for the chat-side capture wrapper. Same Camera → Edge →
 * Enhance pattern as DocumentBuilderScreen's in-doc capture, but the
 * final confirm delegates to QuickCaptureForChatViewModel which writes a
 * standalone output (no ScanDocument row unless save-to-lib is ticked).
 */
private sealed interface ChatCaptureStep {
    data class Capturing(val nonce: Int) : ChatCaptureStep
    data class EdgeDetect(
        val tmp: String,
        val autoDetect: Boolean,
        val liveNorm: List<PointF>?,
    ) : ChatCaptureStep
    data class Enhance(val tmp: String, val corners: List<PointF>) : ChatCaptureStep
}

/**
 * Camera → Edge → Enhance wrapper for the chat `--from-camera` attachment
 * flow. The Enhance sub-screen gets `showSaveToLibCheckbox = true` so the
 * user can opt to keep the capture as a 1-page scan document in addition
 * to sending it as a chat attachment.
 *
 * On final confirm, the VM warps + filters into a scratch dir, copies the
 * enhanced JPEG into `filesDir/chat-attachments/img_<ts>.jpg`, optionally
 * creates a library doc, then fires [onImagePicked] with the chat path —
 * same callback shape as the gallery picker in AttachmentSheet so the
 * caller can hand it straight to the crop overlay.
 */
@Composable
fun QuickCaptureForChatScreen(
    onImagePicked: (path: String) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: QuickCaptureForChatViewModel = hiltViewModel()
    val context = LocalContext.current
    val tmpDir = remember { File(context.filesDir, "scans/tmp").apply { mkdirs() } }
    var step by remember { mutableStateOf<ChatCaptureStep>(ChatCaptureStep.Capturing(0)) }

    val pickedPath by vm.pickedPath.collectAsStateWithLifecycle()
    LaunchedEffect(pickedPath) {
        pickedPath?.let {
            onImagePicked(it)
            vm.clearPicked()
        }
    }

    when (val s = step) {
        is ChatCaptureStep.Capturing -> CameraCaptureScreen(
            outputDir = tmpDir,
            onCaptured = { file, auto, liveNorm ->
                step = ChatCaptureStep.EdgeDetect(file.absolutePath, auto, liveNorm)
            },
            onCancel = onCancel,
        )
        is ChatCaptureStep.EdgeDetect -> EdgeDetectAndCropScreen(
            capturedFilePath = s.tmp,
            autoDetect = s.autoDetect,
            preDetectedNormalized = s.liveNorm,
            onConfirm = { corners -> step = ChatCaptureStep.Enhance(s.tmp, corners) },
            onRetake = { step = ChatCaptureStep.Capturing(System.identityHashCode(step)) },
        )
        is ChatCaptureStep.Enhance -> EnhanceReviewScreen(
            capturedFilePath = s.tmp,
            cornersBitmapPx = s.corners,
            showSaveToLibCheckbox = true,
            onConfirm = { filter, saveToLib ->
                vm.finalize(File(s.tmp), s.corners, filter, saveToLib)
            },
            onCancel = { step = ChatCaptureStep.Capturing(System.identityHashCode(step)) },
        )
    }
}

