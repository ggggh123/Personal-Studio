package com.example.personal_studio.feature.scanner.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ScanDocument
import com.example.personal_studio.domain.model.SortMode
import com.example.personal_studio.ui.components.ScanThumbnail
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.placeholder.ScannerPlaceholder
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * /scans tab content. Displays a sortable list of ScanDocuments streamed
 * from the repository, plus a `[+ new scan]` trailing action that routes
 * to DocumentBuilder. When the library is empty, falls back to the
 * existing ScannerPlaceholder for continuity.
 *
 * Cover thumbnails aren't denormalised onto ScanDocument yet, so rows
 * render an empty ScanThumbnail rectangle. Future work: cache the cover
 * path on ScanDocumentEntity at finalize time (plan §18 note A).
 *
 * Long-press on a row opens an action dialog. Pending docs expose
 * `[resume]` / `[discard]`; completed docs expose `[rename]` / `[delete]`.
 */
@Composable
fun ScanLibraryScreen(
    onOpenDoc: (docId: Long) -> Unit,
    onResumeDoc: (docId: Long) -> Unit,
    onNewDoc: () -> Unit,
) {
    val vm: ScanLibraryViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    var actionTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var renameTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocument?>(null) }

    Column(Modifier.fillMaxSize().background(Void)) {
        TerminalTopBar(
            route = "scans",
            subtitle = buildSortSubtitle(state.sort),
            trailing = {
                Text(
                    "[+ new scan]",
                    color = Phosphor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp).clickable(onClick = onNewDoc),
                )
            },
        )
        SortToolbar(current = state.sort, onSelect = vm::setSort)
        if (state.docs.isEmpty()) {
            ScannerPlaceholder()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp, vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.docs, key = { it.id }) { doc ->
                    DocRow(
                        doc,
                        onClick = { onOpenDoc(doc.id) },
                        onLongPress = { actionTarget = doc },
                    )
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Rule),
                    )
                }
            }
        }
    }

    actionTarget?.let { doc ->
        DocActionsDialog(
            doc = doc,
            onDismiss = { actionTarget = null },
            onResume = { actionTarget = null; onResumeDoc(doc.id) },
            onDiscard = { actionTarget = null; vm.onDelete(doc.id) },
            onRename = { actionTarget = null; renameTarget = doc },
            onDelete = { actionTarget = null; deleteTarget = doc },
        )
    }
    renameTarget?.let { doc ->
        RenameDialog(
            initial = doc.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                renameTarget = null
                vm.onRename(doc.id, newTitle)
            },
        )
    }
    deleteTarget?.let { doc ->
        DeleteConfirmDialog(
            title = doc.title,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                vm.onDelete(doc.id)
            },
        )
    }
}

@Composable
private fun SortToolbar(current: SortMode, onSelect: (SortMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(
            SortMode.TIME_DESC to "time",
            SortMode.ALPHA_ASC to "alpha",
            SortMode.RECENT_UPDATED to "recent",
        ).forEach { (mode, label) ->
            val active = mode == current
            Text(
                text = if (active) "[$label]" else label,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) Phosphor else FoamDim,
                modifier = Modifier.clickable { onSelect(mode) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    doc: ScanDocument,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Placeholder thumbnail (no cover path denormalised yet).
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
                text = "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} · ${formatTs(doc.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )
        }
    }
}

@Composable
private fun DocActionsDialog(
    doc: ScanDocument,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val pending = doc.isPending
    AlertDialog(
        containerColor = Void,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pending) "[incomplete] ${doc.title}" else doc.title,
                color = if (pending) Amber else Foam,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        text = {
            Text(
                text = if (pending)
                    "resume the in-progress scan, or discard its captured pages"
                else
                    "pick an action for this scan",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = if (pending) onResume else onRename) {
                Text(
                    if (pending) "[ resume ]" else "[ rename ]",
                    color = Phosphor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = if (pending) onDiscard else onDelete) {
                Text(
                    if (pending) "[ discard ]" else "[ delete ]",
                    color = Amber,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        containerColor = Void,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "rename scan",
                color = Foam,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) onConfirm(trimmed) else onDismiss()
            }) {
                Text(
                    "[ ok ]",
                    color = Phosphor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "[ cancel ]",
                    color = FoamDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        containerColor = Void,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "delete $title?",
                color = Amber,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        text = {
            Text(
                "this cannot be undone.",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "[ delete ]",
                    color = Amber,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "[ cancel ]",
                    color = FoamDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

private fun buildSortSubtitle(current: SortMode): String = buildString {
    append("# sort: ")
    listOf(
        SortMode.TIME_DESC to "time",
        SortMode.ALPHA_ASC to "alpha",
        SortMode.RECENT_UPDATED to "recent",
    ).forEach { (m, label) ->
        if (m == current) append("[$label] ") else append("$label ")
    }
}

private fun formatTs(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(ts))
