package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.feature.settings.vm.TestConnectionState
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Olive
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Void,
        topBar = {
            TerminalTopBar(
                route = "settings",
                trailing = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                }
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .scanLines()
                .vignette()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Section: API key ──────────────────────────────
            SectionHeader("## api key")

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append("# openrouter.ai gives you one key for many models.\n")
                        append("# empty means use the key bundled at build time.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = vm::onApiKeyDraftChanged,
                placeholder = {
                    Text(
                        text = if (state.savedApiKey != null)
                            "**** [set] — type to replace"
                        else
                            "paste openrouter api key (sk-or-v1-...)",
                        color = FoamDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = {
                    Text(
                        text = "OPENROUTER_API_KEY",
                        color = Cyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Phosphor,
                    unfocusedBorderColor = Rule,
                    cursorColor = Phosphor,
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::onSaveApiKey,
                    enabled = state.apiKeyDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Phosphor,
                        contentColor = Void,
                        disabledContainerColor = Rule,
                        disabledContentColor = FoamDim,
                    ),
                ) {
                    Text("save", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = vm::onClearApiKey,
                    enabled = state.savedApiKey != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam),
                ) {
                    Text("clear", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── Section: Model ──────────────────────────────
            SectionHeader("## model")

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append("# openrouter model id, e.g.\n")
                        append("#   google/gemini-2.0-flash-exp:free   (multimodal, free)\n")
                        append("#   anthropic/claude-3.5-sonnet         (strong reasoning)\n")
                        append("#   openai/gpt-4o-mini                   (cheap multimodal)")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )

            val activeModel = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL
            KeyValueRow(
                key = "ACTIVE",
                value = activeModel + if (state.savedModel == null) "  (default)" else "",
                valueColor = if (state.savedModel == null) FoamMute else Foam,
            )

            OutlinedTextField(
                value = state.modelDraft,
                onValueChange = vm::onModelDraftChanged,
                placeholder = {
                    Text(
                        text = "vendor/model-id",
                        color = FoamDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = {
                    Text(
                        text = "LLM_MODEL",
                        color = Cyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Phosphor,
                    unfocusedBorderColor = Rule,
                    cursorColor = Phosphor,
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::onSaveModel,
                    enabled = state.modelDraft.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Phosphor,
                        contentColor = Void,
                        disabledContainerColor = Rule,
                        disabledContentColor = FoamDim,
                    ),
                ) {
                    Text("save", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = vm::onResetModel,
                    enabled = state.savedModel != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam),
                ) {
                    Text("reset to default", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── Section: diagnostic ──────────────────────────────
            SectionHeader("## diagnostic")

            Text(
                text = "ping the active model to verify key + network.",
                style = MaterialTheme.typography.bodySmall,
                color = FoamDim,
            )

            Button(
                onClick = vm::onTestConnection,
                enabled = state.testConnection !is TestConnectionState.Running,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Void,
                ),
            ) {
                if (state.testConnection is TestConnectionState.Running) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                        color = Void,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("pinging…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("test llm ↵", style = MaterialTheme.typography.labelLarge)
                }
            }

            when (val tc = state.testConnection) {
                TestConnectionState.Idle, TestConnectionState.Running -> Unit
                is TestConnectionState.Success -> StatusLine(
                    ok = true,
                    body = tc.replyPreview,
                )
                is TestConnectionState.Failure -> StatusLine(
                    ok = false,
                    body = tc.message,
                )
            }

            DashedDivider()

            // ── Section: future placeholders ──────────────────────────────
            SectionHeader("## coming later")

            KeyValueRow("THEME", "terminal · phosphor (locked)", FoamMute)
            KeyValueRow("TIMETABLE", "13 periods · set in P4", FoamMute)
            KeyValueRow("NOTIFICATIONS", "wired in P4", FoamMute)
        }
    }
}

// ──────────────────────────────────────────────────────────
// Helpers

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Phosphor,
    )
}

@Composable
private fun DashedDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = Rule,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                )
            }
    )
}

@Composable
private fun KeyValueRow(key: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = Phosphor,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = "= ",
            style = MaterialTheme.typography.bodyMedium,
            color = FoamDim,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
        )
    }
}

@Composable
private fun StatusLine(ok: Boolean, body: String) {
    val tag = if (ok) "ok" else "err"
    val tagColor = if (ok) Olive else Carmine
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = tagColor)) { append("[$tag] ") }
            withStyle(SpanStyle(color = Foam)) { append(body) }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}
