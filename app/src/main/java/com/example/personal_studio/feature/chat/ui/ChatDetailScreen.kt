package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ChatMessage
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.MessageRole
import com.example.personal_studio.feature.chat.vm.ChatDetailViewModel
import com.example.personal_studio.feature.knowledge.ui.SavePreviewModal
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeViewModel
import com.example.personal_studio.feature.scanner.chat.QuickCaptureForChatScreen
import com.example.personal_studio.feature.scanner.library.ScanLibraryPickerScreen
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TypewriterText
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.Tg

/**
 * A conversation: the user's turns as bubbles on the right, answers on the page with the
 * model line above and time / actions below; the composer at the bottom. Photos come from
 * the gallery, the scan camera or the scan library, then pass through the crop overlay.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onNavigateToKbEntry: (Long) -> Unit,
    /** Open the photo-question camera right away (＋ → 拍题提问). */
    startWithQuickCapture: Boolean = false,
) {
    val vm: ChatDetailViewModel = hiltViewModel(
        creationCallback = { factory: ChatDetailViewModel.Factory -> factory.create(sessionId) }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val saveVm: SaveToKnowledgeViewModel = hiltViewModel()
    val saveState by saveVm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    val clipboard = LocalClipboardManager.current
    val toast = LocalShellToast.current
    // Source the save modal works with, so retry from its Error state re-runs the same one.
    val activeSourceState = remember { mutableStateOf<KbDraftSource?>(null) }
    val startSave: (KbDraftSource) -> Unit = { src ->
        activeSourceState.value = src
        saveVm.startDraft(src)
    }
    val listState = rememberLazyListState()

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var pickedForCrop by remember { mutableStateOf<String?>(null) }
    var showQuickCapture by rememberSaveable { mutableStateOf(startWithQuickCapture) }
    var showScansPicker by remember { mutableStateOf(false) }

    // Rendered heights of answers (the formula view is a WebView that reports its height
    // asynchronously); re-entering rows lay out at their last size instead of popping.
    val aiMessageHeights = remember { mutableStateMapOf<Long, Int>() }

    // Follow the newest turn only while the user is already at the bottom.
    val autoFollow by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf true
            lastVisible >= total - 1
        }
    }
    val lastMessageId = state.messages.lastOrNull()?.id
    val lastMessageHeight = lastMessageId?.let { aiMessageHeights[it] }
    LaunchedEffect(state.messages.size, state.streamingText?.length, lastMessageHeight) {
        if (!shouldAutoScroll(autoFollow, listState.isScrollInProgress)) return@LaunchedEffect
        val lastIndex = state.messages.size + (if (state.streamingText != null) 1 else 0)
        // Instant scroll: animating on every token would chase a moving target and shake.
        if (lastIndex > 0) listState.scrollToItem(lastIndex - 1, Int.MAX_VALUE)
    }

    // The page lifts the composer by the keyboard itself; the window must not pan (which hid
    // the header) or shrink differently per ROM.
    com.example.personal_studio.ui.components.KeyboardHandledByInsets()
    // If the keyboard opens while the newest turn is in view, keep it in view on every frame
    // of the keyboard animation (the list shrinks from the bottom as the composer rises).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ime = androidx.compose.foundation.layout.WindowInsets.ime
    val lastIndexNow by androidx.compose.runtime.rememberUpdatedState(state.messages.size + (if (state.streamingText != null) 1 else 0))
    val followNow by androidx.compose.runtime.rememberUpdatedState(autoFollow)
    LaunchedEffect(Unit) {
        var pinned = false
        androidx.compose.runtime.snapshotFlow { ime.getBottom(density) }.collect { bottom ->
            if (bottom == 0) pinned = false else if (!pinned) pinned = followNow
            if (pinned && lastIndexNow > 0) listState.scrollToItem(lastIndexNow - 1, Int.MAX_VALUE)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TerminalTopBar(
            route = "chat",
            subtitle = displayTitle(state.session?.title) + "\n" + state.activeModel,
            onBack = onBack,
            trailing = {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "把整段对话存入知识库") {
                            startSave(KbDraftSource.FromChatSession(sessionId = sessionId))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = "存入知识库", tint = c.text, modifier = Modifier.size(22.dp))
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty() && state.streamingText == null) {
                Column(Modifier.align(Alignment.Center).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("想问点什么？", style = MaterialTheme.typography.headlineLarge, color = c.text)
                    Text(
                        "直接输入问题，或点左下角的 ＋ 拍下题目。好的回答可以存进知识库。",
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                state = listState,
            ) {
                items(state.messages, key = { it.id }) { m ->
                    when (m.role) {
                        MessageRole.USER -> UserBubble(text = m.contentMarkdown, images = m.attachedImagePaths)
                        MessageRole.AI -> AiAnswer(
                            // The model that produced this reply, not the one active now.
                            model = m.modelUsed ?: state.activeModel,
                            footer = formatDoneLine(m),
                            onCopy = {
                                clipboard.setText(AnnotatedString(m.contentMarkdown))
                                toast?.show("已复制")
                            },
                            onSave = { startSave(KbDraftSource.FromChatMessage(sessionId = sessionId, aiMessageId = m.id)) },
                        ) {
                            MathMarkdownView(
                                markdown = m.contentMarkdown,
                                initialHeightDp = aiMessageHeights[m.id] ?: 60,
                                onHeightChanged = { h -> aiMessageHeights[m.id] = h },
                            )
                        }
                        MessageRole.SYSTEM -> Unit
                    }
                }
                if (state.streamingText != null) {
                    item(key = "__streaming__") {
                        AiAnswer(model = state.activeModel) {
                            if (state.streamingText.isNullOrEmpty()) {
                                ThinkingIndicator()
                            } else {
                                TypewriterText(
                                    text = state.streamingText ?: "",
                                    style = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                                )
                            }
                        }
                    }
                }
                item(key = "__end__") { Box(Modifier.padding(bottom = 12.dp)) }
            }
        }

        ChatErrorNote(state.errorBanner, onDismiss = vm::onDismissError)
        ChatComposer(
            input = state.input,
            images = state.attachedImagePaths,
            sending = state.isSending,
            canAttach = state.attachedImagePaths.size < ChatDetailViewModel.MAX_IMAGES,
            onInput = vm::onInputChanged,
            onAttach = { showAttachmentSheet = true },
            onRemoveImage = vm::onRemoveAttachedImage,
            onSend = vm::onSend,
            onStop = vm::onStop,
        )

        SavePreviewModal(
            state = saveState,
            onCancel = { saveVm.reset() },
            onConfirm = { draft -> saveVm.commit(draft) },
            onRetry = { activeSourceState.value?.let { saveVm.retry(it) } },
        )
        LaunchedEffect(saveState) {
            val s = saveState
            if (s is SaveToKnowledgeUiState.Saved) {
                onNavigateToKbEntry(s.entryId)
                saveVm.reset()
            }
        }
    }

    if (showAttachmentSheet) {
        AttachmentSheet(
            onDismiss = { showAttachmentSheet = false },
            onImagePicked = { path ->
                pickedForCrop = path
                showAttachmentSheet = false
            },
            onRequestQuickCapture = { showQuickCapture = true },
            onRequestPickFromScans = { showScansPicker = true },
        )
    }
    // Camera and scan-library takeovers end in the same crop overlay as the gallery.
    if (showQuickCapture) {
        QuickCaptureForChatScreen(
            onImagePicked = { path ->
                pickedForCrop = path
                showQuickCapture = false
            },
            onCancel = { showQuickCapture = false },
        )
    }
    if (showScansPicker) {
        ScanLibraryPickerScreen(
            onPagePicked = { path ->
                pickedForCrop = path
                showScansPicker = false
            },
            onCancel = { showScansPicker = false },
        )
    }
    val toCrop = pickedForCrop
    if (toCrop != null) {
        ImageCropOverlay(
            imagePath = toCrop,
            onDismiss = { pickedForCrop = null },
            onConfirm = { croppedPath ->
                pickedForCrop = null
                vm.onAttachImage(croppedPath)
            },
        )
    }
}

/** 仅当用户停在底部(atBottom)且**没在手动滑动**(userScrolling=false)时才自动钉底。
 *  手滑时不抢滚动,避免末条 AI 消息 WebView 重挂载回报高度把列表瞬跳到底。 */
internal fun shouldAutoScroll(atBottom: Boolean, userScrolling: Boolean): Boolean =
    atBottom && !userScrolling

/** Placeholder names (also the old "session #NNN") read as "新对话". */
internal fun displayTitle(title: String?): String =
    title?.takeUnless { com.example.personal_studio.domain.chat.ChatTitles.isPlaceholder(it) } ?: com.example.personal_studio.domain.chat.ChatTitles.NEW

/** "2.3 秒 · 约 410 tokens"; older rows without stats show nothing. */
internal fun formatDoneLine(message: ChatMessage): String? {
    val duration = message.generationMs ?: return null
    val tokens = message.tokenCount ?: return null
    val secs = duration / 1000.0
    val formatted = if (secs < 10) "%.1f".format(secs) else "%.0f".format(secs)
    return "$formatted 秒 · 约 $tokens tokens"
}
