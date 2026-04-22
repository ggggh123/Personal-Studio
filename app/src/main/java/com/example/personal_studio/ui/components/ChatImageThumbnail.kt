package com.example.personal_studio.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Rule

/**
 * Decodes and displays an image file as a framed thumbnail. Missing-file or failed
 * decode renders nothing (caller can wrap in a Box if a placeholder is desired).
 *
 * Decoding runs once per distinct [path] thanks to [remember] keying — safe for
 * LazyColumn rows that recompose often.
 */
@Composable
fun ChatImageThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 180.dp,
) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .heightIn(max = maxHeight)
            .border(1.dp, Rule),
    )
}

/**
 * A small square crop-style thumbnail — used in the input preview row before sending.
 */
@Composable
fun ChatImageThumbnailSmall(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    } ?: return

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .sizeIn(minWidth = size, minHeight = size, maxWidth = size, maxHeight = size)
            .border(1.dp, Rule),
    )
}
