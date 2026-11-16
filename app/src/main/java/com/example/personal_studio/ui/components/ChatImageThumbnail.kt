package com.example.personal_studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Tg

/**
 * An image attached to a chat message, in a rounded frame. Decoded off the main thread at
 * display size (photos straight from the camera are large); fades in once ready.
 */
@Composable
fun ChatImageThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 180.dp,
) {
    val c = Tg.colors
    val target = with(LocalDensity.current) { maxHeight.roundToPx() }
    val bitmap = rememberSampledBitmap(path, target)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .sizeIn(minWidth = 72.dp, minHeight = 72.dp, maxHeight = maxHeight, maxWidth = 240.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape),
    ) {
        AnimatedVisibility(visible = bitmap != null, enter = fadeIn(tween(220))) {
            bitmap?.let {
                Image(bitmap = it, contentDescription = "图片", contentScale = ContentScale.Fit, modifier = Modifier.sizeIn(maxHeight = maxHeight, maxWidth = 240.dp))
            }
        }
    }
}

/** A square crop-style thumbnail, e.g. images waiting to be sent above the input. */
@Composable
fun ChatImageThumbnailSmall(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val c = Tg.colors
    val target = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = rememberSampledBitmap(path, target)
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.size(size).clip(shape).background(c.surface).border(1.dp, c.line, shape)) {
        bitmap?.let { Image(bitmap = it, contentDescription = "待发送的图片", contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
    }
}
