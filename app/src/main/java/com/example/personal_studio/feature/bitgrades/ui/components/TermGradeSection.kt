package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

@Composable
fun TermGradeSection(term: TermGrades, initiallyExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Text(if (expanded) "▾ ${term.termName}" else "▸ ${term.termName}", color = Phosphor)
            Spacer(Modifier.weight(1f))
            Text("GPA ${String.format(Locale.US, "%.2f", term.weightedGpa)}", color = FoamMute)
            term.rank?.let { r ->
                r.majorRank?.let { Text("  专业$it/${r.majorTotal}", color = FoamMute) }
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                term.courses.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp)) {
                        Text(c.courseName, color = if (!c.isPass) Carmine else Foam,
                            modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1f", c.credit), color = FoamMute,
                            modifier = Modifier.width(40.dp))
                        Text(c.score, color = if (!c.isPass) Carmine else Foam,
                            modifier = Modifier.width(48.dp))
                        if (!c.isPass) Text("⚠挂科", color = Carmine)
                        else if (c.attemptType != "正常") Text(c.attemptType, color = Amber)
                    }
                }
            }
        }
    }
}
