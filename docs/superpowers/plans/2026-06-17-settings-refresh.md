# 设置页重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 设置页重构为干净分组导航清单,整块 LLM 配置(密钥/接口/模型/测试)挪进新子页「AI 模型设置」,并汉化/裁示例墙/删死占位/加关于版本。

**Architecture:** 新建 `LlmSettingsScreen` 承接现 `SettingsScreen` 的 LLM 配置(复用 `SettingsViewModel`,迁入助手件,裁示例墙+汉化);`SettingsScreen` 重写为三组导航清单(教务/通用 + AI + 关于);加 `SETTINGS_LLM` 路由 + AppNavHost composable。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Navigation Compose。

**约定：** 提交结尾 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。中文回复。`./gradlew` / 真机 `./gradlew :app:installDebug`。纯 UI 重排,不强加单测。

---

### Task 1: 新子页 LlmSettingsScreen + 路由 + 接线

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/settings/ui/LlmSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`（+SETTINGS_LLM）
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`（+composable）

- [ ] **Step 1: NavRoutes 加 SETTINGS_LLM**

在 `NavRoutes.kt` 的 `SETTINGS_NOTIF` 行后加：
```kotlin
    const val SETTINGS_LLM = "settings/llm"
```
（紧邻其它 `SETTINGS_*` 子路由,如 `const val SETTINGS_NOTIF = "settings/notif"` 之后。）

- [ ] **Step 2: 写 LlmSettingsScreen**

创建 `LlmSettingsScreen.kt`（搬现 `SettingsScreen` 的 LLM 配置 + 自带顶栏/返回 + 裁示例墙 + 汉化 + 迁入助手件）：

```kotlin
package com.example.personal_studio.feature.settings.ui

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
fun LlmSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val activeModel = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(
            route = "settings/llm",
            subtitle = "# AI 模型",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            },
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
            // ── 密钥 ──
            SectionHeader("## 密钥")
            Text(
                "# OpenAI 兼容端点密钥;留空用内置默认。",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = vm::onApiKeyDraftChanged,
                placeholder = {
                    Text(
                        if (state.savedApiKey != null) "**** [已设置] — 输入以替换" else "粘贴 API 密钥 (sk-...)",
                        color = FoamDim, style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = { Text("API_KEY", color = Cyan, style = MaterialTheme.typography.labelSmall) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveApiKey, enabled = state.apiKeyDraft.isNotBlank(), colors = terminalPrimaryButton()) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = vm::onClearApiKey, enabled = state.savedApiKey != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam)) {
                    Text("清除", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── 接口地址 ──
            SectionHeader("## 接口地址")
            Text(
                "# OpenAI 兼容地址,自动补 /chat/completions。例: openrouter.ai/api/v1",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            val activeBaseUrl = state.savedBaseUrl ?: BuildConfig.DEFAULT_API_BASE_URL
            KeyValueRow(
                key = "当前",
                value = activeBaseUrl + if (state.savedBaseUrl == null) "  (默认)" else "",
                valueColor = if (state.savedBaseUrl == null) FoamMute else Foam,
            )
            OutlinedTextField(
                value = state.baseUrlDraft,
                onValueChange = vm::onBaseUrlDraftChanged,
                placeholder = { Text("https://.../v1", color = FoamDim, style = MaterialTheme.typography.bodyMedium) },
                label = { Text("API_BASE_URL", color = Cyan, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveBaseUrl, enabled = state.baseUrlDraft.isNotBlank(), colors = terminalPrimaryButton()) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = vm::onResetBaseUrl, enabled = state.savedBaseUrl != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam)) {
                    Text("恢复默认", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── 模型 ──
            SectionHeader("## 模型")
            Text(
                "# 端点支持的模型 id。例: google/gemini-2.0-flash-exp:free",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            KeyValueRow(
                key = "当前",
                value = activeModel + if (state.savedModel == null) "  (默认)" else "",
                valueColor = if (state.savedModel == null) FoamMute else Foam,
            )
            OutlinedTextField(
                value = state.modelDraft,
                onValueChange = vm::onModelDraftChanged,
                placeholder = { Text("vendor/model-id", color = FoamDim, style = MaterialTheme.typography.bodyMedium) },
                label = { Text("LLM_MODEL", color = Cyan, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                colors = terminalFieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onSaveModel, enabled = state.modelDraft.isNotBlank(), colors = terminalPrimaryButton()) {
                    Text("保存", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = vm::onResetModel, enabled = state.savedModel != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Foam)) {
                    Text("恢复默认", style = MaterialTheme.typography.labelLarge)
                }
            }

            DashedDivider()

            // ── 连接测试 ──
            SectionHeader("## 连接测试")
            Text(
                "向当前端点 + 模型发一次请求,验证密钥与网络。",
                style = MaterialTheme.typography.bodySmall, color = FoamDim,
            )
            Button(
                onClick = vm::onTestConnection,
                enabled = state.testConnection !is TestConnectionState.Running,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Void),
            ) {
                if (state.testConnection is TestConnectionState.Running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp), color = Void)
                    Spacer(Modifier.size(8.dp))
                    Text("连接中…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("测试连接 ↵", style = MaterialTheme.typography.labelLarge)
                }
            }
            when (val tc = state.testConnection) {
                TestConnectionState.Idle, TestConnectionState.Running -> Unit
                is TestConnectionState.Success -> StatusLine(ok = true, body = tc.replyPreview)
                is TestConnectionState.Failure -> StatusLine(ok = false, body = tc.message)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = Phosphor)
}

@Composable
private fun DashedDivider() {
    Spacer(
        Modifier.fillMaxWidth().height(1.dp).drawBehind {
            drawLine(
                color = Rule, start = Offset(0f, 0f), end = Offset(size.width, 0f),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
    )
}

@Composable
private fun KeyValueRow(key: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.labelSmall, color = Phosphor, modifier = Modifier.width(140.dp))
        Text("= ", style = MaterialTheme.typography.bodyMedium, color = FoamDim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun StatusLine(ok: Boolean, body: String) {
    val tag = if (ok) "成功" else "失败"
    val tagColor = if (ok) Olive else Carmine
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = tagColor)) { append("[$tag] ") }
            withStyle(SpanStyle(color = Foam)) { append(body) }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun terminalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Phosphor, unfocusedBorderColor = Rule, cursorColor = Phosphor,
)

@Composable
private fun terminalPrimaryButton() = ButtonDefaults.buttonColors(
    containerColor = Phosphor, contentColor = Void, disabledContainerColor = Rule, disabledContentColor = FoamDim,
)
```

- [ ] **Step 3: AppNavHost 加 SETTINGS_LLM composable**

在 `AppNavHost.kt` 的 `composable(NavRoutes.SETTINGS) { ... }` 块之后加：
```kotlin
        composable(NavRoutes.SETTINGS_LLM) {
            com.example.personal_studio.feature.settings.ui.LlmSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
```

- [ ] **Step 4: 编译验证** — Run: `./gradlew :app:compileDebugKotlin` Expected: BUILD SUCCESSFUL（此时 SettingsScreen 仍含旧 LLM 体,与 LlmSettingsScreen 各有同名 private 助手件,文件作用域不冲突）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/ui/LlmSettingsScreen.kt \
        app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt \
        app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "feat(settings): 新子页 LlmSettingsScreen(承接 LLM 配置,裁示例墙+汉化)+ 路由接线

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: 重写 SettingsScreen 为分组导航清单

**Files:**
- Modify（整文件替换）: `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`

- [ ] **Step 1: 整体替换 SettingsScreen.kt**

```kotlin
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.navigation.NavRoutes
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(
            route = "settings",
            subtitle = "# 偏好设置",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GroupHeader("教务 / 通用")
            NavRow("学期起始日") { onNavigate(NavRoutes.SETTINGS_SEMESTER) }
            NavRow("作息表(13 节)") { onNavigate(NavRoutes.SETTINGS_TIMETABLE) }
            NavRow("通知与后台提醒") { onNavigate(NavRoutes.SETTINGS_NOTIF) }
            NavRow("课程列表") { onNavigate(NavRoutes.TIMELINE_COURSE_LIST) }

            Spacer(Modifier.height(14.dp))
            GroupHeader("AI")
            NavRow("AI 模型设置", hint = "密钥 · 接口 · 模型 · 测试") { onNavigate(NavRoutes.SETTINGS_LLM) }

            Spacer(Modifier.height(14.dp))
            GroupHeader("关于")
            Text(
                "版本 ${BuildConfig.VERSION_NAME} · 终端风主题",
                color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = FoamDim)) { append("── ") }
            withStyle(SpanStyle(color = Phosphor)) { append(title) }
            withStyle(SpanStyle(color = FoamDim)) { append(" ──") }
        },
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun NavRow(label: String, hint: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("▸ $label", color = Foam, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("→", color = FoamMute, style = MaterialTheme.typography.bodyMedium)
        }
        if (hint != null) {
            Text(hint, color = FoamDim, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 14.dp, top = 1.dp))
        }
    }
}
```

- [ ] **Step 2: 编译验证** — Run: `./gradlew :app:compileDebugKotlin` Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 全量单测回归** — Run: `./gradlew :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt
git commit -m "feat(settings): 主设置页重写为分组导航清单(教务/通用 + AI + 关于),去 LLM 配置/英文/死占位

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完成后

全部 Task 完成且 `./gradlew :app:testDebugUnitTest` 全绿后,用 **superpowers:finishing-a-development-branch** 收尾(真机 DoD → 合并 main → 推 GitHub → 更新记忆)。

**真机 DoD：** 任一 tab 齿轮进设置:① 主页三组导航清单(教务/通用 4 行 / AI 1 行 / 关于版本),清爽、无英文注释墙、无 `coming later`;② 4 个教务行各点进对应页;③ `AI 模型设置` 进子页,可改密钥/接口/模型(保存/清除/恢复默认)+ 测试连接(成功/失败显中文),返回正常;④ 关于显 `版本 0.1.0-p0 · 终端风主题`。

## Self-Review 记录

- **Spec 覆盖**：① 主页分组导航→Task 2;② LLM 子页搬入+瘦身+汉化→Task 1;③ 路由接线→Task 1 Step1/3;关于版本→Task 2。全覆盖。
- **占位符**：无;每步含完整代码 + 命令。
- **类型一致**：`LlmSettingsScreen(onBack, vm)` 在 Task 1 定义、AppNavHost 调用一致;`SettingsScreen(onBack, onNavigate)`(去 vm 参数)与 AppNavHost 既有调用(只传 onBack/onNavigate)兼容;`NavRoutes.SETTINGS_LLM` 在 Task 1 定义、Task 2 引用一致;复用的 `SettingsViewModel` 字段/动作(apiKeyDraft/savedApiKey/onSaveApiKey/onClearApiKey/baseUrlDraft/savedBaseUrl/onSaveBaseUrl/onResetBaseUrl/modelDraft/savedModel/onSaveModel/onResetModel/testConnection/onTestConnection/TestConnectionState)均沿用现 SettingsScreen 用法,未改。
