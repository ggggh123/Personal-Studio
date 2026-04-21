package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.MessageRole
import com.example.personal_studio.feature.chat.vm.ChatDetailViewModel
import com.example.personal_studio.ui.components.AiFrame
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.components.TypewriterText
import com.example.personal_studio.ui.components.UserPromptLine
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

@Composable
fun ChatDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
) {
    val vm: ChatDetailViewModel = hiltViewModel(
        creationCallback = { factory: ChatDetailViewModel.Factory -> factory.create(sessionId) }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages arrive or streaming text grows
    LaunchedEffect(state.messages.size, state.streamingText?.length) {
        val lastIndex = state.messages.size + (if (state.streamingText != null) 1 else 0)
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }

    Scaffold(
        containerColor = Void,
        topBar = {
            TerminalTopBar(
                route = state.session?.title ?: "chat",
                trailing = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .scanLines()
                .vignette(),
        ) {
            // Transcript
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
                        MessageRole.USER -> UserPromptLine(text = m.contentMarkdown)
                        MessageRole.AI -> AiFrame(footer = "── done ──") {
                            MathMarkdownView(markdown = m.contentMarkdown)
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
                        AiFrame {
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
                            withStyle(SpanStyle(color = Carmine)) { append("[err] ") }
                            withStyle(SpanStyle(color = Foam)) { append(state.errorBanner ?: "") }
                            append("  ")
                            withStyle(SpanStyle(color = Cyan)) { append("[dismiss]") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickableNoRipple { vm.onDismissError() }
                    )
                }
            }

            // Input line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = Rule,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1f,
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.onSend() }),
                    modifier = Modifier.weight(1f),
                )
                if (state.input.isBlank()) {
                    BlinkingCursor()
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "↵ send",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                    modifier = Modifier.clickableNoRipple { vm.onSend() }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { /* Phase F wires attachment sheet */ }) {
                    Icon(Icons.Filled.Add, contentDescription = "attach", tint = Cyan)
                }
            }
        }
    }
}

// avoid Material3 ripple on text buttons
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    ))
