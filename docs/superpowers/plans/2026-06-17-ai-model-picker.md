# AI 模型选择（基础选择器 + 高级门）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给普通用户内置默认模型选择器(端点/密钥隐藏内置),自定义 URL/API/Model 降级为高级设置并加警告门。

**Architecture:** 静态名单 `CuratedModels`(显示名↔代号,公开提交)+ 内置端点/密钥经 gitignore 的 `local.properties` 注入 `BuildConfig.DEFAULT_*`(真密钥不提交);新子页 `AiModelScreen`(11 单选 + 高级门)复用 `SettingsViewModel`;选模型=切代号+清自定义 url/key 回退内置;高级门=警告弹窗→现有 `LlmSettingsScreen`。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, JUnit4。

**约定：** 提交结尾 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。中文回复。`./gradlew` / 真机 `./gradlew :app:installDebug`。**安全:真密钥只写 gitignore 的 local.properties,任何提交/计划/源码零明文密钥。**

---

### Task 1: 模型名单 CuratedModels + 单测 + 内置端点/密钥(local.properties)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/core/llm/CuratedModels.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/llm/CuratedModelsTest.kt`
- Modify（**不提交**,gitignore）: `local.properties`

- [ ] **Step 1: 写失败测试**

创建 `CuratedModelsTest.kt`：

```kotlin
package com.example.personal_studio.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CuratedModelsTest {

    @Test fun `has 11 curated models`() {
        assertEquals(11, CuratedModels.ALL.size)
    }

    @Test fun `displayFor maps known code to display name`() {
        assertEquals("Gemini 3.5 Flash", CuratedModels.displayFor("gemini-3.5-flash"))
        assertEquals("豆包", CuratedModels.displayFor("doubao-seed-2-0-lite-260428"))
    }

    @Test fun `displayFor returns null for unknown or null code`() {
        assertNull(CuratedModels.displayFor("some-custom-model"))
        assertNull(CuratedModels.displayFor(null))
    }
}
```

- [ ] **Step 2: 运行确认失败** — Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.core.llm.CuratedModelsTest"` Expected: FAIL（未定义）。

- [ ] **Step 3: 写名单**

创建 `CuratedModels.kt`：

```kotlin
package com.example.personal_studio.core.llm

/** 一个内置可选模型:展示名 + 调用 API 时的实际模型代号。 */
data class CuratedModel(val display: String, val code: String)

/** 内置精选模型名单(共用 App 内置端点+密钥;来源见仓库外 AI-models 文件)。 */
object CuratedModels {
    val ALL: List<CuratedModel> = listOf(
        CuratedModel("Claude Opus 4.8", "claude-opus-4-8"),
        CuratedModel("Claude Sonnet 4.6", "claude-sonnet-4-6"),
        CuratedModel("GPT 5.5", "gpt-5.5"),
        CuratedModel("Gemini 3.5 Flash", "gemini-3.5-flash"),
        CuratedModel("Gemini 3.1 Pro", "gemini-3.1-pro-preview"),
        CuratedModel("Deepseek V4 Flash", "deepseek-v4-flash"),
        CuratedModel("Deepseek V4 Pro", "deepseek-v4-pro"),
        CuratedModel("GLM 5.2", "glm-5.2"),
        CuratedModel("Kimi 2.6", "kimi-2.6"),
        CuratedModel("Minimax M3", "MiniMax-M3"),
        CuratedModel("豆包", "doubao-seed-2-0-lite-260428"),
    )

    /** 代号→展示名;非名单内(自定义)或 null 返回 null。 */
    fun displayFor(code: String?): String? = ALL.firstOrNull { it.code == code }?.display
}
```

- [ ] **Step 4: 运行确认通过** — Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.core.llm.CuratedModelsTest"` Expected: PASS（3 个）。

- [ ] **Step 5: 写内置端点/密钥进 local.properties（不提交）**

读 `AI-models` 文件取「接口地址」(去掉尾部 `/chat/completions` 作 base url) 与 key。读现有 `local.properties`(保留 `sdk.dir` 等),设/更新三键(无则加、有则替换),**真值不写进本计划/任何提交文件**：
```
API_BASE_URL=<AI-models 接口地址去 /chat/completions>
API_KEY=<AI-models 的 sk-… key>
DEFAULT_LLM_MODEL=gemini-3.5-flash
```
（`local.properties` 已在 `.gitignore`,不会提交。）

- [ ] **Step 6: 提交（只提交 CuratedModels + 测试;local.properties 不提交）**

```bash
git add app/src/main/java/com/example/personal_studio/core/llm/CuratedModels.kt \
        app/src/test/java/com/example/personal_studio/core/llm/CuratedModelsTest.kt
git commit -m "feat(llm): 内置精选模型名单 CuratedModels(11 条,显示名↔代号)+ 单测

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: SettingsViewModel.selectCuratedModel

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt`

- [ ] **Step 1: 加 selectCuratedModel**

在 `SettingsViewModel.kt` 的 `onResetModel()` 之后加：

```kotlin
    /** 选内置精选模型:切代号 + 清掉任何自定义 url/key → 回退到内置端点+密钥(BuildConfig 默认)。 */
    fun selectCuratedModel(code: String) {
        viewModelScope.launch {
            prefs.setModelName(code)
            prefs.setApiBaseUrl(null)
            prefs.setApiKey(null)
        }
    }
```

- [ ] **Step 2: 编译验证** — Run: `./gradlew :app:compileDebugKotlin` Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt
git commit -m "feat(settings): SettingsViewModel.selectCuratedModel(切名单模型+清自定义回退内置)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: AiModelScreen + 路由 + AppNavHost + 主设置 AI 行改向

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/settings/ui/AiModelScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`（+SETTINGS_AI_MODEL）
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`（+composable）
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`（AI 行改向）

- [ ] **Step 1: NavRoutes 加 SETTINGS_AI_MODEL**

在 `NavRoutes.kt` 的 `const val SETTINGS_LLM = "settings/llm"` 行后加：
```kotlin
    const val SETTINGS_AI_MODEL = "settings/ai-model"
```

- [ ] **Step 2: 写 AiModelScreen**

创建 `AiModelScreen.kt`：

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.core.llm.CuratedModel
import com.example.personal_studio.core.llm.CuratedModels
import com.example.personal_studio.feature.settings.vm.SettingsViewModel
import com.example.personal_studio.ui.components.TerminalConfirmDialog
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun AiModelScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = state.savedModel ?: BuildConfig.DEFAULT_LLM_MODEL
    var showWarning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TerminalTopBar(route = "settings/ai-model", subtitle = "# AI 模型", trailing = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back") }
        })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (CuratedModels.displayFor(active) == null) {
                Text("当前：$active（自定义）", color = Amber, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "选择一个内置模型（端点与密钥已内置）：",
                color = FoamDim, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            CuratedModels.ALL.forEach { m ->
                ModelRow(m, selected = m.code == active) { vm.selectCuratedModel(m.code) }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "⚙ AI 模型高级设置（自定义接口 / 密钥 / 模型）",
                color = FoamMute, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable { showWarning = true }.padding(vertical = 8.dp),
            )
        }
    }

    if (showWarning) {
        TerminalConfirmDialog(
            title = "高级设置",
            message = "进行此操作之前，请确保你已经对通过 API 调用大模型的流程十分熟悉。",
            confirmLabel = "我已了解,继续",
            onConfirm = { showWarning = false; onOpenAdvanced() },
            onDismiss = { showWarning = false },
        )
    }
}

@Composable
private fun ModelRow(m: CuratedModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selected) "● " else "○ ",
            color = if (selected) Phosphor else FoamMute,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            m.display,
            color = if (selected) Foam else FoamMute,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **Step 3: AppNavHost 加 SETTINGS_AI_MODEL composable**

在 `AppNavHost.kt` 的 `composable(NavRoutes.SETTINGS_LLM) { ... }` 块之前(或之后)加：
```kotlin
        composable(NavRoutes.SETTINGS_AI_MODEL) {
            com.example.personal_studio.feature.settings.ui.AiModelScreen(
                onBack = { navController.popBackStack() },
                onOpenAdvanced = { navController.navigate(NavRoutes.SETTINGS_LLM) },
            )
        }
```

- [ ] **Step 4: 主设置 AI 行改向**

在 `SettingsScreen.kt` 把：
```kotlin
            GroupHeader("AI")
            NavRow("AI 模型设置", hint = "密钥 · 接口 · 模型 · 测试") { onNavigate(NavRoutes.SETTINGS_LLM) }
```
改为：
```kotlin
            GroupHeader("AI")
            NavRow("AI 模型", hint = "切换内置模型 · 高级自定义") { onNavigate(NavRoutes.SETTINGS_AI_MODEL) }
```

- [ ] **Step 5: 编译验证** — Run: `./gradlew :app:compileDebugKotlin` Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 全量单测回归** — Run: `./gradlew :app:testDebugUnitTest` Expected: BUILD SUCCESSFUL（含 `CuratedModelsTest`）。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/ui/AiModelScreen.kt \
        app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt \
        app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt \
        app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt
git commit -m "feat(settings): AI 模型选择子页(11 内置模型单选 + 高级设置警告门),主设置 AI 行改向

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 完成后

全部 Task 完成且 `./gradlew :app:testDebugUnitTest` 全绿后,用 **superpowers:finishing-a-development-branch** 收尾(真机 DoD → 合并 main → 推 GitHub → 更新记忆)。**收尾前再次确认 `git status`/`git log` 无 `local.properties`、无 `AI-models`、无明文 key。**

**真机 DoD：** 设置 → AI 模型:① 11 模型单选,默认 ● Gemini 3.5 Flash;点别的即切换;② chat/成绩分析等用新模型且能连通(走内置端点);③ 点「AI 模型高级设置」→ 弹警告(那句话)→ 确认进自定义页/取消留下;④ 高级页改过 model 后回选择器顶部显「当前：⟨…⟩（自定义）」,再选名单模型切回内置。

## Self-Review 记录

- **Spec 覆盖**：① 名单+内置端点密钥→Task 1;② 选择器子页→Task 3;③ selectCuratedModel→Task 2;④ 警告门→Task 3;⑤ 主设置改向→Task 3 Step4;⑥ 路由→Task 3 Step1/3。全覆盖。
- **占位符**：无业务占位;local.properties 真值刻意用占位(密钥不入提交),执行时从 AI-models 写入。
- **类型一致**：`CuratedModels.ALL/displayFor/CuratedModel(display,code)` Task 1 定义、Task 3 用一致;`selectCuratedModel(code)` Task 2 定义、Task 3 调用一致;`AiModelScreen(onBack,onOpenAdvanced)` Task 3 定义、AppNavHost 调一致;`NavRoutes.SETTINGS_AI_MODEL` Task 3 Step1 定义、Step3/4 引用一致;`BuildConfig.DEFAULT_LLM_MODEL` 既有。
- **安全核**：提交文件(CuratedModels/AiModelScreen/spec/plan)均无 key/端点真值;真值只在 gitignore 的 local.properties。
