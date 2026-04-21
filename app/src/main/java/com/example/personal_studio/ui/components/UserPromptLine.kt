package com.example.personal_studio.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim

/**
 * A user-message row. Renders as `user@study:~$ <message>` with colored prompt parts.
 */
@Composable
fun UserPromptLine(
    text: String,
    modifier: Modifier = Modifier,
    imageThumb: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        if (imageThumb != null) {
            imageThumb()
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Amber)) { append("user@study") }
                withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                withStyle(SpanStyle(color = Foam)) { append(text) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
