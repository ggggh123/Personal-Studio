package com.example.personal_studio.feature.timeline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.timeline.vm.CourseSeriesListViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun CourseSeriesListScreen(
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    vm: CourseSeriesListViewModel = hiltViewModel(),
) {
    val series by vm.series.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "courses", subtitle = "$ ls courses/", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        if (series.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("no courses yet", color = FoamDim)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                items(series, key = { it.seriesId }) { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpenSeries(s.seriesId) },
                    ) {
                        Text(s.title, color = Phosphor, modifier = Modifier.weight(1f))
                        Text("${s.occurrenceCount} 节  · 第 ${s.minWeek}-${s.maxWeek} 周",
                            color = Foam, style = MaterialTheme.typography.labelSmall)
                    }
                    Divider(color = FoamDim)
                }
            }
        }
    }
}
