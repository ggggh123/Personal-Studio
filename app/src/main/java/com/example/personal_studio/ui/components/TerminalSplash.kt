package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

@Composable
fun TerminalSplash() {
    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PERSONAL // STUDIO", color = Phosphor, style = MaterialTheme.typography.displayMedium)
            Text("booting…", color = FoamDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}
