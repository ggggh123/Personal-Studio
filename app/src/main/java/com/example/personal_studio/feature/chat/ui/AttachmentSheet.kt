package com.example.personal_studio.feature.chat.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.TerminalBottomSheet
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import java.io.File
import java.io.FileOutputStream

/**
 * Copies the picked content Uri into a file under `filesDir/chat-attachments/` so our
 * GeminiProvider (which reads raw bytes) can access it. Returns absolute path or null on failure.
 */
private fun Context.copyUriToFile(uri: Uri): String? {
    val dir = File(filesDir, "chat-attachments").apply { mkdirs() }
    val dest = File(dir, "img_${System.currentTimeMillis()}.jpg")
    return try {
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { out -> input.copyTo(out) }
        }
        dest.absolutePath
    } catch (t: Throwable) { null }
}

@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onImagePicked: (path: String) -> Unit,
    onRequestQuickCapture: () -> Unit = {},
    onRequestPickFromScans: () -> Unit = {},
) {
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = context.copyUriToFile(uri)
            if (path != null) onImagePicked(path)
        }
        onDismiss()
    }

    TerminalBottomSheet(onDismiss = onDismiss, header = "attach") {
        Option(line = "--from-gallery     从相册选取", onClick = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        Option(line = "--from-camera      拍一张新照片(扫描)", onClick = { onRequestQuickCapture(); onDismiss() })
        Option(line = "--from-scans       从扫描库选取", onClick = { onRequestPickFromScans(); onDismiss() })
    }
}

@Composable
private fun Option(line: String, onClick: () -> Unit, disabled: Boolean = false) {
    val color = if (disabled) FoamMute else Cyan
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (disabled) Modifier else Modifier.clickable(onClick = onClick))
        .padding(vertical = 10.dp)

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = FoamDim)) { append("▸ ") }
            withStyle(SpanStyle(color = color)) { append(line) }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = rowModifier,
    )
}
