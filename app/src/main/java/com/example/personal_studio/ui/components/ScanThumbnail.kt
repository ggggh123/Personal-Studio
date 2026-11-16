package com.example.personal_studio.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/**
 * A scan page as a small sheet of paper: rounded, hairline border, decoded off the main
 * thread at display size; a document glyph stands in while loading or if the file is gone.
 */
@Composable
fun ScanThumbnail(path: String?, width: Dp = 44.dp, height: Dp = 56.dp, corner: Dp = 8.dp) {
    val c = Tg.colors
    val target = with(LocalDensity.current) { minOf(width, height).roundToPx() }
    val bitmap = rememberSampledBitmap(path, target)
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier.size(width, height).clip(shape).background(c.surface).border(1.dp, c.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(200), label = "thumb") { bmp ->
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(minOf(width, height) / 3))
                }
            }
        }
    }
}
