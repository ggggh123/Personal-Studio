package com.example.personal_studio.feature.scanner.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import java.io.File

/**
 * Modal scan-library variant for chat `--from-scans`. Two internal
 * states: the doc list (tap → drills in) and the selected doc's page
 * grid (tap a page → copies its cooked JPEG to chat-attachments and
 * fires [onPagePicked]).
 *
 * Reuses [ScanLibraryViewModel] so sort state and streaming are
 * identical to the main library; the no-ops for `[+ new scan]` and
 * long-press menu keep the picker focused on the pick gesture.
 */
@Composable
fun ScanLibraryPickerScreen(
    onPagePicked: (path: String) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: ScanLibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var selectedDocId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    val current = selectedDocId
    if (current == null) {
        DocList(
            docs = state.docs,
            onTapDoc = { selectedDocId = it.id },
            onCancel = onCancel,
        )
    } else {
        PageGrid(
            docId = current,
            onTapPage = { page ->
                val path = copyCookedToChat(context, page)
                if (path != null) onPagePicked(path)
            },
            onBack = { selectedDocId = null },
        )
    }
}

@Composable
private fun DocList(
    docs: List<ScanDocument>,
    onTapDoc: (ScanDocument) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .navigationBarsPadding(),
    ) {
        TerminalTopBar(
            route = "scans/pick",
            subtitle = "# pick a doc, then a page",
            trailing = {
                Text(
                    "[x cancel]",
                    color = Carmine,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clickable(onClick = onCancel),
                )
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(docs, key = { it.id }) { doc ->
                PickerDocRow(doc = doc, onClick = { onTapDoc(doc) })
                Spacer(
                    Modifier.fillMaxWidth().height(1.dp).background(Rule),
                )
            }
        }
    }
}

@Composable
private fun PickerDocRow(doc: ScanDocument, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanThumbnail(path = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) { append("drwx── ") }
                    if (doc.isPending) {
                        withStyle(SpanStyle(color = Amber)) { append("[incomplete] ") }
                    }
                    withStyle(SpanStyle(color = Foam)) { append(doc.title) }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )
        }
    }
}

@Composable
private fun PageGrid(
    docId: Long,
    onTapPage: (ScanPage) -> Unit,
    onBack: () -> Unit,
) {
    val pagerVm: ScanPagePickerViewModel = hiltViewModel(
        key = "picker-$docId",
        creationCallback = { f: ScanPagePickerViewModel.Factory -> f.create(docId) },
    )
    val pages by pagerVm.pages.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .navigationBarsPadding(),
    ) {
        TerminalTopBar(
            route = "scans/pick/$docId",
            subtitle = "# tap a page to attach",
            trailing = {
                Text(
                    "[< back]",
                    color = FoamMute,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clickable(onClick = onBack),
                )
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(pages, key = { it.id }) { page ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onTapPage(page) },
                ) {
                    ScanThumbnail(
                        path = page.enhancedImagePath,
                        width = 140.dp,
                        height = 180.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "p.${page.ordinal + 1} · ${page.filter.name.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoamMute,
                    )
                }
            }
        }
    }
}

private fun copyCookedToChat(context: android.content.Context, page: ScanPage): String? {
    val src = File(page.enhancedImagePath)
    if (!src.exists()) return null
    val chatDir = File(context.filesDir, "chat-attachments").apply { mkdirs() }
    val dest = File(chatDir, "img_${System.currentTimeMillis()}.jpg")
    return runCatching { src.copyTo(dest, overwrite = true).absolutePath }.getOrNull()
}
