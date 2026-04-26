package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun KbSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("$ grep -r ") }
                withStyle(SpanStyle(color = Phosphor)) { append("\"") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
            cursorBrush = SolidColor(Phosphor),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("\"") }
                withStyle(SpanStyle(color = FoamDim)) { append(" kb/") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
