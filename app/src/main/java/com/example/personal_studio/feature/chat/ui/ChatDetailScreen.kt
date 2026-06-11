package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.example.personal_studio.feature.scanner.chat.QuickCaptureForChatScreen
import com.example.personal_studio.feature.scanner.library.ScanLibraryPickerScreen
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
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
import com.example.personal_studio.ui.components.AiFrame
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.ChatImageThumbnail
import com.example.personal_studio.ui.components.ChatImageThumbnailSmall
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TypewriterText
import com.example.personal_studio.ui.components.UserPromptLine
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.io.File

@Composable
fun ChatDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onNavigateToKbEntry: (Long) -> Unit,
) {
    val vm: ChatDetailViewModel = hiltViewModel(
        creationCallback = { factory: ChatDetailViewModel.Factory -> factory.create(sessionId) }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val saveVm: SaveToKnowledgeViewModel = hiltViewModel()
    val saveState by saveVm.uiState.collectAsStateWithLifecycle()
    // Holds the source the modal is currently working with so retry from the
    // Error state knows what to re-run. Invariant: when retry is reachable
    // (state == Error), activeSourceState matches the source that errored, because
    // Error is only entered from Loading/Saving and both flip activeSourceState
    // first. If a future change makes Error reachable from elsewhere, fold the
    // source into Error itself instead of relying on this side-channel.
    val activeSourceState = remember { mutableStateOf<KbDraftSource?>(null) }
    val listState = rememberLazyListState()

    // Attachment sheet + crop overlay local UI state
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var pickedForCrop by remember { mutableStateOf<String?>(null) }
    // Full-screen takeover states for scanner integration.
    var showQuickCapture by remember { mutableStateOf(false) }
    var showScansPicker by remember { mutableStateOf(false) }

    // Per-message rendered height cache. MathMarkdownView is a WebView that reports
    // its real height async (after KaTeX + font load + ResizeObserver). When an AI
    // message scrolls out of view LazyColumn disposes its composition; re-entering
    // would otherwise start back at a 60dp placeholder and visibly pop to full size,
    // causing the neighboring rows to shift — this is the "scroll stall" symptom.
    // Caching at the screen level means a row re-entering the viewport immediately
    // lays out at its last-known size and the WebView's async report just confirms it.
    val aiMessageHeights = remember { mutableStateMapOf<Long, Int>() }

    // "Auto-follow" = user is already pinned to the latest turn. We only hijack
    // scroll when this is true, so reading older messages (user drags down to
    // look back) doesn't get yanked back to the bottom every time a streaming
    // token arrives. Checked via the tail item being in the visible range —
    // layoutInfo is pre-mutation when a new message appends, which conveniently
    // captures the at-bottom state from *before* the new item was added.
    val autoFollow by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf true
            lastVisible >= total - 1
        }
    }

    // Watch the last persisted message's cached WebView height. When a stream ends,
    // the streaming item (TypewriterText at natural text height) is swapped for a
    // MathMarkdownView placeholder at 60dp, we scroll to the end of that placeholder,
    // and THEN the WebView asynchronously reports its real height and the item grows.
    // LazyColumn anchors the first visible item, so that growth pushes the tail below
    // the viewport — which reads as the viewport "snapping back to the top of the
    // message". Including this height in the LaunchedEffect's keys re-fires the
    // auto-scroll once the real height is known, pinning the tail against the bottom.
    val lastMessageId = state.messages.lastOrNull()?.id
    val lastMessageHeight = lastMessageId?.let { aiMessageHeights[it] }

    LaunchedEffect(state.messages.size, state.streamingText?.length, lastMessageHeight) {
        if (!autoFollow) return@LaunchedEffect
        val lastIndex = state.messages.size + (if (state.streamingText != null) 1 else 0)
        if (lastIndex > 0) {
            // Non-animated scroll: the streaming path re-launches this effect on
            // every token (~40/sec). Using animateScrollToItem here cancels the
            // in-flight animation every tick and chases the ever-moving scroll-max,
            // which reads as visual shake. Instant scroll has no curve to cancel
            // and — since deltas per tick are tiny — still looks smooth to the eye.
            // scrollOffset = Int.MAX_VALUE lands at the item's natural end (Compose
            // clamps to scroll-max), so the tail of the message stays against the
            // viewport bottom instead of the head being yanked to the viewport top.
            listState.scrollToItem(lastIndex - 1, Int.MAX_VALUE)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TerminalTopBar(
            route = "chat",
            subtitle = buildSubtitle(state.session?.title, state.activeModel),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "[+ 归档会话]",
                        color = Phosphor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clickable {
                                val src = KbDraftSource.FromChatSession(sessionId = sessionId)
                                activeSourceState.value = src
                                saveVm.startDraft(src)
                            }
                            .padding(8.dp),
                    )
                    Text(
                        "←",
                        color = FoamMute,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickableNoRipple(onBack).padding(8.dp),
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            state = listState,
            verticalArrangement = Arrangement.Top,
        ) {
            items(state.messages, key = { it.id }) { m ->
                when (m.role) {
                    MessageRole.USER -> {
                        val imagePath = m.attachedImagePath
                        UserPromptLine(
                            text = m.contentMarkdown,
                            imageThumb = if (imagePath != null) {
                                { ChatImageThumbnail(path = imagePath) }
                            } else null,
                        )
                    }
                    MessageRole.AI -> {
                        // Header pins the model that produced THIS reply — not the
                        // currently-active one — so switching models later doesn't
                        // retroactively relabel historical turns. Legacy rows without
                        // a persisted model id fall back to the active model.
                        AiFrame(
                            header = m.modelUsed ?: state.activeModel,
                            footer = formatDoneLine(m),
                        ) {
                            MathMarkdownView(
                                markdown = m.contentMarkdown,
                                initialHeightDp = aiMessageHeights[m.id] ?: 60,
                                onHeightChanged = { h -> aiMessageHeights[m.id] = h },
                            )
                        }
                        // [+ archive] row — only on completed AI messages (not the
                        // streaming placeholder, which has no real id yet).
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "[+ 归档]",
                                color = Phosphor,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .clickable {
                                        val src = KbDraftSource.FromChatMessage(
                                            sessionId = sessionId,
                                            aiMessageId = m.id,
                                        )
                                        activeSourceState.value = src
                                        saveVm.startDraft(src)
                                    }
                                    .padding(8.dp),
                            )
                        }
                    }
                    MessageRole.SYSTEM -> Text(
                        m.contentMarkdown,
                        style = MaterialTheme.typography.bodySmall,
                        color = FoamDim,
                    )
                }
            }
            if (state.streamingText != null) {
                item(key = "__streaming__") {
                    AiFrame(header = state.activeModel) {
                        TypewriterText(
                            text = state.streamingText ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                        )
                    }
                }
            }
        }

        // Error banner
        if (state.errorBanner != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Carmine)) { append("[错误] ") }
                        withStyle(SpanStyle(color = Foam)) { append(state.errorBanner ?: "") }
                        append("  ")
                        withStyle(SpanStyle(color = Cyan)) { append("[关闭]") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickableNoRipple { vm.onDismissError() }
                )
            }
        }

        // Attached image preview row
        if (state.attachedImagePath != null) {
            val attachedPath = state.attachedImagePath!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatImageThumbnailSmall(path = attachedPath)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "[图片] ${File(attachedPath).name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cyan,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "[x]",
                    style = MaterialTheme.typography.bodySmall,
                    color = Carmine,
                    modifier = Modifier.clickableNoRipple { vm.onAttachImage(null) }
                )
            }
        }

        // Phosphor dashed rule above the input — matching the top bar's divider style.
        DashedPhosphorRule()

        // Input line. MIUI 等在 edge-to-edge 下仍会因 IME 自动缩窗(已把内容抬到键盘上方),
        // 此处**不再叠加 imePadding**——叠了会翻倍,把输入框顶到屏幕中部。只用 navigationBarsPadding
        // 兜键盘隐藏时的手势条,外加固定底部留白,避免缩窗后输入框被键盘顶边压住。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "> ",
                style = MaterialTheme.typography.bodyMedium,
                color = Phosphor,
            )
            BasicTextField(
                value = state.input,
                onValueChange = vm::onInputChanged,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                // 回车插入换行(支持多行);发送只走右侧「↵ 发送」。
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 5,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    // Terminal-style placeholder: rendered underneath the inner text
                    // field so it disappears as soon as the user types.
                    if (state.input.isEmpty()) {
                        Text(
                            text = "在这里输入…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoamDim,
                        )
                    }
                    innerTextField()
                },
            )
            if (state.input.isBlank()) {
                BlinkingCursor()
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "↵ 发送",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
                modifier = Modifier.clickableNoRipple { vm.onSend() }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "[+]",
                color = Cyan,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickableNoRipple { showAttachmentSheet = true },
            )
        }

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

    // Attachment sheet
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

    // Scanner takeovers. Both funnel picked paths through the same
    // pickedForCrop state, so the ImageCropOverlay path is identical to
    // the gallery case.
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
    // Crop overlay
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

private fun buildSubtitle(title: String?, model: String): String {
    val sessionLabel = title?.takeIf { it.isNotBlank() } ?: "(未命名)"
    // Two comment-style lines — keeps the session title readable when long and
    // gives the active model its own row so it doesn't wrap awkwardly.
    return "# 会话: $sessionLabel\n# 模型: $model"
}

/**
 * Footer for a persisted AI message. Falls back to `── done ──` when duration or token
 * count is missing (older DB rows); otherwise renders stats persisted by SendMessageUseCase.
 */
private fun formatDoneLine(message: ChatMessage): String {
    val duration = message.generationMs
    val tokens = message.tokenCount
    if (duration == null || tokens == null) return "── 完成 ──"
    val secs = duration / 1000.0
    val formatted = if (secs < 10) "%.1fs".format(secs) else "%.0fs".format(secs)
    return "── 完成 · $formatted · $tokens tokens ──"
}

@Composable
private fun DashedPhosphorRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 20.dp)
            .drawBehind {
                drawLine(
                    color = Phosphor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                )
            }
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    ))
