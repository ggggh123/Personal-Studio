package com.example.personal_studio.feature.chat.ui

import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary

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

    TerminalBottomSheet(onDismiss = onDismiss, header = "添加图片") {
        com.example.personal_studio.ui.components.SheetAction(
            label = "从相册选取",
            hint = "选好后可以裁剪",
            icon = androidx.compose.material.icons.Icons.Outlined.PhotoLibrary,
            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        com.example.personal_studio.ui.components.SheetAction(
            label = "拍一张",
            hint = "用扫描相机拍，自动识别纸张并矫正",
            icon = androidx.compose.material.icons.Icons.Outlined.PhotoCamera,
            onClick = { onRequestQuickCapture(); onDismiss() },
        )
        com.example.personal_studio.ui.components.SheetAction(
            label = "从扫描文档选取",
            hint = "用已经扫描过的页面",
            icon = androidx.compose.material.icons.Icons.Outlined.DocumentScanner,
            onClick = { onRequestPickFromScans(); onDismiss() },
        )
    }
}
