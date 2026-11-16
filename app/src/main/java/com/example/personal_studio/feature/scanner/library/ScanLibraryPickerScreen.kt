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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.personal_studio.domain.model.ScanDocumentSummary
import com.example.personal_studio.domain.model.ScanPage
import com.example.personal_studio.feature.scanner.scanFilterLabel
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.domain.model.SortMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import java.io.File

/**
 * 问答 → 附件 → 从扫描文档选取: the documents as covers (tap → its pages), then the pages
 * (tap → the enhanced JPEG is copied to chat-attachments and handed to [onPagePicked]).
 * Reuses [ScanLibraryViewModel], without the new-scan card and the long-press menu.
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
            title = state.docs.firstOrNull { it.id == current }?.title ?: "选择页面",
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
    docs: List<ScanDocumentSummary>,
    onTapDoc: (ScanDocumentSummary) -> Unit,
    onCancel: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    Column(
        Modifier
            .fillMaxSize()
            .background(Tg.colors.bg)
            .navigationBarsPadding(),
    ) {
        TerminalTopBar(route = "从扫描文档选取", subtitle = "从扫描文档选取\n选一份文档，再点一页", onBack = onCancel)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (docs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TgEmptyState("还没有扫描文档", "在 资料 → 扫描文档 里新建扫描后，就能从这里选取页面。")
                }
            }
            items(docs, key = { it.id }) { doc ->
                ScanDocCover(doc = doc, meta = scanDocMeta(doc, SortMode.TIME_DESC, now), onClick = { onTapDoc(doc) })
            }
        }
    }
}

@Composable
private fun PageGrid(
    docId: Long,
    title: String,
    onTapPage: (ScanPage) -> Unit,
    onBack: () -> Unit,
) {
    val c = Tg.colors
    val pagerVm: ScanPagePickerViewModel = hiltViewModel(
        key = "picker-$docId",
        creationCallback = { f: ScanPagePickerViewModel.Factory -> f.create(docId) },
    )
    val pages by pagerVm.pages.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .navigationBarsPadding(),
    ) {
        TerminalTopBar(route = title, subtitle = "$title\n点一页加到提问里", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(pages, key = { it.id }) { page ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "选取第 ${page.ordinal + 1} 页") { onTapPage(page) },
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.74f)) {
                        ScanThumbnail(path = page.enhancedImagePath, width = maxWidth, height = maxHeight, corner = 12.dp)
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .size(26.dp)
                                .background(c.text, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${page.ordinal + 1}", style = MaterialTheme.typography.labelSmall, color = c.bg)
                        }
                    }
                    Text(
                        scanFilterLabel(page.filter),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
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
