package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Foam

/**
 * Terminal-style top bar. Renders `studio:~/<route> $` plus optional trailing action.
 * Bottom edge is a 1dp dashed phosphor line.
 */
@Composable
fun TerminalTopBar(
    route: String,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                val y = size.height
                drawLine(
                    color = Phosphor,
                    start = Offset(0f, y - 0.5f),
                    end = Offset(size.width, y - 0.5f),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("studio:") }
                withStyle(SpanStyle(color = Foam)) { append("~/") }
                withStyle(SpanStyle(color = Foam)) { append(route) }
                append(" ")
                withStyle(SpanStyle(color = Phosphor)) { append("$") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Box { trailing?.invoke() }
    }
}
