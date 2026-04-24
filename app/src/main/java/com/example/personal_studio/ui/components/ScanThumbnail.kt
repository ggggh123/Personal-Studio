package com.example.personal_studio.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Rule

/**
 * Decode-sampled thumbnail for a scan page. Uses BitmapFactory.Options
 * inSampleSize so row-render cost stays near-constant regardless of the
 * underlying enhanced JPEG's native resolution.
 */
@Composable
fun ScanThumbnail(path: String?, width: Dp = 44.dp, height: Dp = 56.dp) {
    val bmp = path?.let {
        remember(path) {
            runCatching {
                BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply { inSampleSize = 4 },
                )
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(width, height)
            .border(1.dp, Rule)
            .background(Deep),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width, height),
            )
        } else {
            Text("?", color = FoamDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
