package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val activeModel = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL

    // No inner Scaffold: MainScreen owns the backdrop (scanLines + vignette) and safe-area
    // insets for sub-routes. We render our own TerminalTopBar and a scrollable body.
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(
            route = "settings",
            subtitle = "# configure: api key · base url · model=$activeModel",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            }
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Section: API key ──────────────────────────────
            SectionHeader("## api key")

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append("# bearer token for any OpenAI-compatible endpoint.\n")
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
                            "paste api key (sk-...)",
                        color = FoamDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = {
                    Text(
                        text = "API_KEY",
                        color = Cyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::onSaveApiKey,
                    enabled = state.apiKeyDraft.isNotBlank(),
                    colors = terminalPrimaryButton(),
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

            // ── Section: API base URL ──────────────────────────────
            SectionHeader("## api base url")

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append("# point at any OpenAI-compatible server. Examples:\n")
                        append("#   https://api.openai.com/v1         (OpenAI)\n")
                        append("#   https://openrouter.ai/api/v1      (OpenRouter — default)\n")
                        append("#   http://<host>:11434/v1            (Ollama)\n")
                        append("#   http://<host>:1234/v1             (LM Studio)\n")
                        append("# /chat/completions is appended automatically.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )

            val activeBaseUrl = state.savedBaseUrl ?: BuildConfig.DEFAULT_API_BASE_URL
            KeyValueRow(
                key = "ACTIVE",
                value = activeBaseUrl + if (state.savedBaseUrl == null) "  (default)" else "",
                valueColor = if (state.savedBaseUrl == null) FoamMute else Foam,
            )

            OutlinedTextField(
                value = state.baseUrlDraft,
                onValueChange = vm::onBaseUrlDraftChanged,
                placeholder = {
                    Text(
                        text = "https://.../v1",
                        color = FoamDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = {
                    Text(
                        text = "API_BASE_URL",
                        color = Cyan,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::onSaveBaseUrl,
                    enabled = state.baseUrlDraft.isNotBlank(),
                    colors = terminalPrimaryButton(),
                ) {
                    Text("save", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = vm::onResetBaseUrl,
                    enabled = state.savedBaseUrl != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam),
                ) {
                    Text("reset to default", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── Section: Model ──────────────────────────────
            SectionHeader("## model")

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append("# model id understood by the active endpoint. Examples:\n")
                        append("#   google/gemini-2.0-flash-exp:free     (OpenRouter, free, multimodal)\n")
                        append("#   openai/gpt-4o-mini                    (OpenAI / OpenRouter, cheap multimodal)\n")
                        append("#   anthropic/claude-3.5-sonnet           (strong reasoning)\n")
                        append("#   llama3.1:8b                           (Ollama local)")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )

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
                colors = terminalFieldColors(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::onSaveModel,
                    enabled = state.modelDraft.isNotBlank(),
                    colors = terminalPrimaryButton(),
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
                text = "ping the active endpoint + model to verify key + network.",
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
                is TestConnectionState.Success -> StatusLine(ok = true, body = tc.replyPreview)
                is TestConnectionState.Failure -> StatusLine(ok = false, body = tc.message)
            }

            DashedDivider()

            // ── Section: timeline ──────────────────────────────
            SectionHeader("## timeline")

            NavigableRow(
                key = "SEMESTER",
                value = "学期起始日 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_SEMESTER) },
            )
            NavigableRow(
                key = "TIMETABLE",
                value = "13 节作息表 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_TIMETABLE) },
            )
            NavigableRow(
                key = "NOTIFICATIONS",
                value = "通知开关 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_NOTIF) },
            )
            NavigableRow(
                key = "COURSES",
                value = "课程列表 →",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.TIMELINE_COURSE_LIST) },
            )

            DashedDivider()

            // ── Section: future placeholders ──────────────────────────────
            SectionHeader("## coming later")

            KeyValueRow("THEME", "terminal · phosphor (locked)", FoamMute)
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
        Text("= ", style = MaterialTheme.typography.bodyMedium, color = FoamDim)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun NavigableRow(key: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
    ) {
        Text(key, style = MaterialTheme.typography.labelSmall, color = Phosphor,
            modifier = androidx.compose.ui.Modifier.width(140.dp))
        Text("= ", style = MaterialTheme.typography.bodyMedium, color = FoamDim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Foam)
    }
}

/** Same shape as [NavigableRow] but renders a small dim subtitle under the
 *  value — used for rows that need a short explanation (e.g. IMPORT). */
@Composable
private fun NavigableRowWithSubtitle(
    key: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
    ) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = Phosphor,
            modifier = androidx.compose.ui.Modifier.width(140.dp),
        )
        Text("= ", style = MaterialTheme.typography.bodyMedium, color = FoamDim)
        Column {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Foam)
            Text(
                "# $subtitle",
                style = MaterialTheme.typography.labelSmall,
                color = FoamDim,
            )
        }
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

@Composable
private fun terminalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Phosphor,
    unfocusedBorderColor = Rule,
    cursorColor = Phosphor,
)

@Composable
private fun terminalPrimaryButton() = ButtonDefaults.buttonColors(
    containerColor = Phosphor,
    contentColor = Void,
    disabledContainerColor = Rule,
    disabledContentColor = FoamDim,
)
