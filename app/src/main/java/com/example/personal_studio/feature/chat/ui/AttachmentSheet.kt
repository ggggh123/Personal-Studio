package com.example.personal_studio.feature.chat.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Void
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onImagePicked: (path: String) -> Unit,
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

    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Void,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Amber)) { append("user@study") }
                    withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                    withStyle(SpanStyle(color = Foam)) { append("attach --source") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))

            Option(
                line = "--from-gallery     open photo picker",
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Option(
                line = "--from-camera     take a new photo (P2 adds enhancement)",
                onClick = { /* deferred to P2 — scanner will own camera capture + enhancement */ },
                disabled = true,
            )
            Option(
                line = "--from-scans      pick from scan library (P2)",
                onClick = {},
                disabled = true,
            )
            Spacer(Modifier.height(8.dp))
        }
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
