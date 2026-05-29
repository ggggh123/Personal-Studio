# 统一登录界面 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把零散的"各功能现场输入学号密码"统一成一个门面级登录界面;首屏引导登录(可跳过),教务功能未登录时拦截跳登录、登录成功回到目标。

**Architecture:** 复用现有单一凭据 store(`ImportCredentialPrefs`,EncryptedSharedPreferences)作为"已登录"唯一真相(`observeAll().value != null`);新增 `LoginPrefs`(DataStore `hasSeenLogin`)管首屏门;`ValidateCredentialsUseCase` 立即验证(open→`SsoLoginUseCase`→map→close);`BitLoginScreen`/`BitLoginViewModel` 统一登录入口(门面视觉复用 `TerminalBackdrop`/VT323/`AiFrame`/`BlinkingCursor`)。登录是一等导航路由 `login?next={next}`,各教务入口未登录时跳它。成绩/导入的内联登录表单退休,改为读存好的凭据直接同步/导入。

**Tech Stack:** Kotlin、Hilt、DataStore Preferences、EncryptedSharedPreferences、Jetpack Compose + Navigation、mockk + runTest + Turbine。

**前置:** 分支 `feature/p8-unified-login`(从 main 切出)。

**实现注记(读现状得出,务必遵守):**
- `SyncGradesUseCase.sync(req)` / `ImportCoursesUseCase.import(req)` **各自 open/sso/close 自带登录**,无法消费外部会话 —— "已登录后同步" = 读存好的 creds 喂进 request,而非复用 session。
- 导入向导的 `ImportScreen.TermPicker` 是**未接线死代码**(无处填 `terms`/`currentTerm`),真实流程 Credentials→Progress→Preview。本计划**不启用 TermPicker**,已登录时直接用存的 creds 跑当前学期导入。
- 不新建 `AuthStatus` 类;各 VM 直接注入 `ImportCredentialPrefs`,`observeAll().map { it != null }` 得登录态。
- `ImportCredentialPrefs.save/clear` 同步;`*SyncPrefs.setEnabled` 是 `suspend`;`*PollScheduler.cancel()` 同步。
- mockk 对 `SsoLoginUseCase.invoke`(suspend operator)/其它非 operator `invoke` 打桩时用**显式 receiver**(`coEvery { sso.invoke(...) }`)避免类型推断歧义。

---

## 文件结构

```
# 新增
domain/bitimport/model/LoginModels.kt              LoginRequest / LoginOutcome
domain/bitimport/ValidateCredentialsUseCase.kt     立即验证
data/local/datastore/LoginPrefs.kt                 hasSeenLogin(DataStore)
feature/auth/BitLoginViewModel.kt                  登录态/输入/验证编排 + 事件
feature/auth/ui/BitLoginScreen.kt                  门面登录屏
feature/auth/RootViewModel.kt                      冷启动读 hasSeenLogin → 起始目的地
ui/components/TerminalSplash.kt                    冷启动终端 splash(门面)

# 改
ui/navigation/NavRoutes.kt                         LOGIN 路由 + login(next)
ui/AppNavHost.kt                                   注册 login;startDestination 参数化;grades onNeedLogin;删 GRADES_SYNC
ui/MainScreen.kt                                   RootViewModel 决定起始;splash
feature/bitgrades/GradesViewModel.kt               注入 sync+creds;loggedIn;onSyncNow + 进度
feature/bitgrades/ui/GradesScreen.kt               onNeedLogin 守卫 + 内联进度
feature/bitimport/ImportViewModel.kt               startWithSavedCreds(已登录自动导入)
feature/bitimport/ImportNavGraph.kt                ImportEntryRoute 守卫 + 自动导入
feature/bitddl/AssignmentsViewModel.kt             onRefresh 未登录发导航事件
feature/bitddl/ui/AssignmentsScreen.kt             收事件跳 login
feature/settings/vm/SettingsViewModel.kt           注入 5 依赖;loggedInUsername;onLogout
feature/settings/ui/SettingsScreen.kt              「BIT 账号」行

# 退休(删除)
feature/bitgrades/GradesSyncViewModel.kt           删
feature/bitgrades/ui/GradesSyncScreen.kt           删
feature/bitimport/ui/ImportCredentialsScreen.kt    删(凭据步退休)
NavRoutes.GRADES_SYNC                              删常量 + AppNavHost composable
```

---

## Phase A · 数据 / 用例

### Task 1: `LoginModels` + `ValidateCredentialsUseCase`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/bitimport/model/LoginModels.kt`
- Create: `app/src/main/java/com/example/personal_studio/domain/bitimport/ValidateCredentialsUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitimport/ValidateCredentialsUseCaseTest.kt`

- [ ] **Step 1: 写模型**

```kotlin
// LoginModels.kt
package com.example.personal_studio.domain.bitimport.model

import com.example.personal_studio.data.network.bit.NetworkMode

data class LoginRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
)

sealed interface LoginOutcome {
    object Success : LoginOutcome
    object WrongCredentials : LoginOutcome
    object AccountLocked : LoginOutcome
    object CaptchaRequired : LoginOutcome
    data class NetworkFail(val cause: String) : LoginOutcome
    data class Unexpected(val cause: String) : LoginOutcome
}
```

- [ ] **Step 2: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.domain.bitimport.model.LoginRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateCredentialsUseCaseTest {
    private fun req() = LoginRequest("u", "p", NetworkMode.LOCAL)

    private fun useCase(sso: SsoLoginUseCase, api: BitApiClient = mockk(relaxed = true)) =
        ValidateCredentialsUseCase(api, sso) to api

    @Test fun `success maps to Success`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.Success
        val (uc, _) = useCase(sso)
        assertEquals(LoginOutcome.Success, uc.invoke(req()))
    }

    @Test fun `wrong credentials maps`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        assertEquals(LoginOutcome.WrongCredentials, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `account locked maps`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.AccountLocked
        assertEquals(LoginOutcome.AccountLocked, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `captcha maps`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.CaptchaRequired
        assertEquals(LoginOutcome.CaptchaRequired, ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req()))
    }

    @Test fun `unknown failure maps to Unexpected`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.UnknownFailure("boom")
        val r = ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req())
        assert(r is LoginOutcome.Unexpected)
    }

    @Test fun `io exception maps to NetworkFail`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } throws java.io.IOException("net")
        val r = ValidateCredentialsUseCase(mockk(relaxed = true), sso).invoke(req())
        assert(r is LoginOutcome.NetworkFail)
    }

    @Test fun `close is always called`() = runTest {
        val sso = mockk<SsoLoginUseCase>(); coEvery { sso.invoke(any(), any(), any()) } returns CasLoginDto.Success
        val api = mockk<BitApiClient>(relaxed = true)
        ValidateCredentialsUseCase(api, sso).invoke(req())
        coVerify { api.close() }
    }
}
```

- [ ] **Step 3: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*ValidateCredentialsUseCaseTest*"`
Expected: FAIL(未定义)

- [ ] **Step 4: 写实现**

```kotlin
// ValidateCredentialsUseCase.kt
package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.domain.bitimport.model.LoginRequest
import javax.inject.Inject

/** 立即验证 BIT 凭据:open→CAS 登录→映射→close。不落库(存凭据由调用方在 Success 后做)。 */
class ValidateCredentialsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
) {
    suspend fun invoke(req: LoginRequest): LoginOutcome {
        return try {
            apiClient.open(req.networkMode)
            when (val dto = ssoLogin.invoke(apiClient, req.username, req.password)) {
                CasLoginDto.Success -> LoginOutcome.Success
                CasLoginDto.WrongCredentials -> LoginOutcome.WrongCredentials
                CasLoginDto.AccountLocked -> LoginOutcome.AccountLocked
                CasLoginDto.CaptchaRequired -> LoginOutcome.CaptchaRequired
                is CasLoginDto.UnknownFailure -> LoginOutcome.Unexpected(dto.body)
            }
        } catch (io: java.io.IOException) {
            LoginOutcome.NetworkFail(io.message ?: "io")
        } catch (e: Throwable) {
            LoginOutcome.Unexpected(e.message ?: e.javaClass.simpleName)
        } finally {
            apiClient.close()
        }
    }
}
```

- [ ] **Step 5: 运行 → PASS**(7 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*ValidateCredentialsUseCaseTest*"`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitimport/model/LoginModels.kt \
        app/src/main/java/com/example/personal_studio/domain/bitimport/ValidateCredentialsUseCase.kt \
        app/src/test/java/com/example/personal_studio/domain/bitimport/ValidateCredentialsUseCaseTest.kt
git commit -m "p8: LoginModels + ValidateCredentialsUseCase(立即验证)+ 7 单测"
```

---

### Task 2: `LoginPrefs`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/local/datastore/LoginPrefs.kt`
- Test: `app/src/test/java/com/example/personal_studio/data/local/datastore/LoginPrefsTest.kt`

注:复用共享 `DataStore<Preferences>`(`DataStoreModule` 已提供),无新 DI provider,key 前缀 `login_`。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LoginPrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(tmp.newFolder(), "login.preferences_pb") }

    @Test fun `default hasSeenLogin is false`() = runTest {
        assertEquals(false, LoginPrefs(newStore()).snapshot())
    }

    @Test fun `setHasSeenLogin persists`() = runTest {
        val prefs = LoginPrefs(newStore())
        prefs.setHasSeenLogin(true)
        assertEquals(true, prefs.snapshot())
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*LoginPrefsTest*"`

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 首屏登录门:是否已见过登录页(跳过或登录成功都置 true,首屏门只出现一次)。 */
@Singleton
class LoginPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keySeen = booleanPreferencesKey("login_has_seen")

    val observe: Flow<Boolean> = dataStore.data.map { it[keySeen] ?: false }
    suspend fun snapshot(): Boolean = observe.first()
    suspend fun setHasSeenLogin(v: Boolean) = dataStore.edit { it[keySeen] = v }
}
```

- [ ] **Step 4: 运行 → PASS**(2 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*LoginPrefsTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/datastore/LoginPrefs.kt \
        app/src/test/java/com/example/personal_studio/data/local/datastore/LoginPrefsTest.kt
git commit -m "p8: LoginPrefs(hasSeenLogin)+ 2 单测"
```

---

## Phase B · 登录 VM + 屏

### Task 3: `BitLoginViewModel`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/auth/BitLoginViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/auth/BitLoginViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.feature.auth

import app.cash.turbine.test
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.ValidateCredentialsUseCase
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BitLoginViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun creds(): ImportCredentialPrefs = mockk(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(null)
    }

    private fun vm(
        validate: ValidateCredentialsUseCase,
        credPrefs: ImportCredentialPrefs = creds(),
        loginPrefs: LoginPrefs = mockk(relaxed = true),
    ) = BitLoginViewModel(validate, credPrefs, loginPrefs)

    @Test fun `successful login saves creds + sets flag + emits Succeeded`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>()
        coEvery { validate.invoke(any()) } returns LoginOutcome.Success
        val credPrefs = creds()
        val loginPrefs = mockk<LoginPrefs>(relaxed = true)
        val sut = vm(validate, credPrefs, loginPrefs)
        sut.onUsernameChange("2024xx"); sut.onPasswordChange("pw")
        sut.events.test {
            sut.onLogin()
            advanceUntilIdle()
            assertEquals(BitLoginEvent.Succeeded, awaitItem())
        }
        coVerify { credPrefs.save("2024xx", "pw", NetworkMode.LOCAL) }
        coVerify { loginPrefs.setHasSeenLogin(true) }
    }

    @Test fun `login without rememberPwd clears creds`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase> { coEvery { invoke(any()) } returns LoginOutcome.Success }
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p"); sut.onRememberToggle(false)
        sut.onLogin(); advanceUntilIdle()
        coVerify { credPrefs.clear() }
    }

    @Test fun `wrong credentials sets error and does not save`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase> { coEvery { invoke(any()) } returns LoginOutcome.WrongCredentials }
        val credPrefs = creds()
        val sut = vm(validate, credPrefs)
        sut.onUsernameChange("u"); sut.onPasswordChange("p")
        sut.onLogin(); advanceUntilIdle()
        assertTrue(sut.uiState.value.error is LoginOutcome.WrongCredentials)
        assertEquals(false, sut.uiState.value.loading)
        coVerify(exactly = 0) { credPrefs.save(any(), any(), any()) }
    }

    @Test fun `skip sets flag and emits Skipped without saving`() = runTest {
        val validate = mockk<ValidateCredentialsUseCase>(relaxed = true)
        val credPrefs = creds()
        val loginPrefs = mockk<LoginPrefs>(relaxed = true)
        val sut = vm(validate, credPrefs, loginPrefs)
        sut.events.test {
            sut.onSkip()
            advanceUntilIdle()
            assertEquals(BitLoginEvent.Skipped, awaitItem())
        }
        coVerify { loginPrefs.setHasSeenLogin(true) }
        coVerify(exactly = 0) { credPrefs.save(any(), any(), any()) }
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*BitLoginViewModelTest*"`

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.ValidateCredentialsUseCase
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.domain.bitimport.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BitLoginUiState(
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val rememberPwd: Boolean = true,
    val networkMode: NetworkMode = NetworkMode.LOCAL,
    val loading: Boolean = false,
    val error: LoginOutcome? = null,
)

sealed interface BitLoginEvent {
    object Succeeded : BitLoginEvent
    object Skipped : BitLoginEvent
}

@HiltViewModel
class BitLoginViewModel @Inject constructor(
    private val validate: ValidateCredentialsUseCase,
    private val credPrefs: ImportCredentialPrefs,
    private val loginPrefs: LoginPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BitLoginUiState())
    val uiState: StateFlow<BitLoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BitLoginEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BitLoginEvent> = _events.asSharedFlow()

    init {
        // 预填已存凭据(若有)。
        credPrefs.observeAll().value?.let { saved ->
            _uiState.update {
                it.copy(
                    username = saved.username,
                    password = saved.password,
                    rememberPwd = true,
                    networkMode = saved.lastMode ?: NetworkMode.LOCAL,
                )
            }
        }
    }

    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onRememberToggle(v: Boolean) = _uiState.update { it.copy(rememberPwd = v) }
    fun onNetworkModeChange(m: NetworkMode) = _uiState.update { it.copy(networkMode = m) }
    fun onDismissError() = _uiState.update { it.copy(error = null) }

    fun onLogin() {
        val st = _uiState.value
        if (st.username.isBlank() || st.password.isBlank() || st.loading) return
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val outcome = validate.invoke(LoginRequest(st.username, st.password, st.networkMode))
            if (outcome is LoginOutcome.Success) {
                if (st.rememberPwd) credPrefs.save(st.username, st.password, st.networkMode)
                else credPrefs.clear()
                loginPrefs.setHasSeenLogin(true)
                _uiState.update { it.copy(loading = false) }
                _events.emit(BitLoginEvent.Succeeded)
            } else {
                _uiState.update { it.copy(loading = false, error = outcome) }
            }
        }
    }

    fun onSkip() {
        viewModelScope.launch {
            loginPrefs.setHasSeenLogin(true)
            _events.emit(BitLoginEvent.Skipped)
        }
    }
}
```

- [ ] **Step 4: 运行 → PASS**(4 用例)

注:Turbine 已在测试依赖(P6 GradesViewModelTest 等用过)。若 `app.cash.turbine` 不可用,改用收集到 list 的方式断言事件。

Run: `./gradlew :app:testDebugUnitTest --tests "*BitLoginViewModelTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/auth/BitLoginViewModel.kt \
        app/src/test/java/com/example/personal_studio/feature/auth/BitLoginViewModelTest.kt
git commit -m "p8: BitLoginViewModel(立即验证/记住/跳过 + 事件)+ 4 单测"
```

---

### Task 4: `BitLoginScreen`(门面视觉)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/auth/ui/BitLoginScreen.kt`

视觉要求(门面级,全部复用现有元素,不引新资源):整屏 `Void` 底 + `scanLines().vignette()` CRT 质感;VT323 hero(`displayLarge` + `Phosphor`);`$ 统一身份认证` 副题;表单包在 `AiFrame` 风格磷光框里;输入框用 `OutlinedTextField` + 主题色覆盖(`focusedBorderColor = Phosphor`、`cursorColor = Phosphor`);记住密码 Checkbox(`Phosphor`);校内/校外 `FilterChip`;主按钮磷光描边、loading 时文字 `验证中` + `BlinkingCursor`;错误 banner;`skipVisible` 为 true 时底部显示 `FoamDim` 文本「跳过」。

- [ ] **Step 1: 写实现**

```kotlin
package com.example.personal_studio.feature.auth.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitimport.model.LoginOutcome
import com.example.personal_studio.feature.auth.BitLoginEvent
import com.example.personal_studio.feature.auth.BitLoginViewModel
import com.example.personal_studio.ui.components.BlinkingCursor
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Dim
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BitLoginScreen(
    skipVisible: Boolean,
    onSucceeded: () -> Unit,
    onSkipped: () -> Unit,
    vm: BitLoginViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffectEvents(vm, onSucceeded, onSkipped)

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .scanLines()
            .vignette()
            .systemBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("PERSONAL // STUDIO", color = Phosphor, style = MaterialTheme.typography.displayMedium)
        Text("> 统一身份认证", color = FoamMute, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(28.dp))

        // ── AUTH ── 磷光框
        Column(
            Modifier
                .fillMaxWidth()
                .background(Phosphor.copy(alpha = 0.04f))
                .border(1.dp, Phosphor.copy(alpha = 0.45f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("── AUTH ──────────────", color = Phosphor.copy(alpha = 0.45f), style = MaterialTheme.typography.labelSmall)

            st.error?.let { err ->
                ErrorBox(err) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://login.bit.edu.cn/")))
                    }
                }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Phosphor,
                unfocusedBorderColor = Dim,
                cursorColor = Phosphor,
                focusedLabelColor = Phosphor,
                unfocusedLabelColor = FoamDim,
                focusedTextColor = Foam,
                unfocusedTextColor = Foam,
            )
            OutlinedTextField(
                value = st.username, onValueChange = vm::onUsernameChange,
                label = { Text("学号") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            OutlinedTextField(
                value = st.password, onValueChange = vm::onPasswordChange,
                label = { Text("密码") }, singleLine = true,
                visualTransformation = if (st.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = vm::onShowPasswordToggle) {
                        Icon(
                            if (st.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "toggle", tint = FoamMute,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = st.rememberPwd, onCheckedChange = vm::onRememberToggle,
                    colors = CheckboxDefaults.colors(checkedColor = Phosphor),
                )
                Text("记住密码（Keystore 加密）", color = FoamMute, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = st.networkMode == NetworkMode.LOCAL,
                    onClick = { vm.onNetworkModeChange(NetworkMode.LOCAL) }, label = { Text("校内") })
                FilterChip(selected = st.networkMode == NetworkMode.WEBVPN,
                    onClick = { vm.onNetworkModeChange(NetworkMode.WEBVPN) }, label = { Text("校外") })
            }
        }

        Spacer(Modifier.height(20.dp))
        // 主按钮:磷光描边
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, if (st.loading) FoamDim else Phosphor)
                .background(Phosphor.copy(alpha = if (st.loading) 0f else 0.08f))
                .padding(vertical = 12.dp)
                .let { if (!st.loading) it else it }
                .clickableNoRipple(enabled = !st.loading) { vm.onLogin() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (st.loading) "验证中" else "登录 →", color = if (st.loading) FoamDim else Phosphor)
            if (st.loading) { Spacer(Modifier.height(0.dp)); BlinkingCursor() }
        }

        if (skipVisible) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = vm::onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("跳过", color = FoamDim)
            }
        }
    }
}

@Composable
private fun LaunchedEffectEvents(vm: BitLoginViewModel, onSucceeded: () -> Unit, onSkipped: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.events.collectLatest { ev ->
            when (ev) {
                BitLoginEvent.Succeeded -> onSucceeded()
                BitLoginEvent.Skipped -> onSkipped()
            }
        }
    }
}

@Composable
private fun ErrorBox(err: LoginOutcome, onOpenWeb: () -> Unit) {
    val msg = when (err) {
        LoginOutcome.WrongCredentials -> "学号或密码错误"
        LoginOutcome.AccountLocked -> "账号已锁定,请稍后或网页端处理"
        LoginOutcome.CaptchaRequired -> "需要验证码,请在网页端登录一次后重试"
        is LoginOutcome.NetworkFail -> "连接失败,检查网络或切换校内/校外后重试"
        is LoginOutcome.Unexpected -> "登录失败,请重试"
        LoginOutcome.Success -> return
    }
    Column(
        Modifier.fillMaxWidth().border(1.dp, Carmine.copy(alpha = 0.6f)).padding(8.dp),
    ) {
        Text(msg, color = Carmine, style = MaterialTheme.typography.labelMedium)
        if (err == LoginOutcome.CaptchaRequired) {
            TextButton(onClick = onOpenWeb) { Text("打开网页端", color = Amber) }
        }
    }
}

/** 无涟漪点击(终端风格按钮)。 */
private fun Modifier.clickableNoRipple(enabled: Boolean, onClick: () -> Unit): Modifier = this.then(
    androidx.compose.foundation.clickable(enabled = enabled, indication = null,
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()) { onClick() }
)
```

注:`import androidx.compose.runtime.LaunchedEffect` 已通过全限定使用。若 `clickableNoRipple` 的 `MutableInteractionSource` 写法编译告警,可改用普通 `Modifier.clickable`。门面动效(开机自检序列)留待真机目检后按需加,本步先实现静态门面。

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（主题色名/组件 API 不符则对照 `ui/theme/Color.kt` 调整）

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/auth/ui/BitLoginScreen.kt
git commit -m "p8: BitLoginScreen 门面登录屏(CRT 底 + VT323 + 磷光框 + 光标)"
```

---

## Phase C · 导航接入

### Task 5: `NavRoutes.LOGIN` + AppNavHost 注册

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`

- [ ] **Step 1: NavRoutes 加 LOGIN**

在 `NavRoutes` 顶部 import 区确认有 `import android.net.Uri`(已存在)。加:
```kotlin
    const val LOGIN = "login?next={next}"
    fun login(next: String? = null) =
        if (next.isNullOrBlank()) "login?next=" else "login?next=${Uri.encode(next)}"
```

- [ ] **Step 2: AppNavHost 注册 login composable**

import 加 `import com.example.personal_studio.feature.auth.ui.BitLoginScreen`。在 NavHost 内追加:
```kotlin
        composable(
            route = NavRoutes.LOGIN,
            arguments = listOf(navArgument("next") { type = NavType.StringType; defaultValue = "" }),
        ) { backStack ->
            val next = backStack.arguments?.getString("next").orEmpty().ifBlank { null }?.let { Uri.decode(it) }
            BitLoginScreen(
                skipVisible = next == null,   // 首屏(无 next)才给跳过;守卫场景必登
                onSucceeded = {
                    if (next != null) navController.navigate(next) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    } else navController.navigate(NavRoutes.CHAT) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                },
                onSkipped = {
                    navController.navigate(NavRoutes.CHAT) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                },
            )
        }
```
AppNavHost 顶部 import 加 `import android.net.Uri`(若无)。

- [ ] **Step 3: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt \
        app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "p8: NavRoutes.LOGIN(login?next=)+ AppNavHost 注册登录路由"
```

---

### Task 6: 冷启动起始目的地 + splash

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/auth/RootViewModel.kt`
- Create: `app/src/main/java/com/example/personal_studio/ui/components/TerminalSplash.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/MainScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`(startDestination 参数化)
- Test: `app/src/test/java/com/example/personal_studio/feature/auth/RootViewModelTest.kt`

- [ ] **Step 1: 写 RootViewModel 失败测试**

```kotlin
package com.example.personal_studio.feature.auth

import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.ui.navigation.NavRoutes
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RootViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `unseen login starts at LOGIN`() = runTest {
        val prefs = mockk<LoginPrefs> { every { observe } returns flowOf(false) }
        val vm = RootViewModel(prefs)
        val job = launch { vm.startDestination.collect {} }
        advanceUntilIdle()
        assertEquals(NavRoutes.LOGIN, vm.startDestination.value)
        job.cancel()
    }

    @Test fun `seen login starts at CHAT`() = runTest {
        val prefs = mockk<LoginPrefs> { every { observe } returns flowOf(true) }
        val vm = RootViewModel(prefs)
        val job = launch { vm.startDestination.collect {} }
        advanceUntilIdle()
        assertEquals(NavRoutes.CHAT, vm.startDestination.value)
        job.cancel()
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*RootViewModelTest*"`

- [ ] **Step 3: 写 RootViewModel**

```kotlin
package com.example.personal_studio.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.LoginPrefs
import com.example.personal_studio.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 冷启动决定起始目的地:见过登录 → CHAT;否则 → LOGIN。初值 null = splash 中。 */
@HiltViewModel
class RootViewModel @Inject constructor(
    loginPrefs: LoginPrefs,
) : ViewModel() {
    val startDestination: StateFlow<String?> =
        loginPrefs.observe
            .map { seen -> if (seen) NavRoutes.CHAT else NavRoutes.LOGIN }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
```

- [ ] **Step 4: 运行 → PASS**(2 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*RootViewModelTest*"`

- [ ] **Step 5: 写 TerminalSplash**

```kotlin
package com.example.personal_studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import com.example.personal_studio.ui.theme.scanLines
import com.example.personal_studio.ui.theme.vignette

@Composable
fun TerminalSplash() {
    Box(Modifier.fillMaxSize().background(Void).scanLines().vignette(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PERSONAL // STUDIO", color = Phosphor, style = MaterialTheme.typography.displayMedium)
            Text("booting…", color = FoamDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}
```

- [ ] **Step 6: AppNavHost 起始目的地参数化**

把 `fun AppNavHost(navController: NavHostController)` 改为带默认值的参数:
```kotlin
fun AppNavHost(navController: NavHostController, startDestination: String = NavRoutes.CHAT) {
    ...
    NavHost(navController = navController, startDestination = startDestination) { ... }
}
```

- [ ] **Step 7: MainScreen 读起始目的地 + splash**

`MainScreen` 顶部加(在 `@Composable fun MainScreen(...)` 内,Scaffold 之前):
```kotlin
    val rootVm: com.example.personal_studio.feature.auth.RootViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val startDest by rootVm.startDestination.collectAsStateWithLifecycle()
    if (startDest == null) {
        com.example.personal_studio.ui.components.TerminalSplash()
        return
    }
```
并把底部 `AppNavHost(navController = navController)` 改为 `AppNavHost(navController = navController, startDestination = startDest!!)`。
加 import:`import androidx.lifecycle.compose.collectAsStateWithLifecycle`、`import androidx.compose.runtime.getValue`(若无)。

- [ ] **Step 8: 编译 + 测试**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "*RootViewModelTest*"`
Expected: BUILD SUCCESSFUL;2 PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/auth/RootViewModel.kt \
        app/src/test/java/com/example/personal_studio/feature/auth/RootViewModelTest.kt \
        app/src/main/java/com/example/personal_studio/ui/components/TerminalSplash.kt \
        app/src/main/java/com/example/personal_studio/ui/MainScreen.kt \
        app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "p8: 冷启动 RootViewModel 决定起始目的地 + 终端 splash + 单测"
```

---

## Phase D · 收敛

### Task 7: 成绩收敛(退休 GradesSyncScreen,进度并入成绩页)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesViewModel.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/GradesScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- Delete: `feature/bitgrades/GradesSyncViewModel.kt`, `feature/bitgrades/ui/GradesSyncScreen.kt`
- Test: 追加到 `app/src/test/java/com/example/personal_studio/feature/bitgrades/GradesViewModelTest.kt`

- [ ] **Step 1: GradesViewModel 加同步能力(先写测试)**

在现有 `GradesViewModelTest` 追加(注意现有测试构造 VM 的方式,新增 2 个依赖要一并传):

```kotlin
    @Test fun `onSyncNow with saved creds runs sync and surfaces progress`() = runTest {
        val dao = mockk<GradesDao>(relaxed = true) {
            every { observeAll() } returns flowOf(emptyList())
            every { observeRanks() } returns flowOf(emptyList())
        }
        val cache = mockk<GradesAnalysisCache> { every { observe } returns flowOf(null) }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
        }
        val sync = mockk<SyncGradesUseCase> {
            every { sync(any()) } returns flowOf(SyncGradesStep.LoggingIn, SyncGradesStep.Done(1, 3))
        }
        val vm = GradesViewModel(dao, ComputeGpaUseCase(), mockk(relaxed = true), mockk(relaxed = true), cache, sync, creds)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.loggedIn)
        vm.onSyncNow()
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.syncing)
        job.cancel()
    }
```
(import `MutableStateFlow`/`SavedCredentials`/`NetworkMode`/`SyncGradesUseCase`/`SyncGradesStep`/`ImportCredentialPrefs`。)

- [ ] **Step 2: 运行 → FAIL**(构造签名不符 / onSyncNow 未定义)

- [ ] **Step 3: 改 GradesViewModel**

构造函数追加两个依赖:
```kotlin
@HiltViewModel
class GradesViewModel @Inject constructor(
    private val dao: GradesDao,
    private val computeGpa: ComputeGpaUseCase,
    private val analyze: AnalyzeGradesUseCase,
    private val startChat: StartGradeChatUseCase,
    private val analysisCache: GradesAnalysisCache,
    private val syncGrades: com.example.personal_studio.domain.bitgrades.SyncGradesUseCase,
    private val credPrefs: com.example.personal_studio.data.local.datastore.ImportCredentialPrefs,
) : ViewModel()
```
`GradesUiState` 追加字段(带默认):
```kotlin
    val loggedIn: Boolean = false,
    val syncing: Boolean = false,
    val syncSteps: List<String> = emptyList(),
    val syncError: com.example.personal_studio.domain.bitgrades.model.GradesSyncError? = null,
```
把 `loggedIn` 并入 `uiState` 的 combine(加入 `credPrefs.observeAll()` 源;同步态用一个独立 `MutableStateFlow` 合并,模式同 AssignmentsViewModel 的 `transient`)。新增:
```kotlin
    private val syncLocal = MutableStateFlow(SyncLocalState())
    data class SyncLocalState(val syncing: Boolean = false, val steps: List<String> = emptyList(),
                              val error: GradesSyncError? = null)

    fun onSyncNow() {
        val creds = credPrefs.observeAll().value ?: return
        if (syncLocal.value.syncing) return
        syncLocal.update { it.copy(syncing = true, steps = emptyList(), error = null) }
        viewModelScope.launch {
            val req = GradesSyncRequest(creds.username, creds.password, creds.lastMode ?: NetworkMode.LOCAL, true)
            syncGrades.sync(req).collect { step ->
                syncLocal.update { cur ->
                    when (step) {
                        SyncGradesStep.LoggingIn -> cur.copy(steps = cur.steps + "登录中…")
                        SyncGradesStep.FetchingGrades -> cur.copy(steps = cur.steps + "拉取成绩…")
                        SyncGradesStep.FetchingRanks -> cur.copy(steps = cur.steps + "拉取排名…")
                        SyncGradesStep.Persisting -> cur.copy(steps = cur.steps + "落库…")
                        is SyncGradesStep.Done -> cur.copy(syncing = false, steps = cur.steps + "完成")
                        is SyncGradesStep.Failed -> cur.copy(syncing = false, error = step.err)
                    }
                }
            }
        }
    }
```
把 `syncLocal` + `credPrefs.observeAll()` 并入 `uiState` 的 combine(填充 `loggedIn/syncing/syncSteps/syncError`)。(import `GradesSyncRequest`/`SyncGradesStep`/`GradesSyncError`/`NetworkMode`/`MutableStateFlow`/`update`。)

- [ ] **Step 4: 改 GradesScreen**

签名把 `onSync: () -> Unit` 改为 `onNeedLogin: () -> Unit`。两处同步触发改:
```kotlin
TextButton(onClick = { if (st.loggedIn) vm.onSyncNow() else onNeedLogin() }) {
    Text(if (st.syncing) "同步中…" else "↻ 同步")
}
```
空态 CTA 同理。在合适位置(如顶部下方)显示同步进度:
```kotlin
if (st.syncSteps.isNotEmpty()) {
    st.syncSteps.forEach { Text("> $it", color = Phosphor, style = MaterialTheme.typography.labelMedium) }
}
st.syncError?.let { Text("⚠ 同步失败", color = Carmine, style = MaterialTheme.typography.labelMedium) }
```
(import `Carmine`/`Phosphor` 若无。)

- [ ] **Step 5: AppNavHost 改 grades 入口 + 删 GRADES_SYNC**

grades composable:`onSync = { navController.navigate(NavRoutes.GRADES_SYNC) }` → `onNeedLogin = { navController.navigate(NavRoutes.login("grades")) }`。
删除整个 `composable(NavRoutes.GRADES_SYNC) { GradesSyncRoute(...) }` 块及其 import。
删 `NavRoutes.GRADES_SYNC` 常量。

- [ ] **Step 6: 删退休文件**

```bash
git rm app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesSyncViewModel.kt \
       app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/GradesSyncScreen.kt
```
若有 `GradesSyncViewModelTest` 一并 `git rm`。

- [ ] **Step 7: 编译 + 全量测试**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;全绿(含新 GradesViewModel 用例)。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "p8: 成绩收敛 — 退休 GradesSyncScreen,登录守卫 + 同步进度并入成绩页"
```

---

### Task 8: 导入收敛(已登录自动导入,凭据步退休)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitimport/ImportViewModel.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitimport/ImportNavGraph.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
- Delete: `feature/bitimport/ui/ImportCredentialsScreen.kt`

- [ ] **Step 1: ImportViewModel 加 startWithSavedCreds(先写测试)**

```kotlin
// 追加到 ImportViewModel 测试(若无则新建 ImportViewModelTest)
@Test fun `startWithSavedCreds triggers import when creds saved`() = runTest {
    val importUseCase = mockk<ImportCoursesUseCase>(relaxed = true) {
        every { import(any(), any()) } returns flowOf(/* ImportStep.LoggingIn 等 */)
    }
    val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(SavedCredentials("u", "p", NetworkMode.LOCAL))
    }
    val vm = ImportViewModel(importUseCase, creds)
    vm.startWithSavedCreds()
    advanceUntilIdle()
    verify { importUseCase.import(any(), any()) }
}
```
(import 据 `ImportCoursesUseCase.import` 的真实签名调整;若其签名带 `Channel`,mock 时用 `any()`。)

- [ ] **Step 2: 运行 → FAIL**

- [ ] **Step 3: 改 ImportViewModel**

加方法(复用现有 `onLogin()` 的请求构造逻辑,但凭据来自存储而非表单输入):
```kotlin
    /** 已登录时由 ImportEntryRoute 调用:用存好的凭据直接导入,跳过凭据表单。 */
    fun startWithSavedCreds() {
        val saved = credPrefs.observeAll().value ?: return
        _uiState.update {
            it.copy(username = saved.username, password = saved.password,
                    networkMode = saved.lastMode ?: NetworkMode.LOCAL, rememberPwd = true)
        }
        onLogin()   // 复用既有导入编排
    }
```
(`onLogin()` 内部已用 `_uiState` 的 username/password/networkMode 构造 `ImportRequest`。)

- [ ] **Step 4: 改 ImportEntryRoute**

加 `onNeedLogin: () -> Unit` 参数;进入时按登录态分流:
```kotlin
@Composable
fun ImportEntryRoute(
    onClose: () -> Unit,
    onNeedLogin: () -> Unit,
    vm: ImportViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val loggedIn = remember { vm.isLoggedIn() }   // 见下:VM 暴露一次性判断
    LaunchedEffect(Unit) {
        if (!loggedIn) onNeedLogin() else vm.startWithSavedCreds()
    }
    LaunchedEffect(ui.done) { if (ui.done) onClose() }
    when (ui.currentScreen) {
        ImportScreen.Credentials -> {
            // 已登录会立即自动导入并切到 Progress;此分支仅作自动导入前的瞬时占位。
            com.example.personal_studio.ui.components.TerminalSplash()
        }
        ImportScreen.TermPicker  -> ImportTermPickerScreen(vm = vm)  // 仍未启用
        ImportScreen.Progress    -> ImportProgressScreen(vm = vm)
        ImportScreen.Preview     -> ImportPreviewScreen(vm = vm)
    }
}
```
`ImportViewModel` 加:`fun isLoggedIn(): Boolean = credPrefs.observeAll().value != null`。

- [ ] **Step 5: AppNavHost 传 onNeedLogin**

```kotlin
composable(NavRoutes.IMPORT_WIZARD) {
    ImportEntryRoute(
        onClose = { navController.popBackStack() },
        onNeedLogin = {
            navController.navigate(NavRoutes.login("import")) { popUpTo(NavRoutes.IMPORT_WIZARD) { inclusive = true } }
        },
    )
}
```

- [ ] **Step 6: 删 ImportCredentialsScreen**

```bash
git rm app/src/main/java/com/example/personal_studio/feature/bitimport/ui/ImportCredentialsScreen.kt
```
(ImportEntryRoute 不再引用它。`ImportViewModel` 的表单 setter 可保留不删——无害,后续清理。)

- [ ] **Step 7: 编译 + 测试**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;全绿。

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "p8: 导入收敛 — 已登录自动用存凭据导入,凭据步退休 + 入口守卫"
```

---

### Task 9: DDL 收敛(未登录发导航事件)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitddl/AssignmentsViewModel.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/bitddl/ui/AssignmentsScreen.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
- Test: 追加到 `AssignmentsViewModelTest`

- [ ] **Step 1: 写失败测试**

```kotlin
    @Test fun `refresh without creds emits NeedLogin event`() = runTest {
        val dao = mockk<TimelineDao>(relaxed = true) { every { observeLexueDdls() } returns flowOf(emptyList()) }
        val vm = AssignmentsViewModel(
            dao = dao, toggleDone = mockk(relaxed = true), cancelReminders = mockk(relaxed = true),
            scheduleReminders = mockk(relaxed = true), repo = mockk(relaxed = true),
            sync = mockk(relaxed = true),
            credPrefs = mockk(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) },
            nowProvider = { 0L },
        )
        vm.events.test {
            vm.onRefresh()
            advanceUntilIdle()
            assertEquals(AssignmentsEvent.NeedLogin, awaitItem())
        }
    }
```
(import `app.cash.turbine.test`、`AssignmentsEvent`。)

- [ ] **Step 2: 运行 → FAIL**

- [ ] **Step 3: 改 AssignmentsViewModel**

加事件类型 + SharedFlow:
```kotlin
sealed interface AssignmentsEvent { object NeedLogin : AssignmentsEvent }

// 在 VM 内:
private val _events = kotlinx.coroutines.flow.MutableSharedFlow<AssignmentsEvent>(extraBufferCapacity = 4)
val events: kotlinx.coroutines.flow.SharedFlow<AssignmentsEvent> = _events.asSharedFlow()
```
`onRefresh()` 把 `creds == null` 分支由"设 error"改为发事件:
```kotlin
    fun onRefresh() {
        val creds = credPrefs.observeAll().value ?: run {
            viewModelScope.launch { _events.emit(AssignmentsEvent.NeedLogin) }
            return
        }
        ... // 其余不变
    }
```
(import `asSharedFlow`、`launch` 若无。)

- [ ] **Step 4: 改 AssignmentsScreen**

加 `onNeedLogin: () -> Unit` 参数;收集事件:
```kotlin
androidx.compose.runtime.LaunchedEffect(Unit) {
    vm.events.collect { if (it is com.example.personal_studio.feature.bitddl.AssignmentsEvent.NeedLogin) onNeedLogin() }
}
```

- [ ] **Step 5: AppNavHost 传 onNeedLogin**

assignments composable:
```kotlin
com.example.personal_studio.feature.bitddl.ui.AssignmentsScreen(
    onBack = { navController.popBackStack() },
    onNeedLogin = { navController.navigate(NavRoutes.login("assignments")) },
)
```

- [ ] **Step 6: 编译 + 测试**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "*AssignmentsViewModelTest*"`
Expected: BUILD SUCCESSFUL;全绿。

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "p8: DDL 收敛 — 未登录刷新发 NeedLogin 事件跳登录"
```

---

## Phase E · 账号管理 + 退出

### Task 10: Settings「BIT 账号」行 + onLogout

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/vm/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`
- Test: 追加到 `SettingsViewModelTest`(若无则新建)

- [ ] **Step 1: 写失败测试**

```kotlin
    @Test fun `logout clears creds and stops both pollers`() = runTest {
        val credPrefs = mockk<ImportCredentialPrefs>(relaxed = true) { every { observeAll() } returns MutableStateFlow(null) }
        val gradesSync = mockk<GradesSyncPrefs>(relaxed = true)
        val ddlSync = mockk<DdlSyncPrefs>(relaxed = true)
        val gradesSched = mockk<GradesPollScheduler>(relaxed = true)
        val ddlSched = mockk<DdlPollScheduler>(relaxed = true)
        val vm = SettingsViewModel(mockk(relaxed = true), mockk(relaxed = true),
            credPrefs, gradesSync, ddlSync, gradesSched, ddlSched)
        vm.onLogout(); advanceUntilIdle()
        verify { credPrefs.clear() }
        coVerify { gradesSync.setEnabled(false) }
        coVerify { ddlSync.setEnabled(false) }
        verify { gradesSched.cancel() }
        verify { ddlSched.cancel() }
    }
```
(import 对应类型;前两个 mock 是现有 `UserPreferencesRepository`/`LLMProvider`。)

- [ ] **Step 2: 运行 → FAIL**

- [ ] **Step 3: 改 SettingsViewModel**

构造追加 5 个依赖;uiState 加 `loggedInUsername: String?`;加 `onLogout`:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val llm: LLMProvider,
    private val credPrefs: com.example.personal_studio.data.local.datastore.ImportCredentialPrefs,
    private val gradesSyncPrefs: com.example.personal_studio.data.local.datastore.GradesSyncPrefs,
    private val ddlSyncPrefs: com.example.personal_studio.data.local.datastore.DdlSyncPrefs,
    private val gradesPollScheduler: com.example.personal_studio.feature.bitgrades.GradesPollScheduler,
    private val ddlPollScheduler: com.example.personal_studio.feature.bitddl.DdlPollScheduler,
) : ViewModel() {
    // 把 credPrefs.observeAll() 并入 uiState,填充 loggedInUsername = it?.username
    fun onLogout() {
        credPrefs.clear()
        gradesPollScheduler.cancel()
        ddlPollScheduler.cancel()
        viewModelScope.launch {
            gradesSyncPrefs.setEnabled(false)
            ddlSyncPrefs.setEnabled(false)
        }
    }
}
```
把现有 uiState 的构建改为 combine 进 `credPrefs.observeAll()`(若当前不是 Flow combine,则新增一个并发 collect 更新 `loggedInUsername`)。

- [ ] **Step 4: 改 SettingsScreen**

在 `## timeline` 区顶部(`SectionHeader("## timeline")` 之后、SEMESTER 行之前)加账号行:
```kotlin
val acct = st.loggedInUsername
if (acct == null) {
    NavigableRowWithSubtitle(key = "BIT 账号", value = "未登录,点此登录 →",
        subtitle = "登录后成绩 / 课表 / 作业免再输密码",
        onClick = { onNavigate(NavRoutes.login("settings")) })
} else {
    KeyValueRow(key = "BIT 账号", value = acct, valueColor = Phosphor)
    NavigableRow(key = "", value = "退出登录", onClick = vm::onLogout)
}
```
(`st` = `vm.uiState` 收集值;`NavRoutes` 全限定或已 import;`Phosphor` import。`onNavigate` 是现有参数。)

- [ ] **Step 5: 编译 + 测试**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;全绿。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "p8: Settings BIT 账号行 + 退出登录(清凭据 + 关两轮询)+ 单测"
```

---

## Phase F · 真机 DoD

### Task 11: 真机端到端验证

**Files:** 无代码。

- [ ] **Step 1: 装机**

Run: `./gradlew :app:installDebug`

- [ ] **Step 2: 首屏门**

全新安装(或 `adb shell pm clear` / 清 datastore)→ 首次打开进 `BitLoginScreen`,门面视觉(CRT 底 + VT323 logo + 磷光框)。

- [ ] **Step 3: 验证 + 错误**

输错密码 → banner "学号或密码错误",不进 app、不存凭据。正确登录 → 进 CHAT;重开 app 不再首屏弹登录。

- [ ] **Step 4: 跳过**

清数据重开 → 点「跳过」→ 进 CHAT;重开不再弹;Settings「BIT 账号」显"未登录"。

- [ ] **Step 5: 守卫 + 回跳**

未登录(跳过状态)点成绩「↻ 同步」/ 设置「导入课表」/ 作业页「刷新」→ 跳 `BitLoginScreen`(无跳过按钮),登录成功 → 回到该功能并继续(成绩开始同步 / 导入开始 / 作业刷新)。

- [ ] **Step 6: 已登录直连**

成绩页「↻ 同步」直接同步(内联进度 `> 登录中…` 等);设置「导入课表」直接进导入进度;作业页「刷新」直接拉。

- [ ] **Step 7: 退出登录**

Settings「BIT 账号」显学号 → 退出 → 凭据清除;`adb shell dumpsys jobscheduler | grep -E "grades-poll|ddl-poll"` 两个轮询任务消失;各功能恢复未登录拦截。

- [ ] **Step 8: 视觉门面验收**

确认登录屏不偏离终端/磷光绿风格、有高级感(CRT 质感、VT323、磷光辉光、光标)。按需启用/降级 §6.4 引导动效。

- [ ] **Step 9: 全量单测收尾**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 全绿。准备 PR。

---

## Self-Review

**Spec 覆盖**(对照 `2026-05-29-unified-login-design.md`):
- §2 模块布局 → 各 Task 文件一一对应
- §3 登录态语义/首屏门/守卫/回跳 → Task 2(LoginPrefs)+ 5(login 路由 + next 回跳)+ 6(起始目的地)
- §4 ValidateCredentialsUseCase + 错误 → Task 1 + Task 3(VM 映射)+ Task 4(banner 文案)
- §5 收敛三处 → Task 7(成绩)+ 8(导入,已修正为自动导入)+ 9(DDL)
- §6 UI 门面 → Task 4(BitLoginScreen)+ Task 6(splash)
- §7 退出 → Task 10
- §8 测试 → 各 Task TDD + Task 1/2/3/6/9/10 单测
- §11 DoD → Task 11

**占位符**:无 TBD。门面引导动效(§6.4)明确标"留待真机目检按需加/降级"(非阻塞,有默认=静态门面)。`ImportCoursesUseCase.import` 签名以真实为准(Task 8 注明 mock 用 any())。

**Type 一致性**:`LoginOutcome`(T1)在 T3/T4 一致;`LoginRequest`(T1)在 T1/T3 一致;`BitLoginEvent.Succeeded/Skipped`(T3)在 T4 一致;`LoginPrefs.observe/snapshot/setHasSeenLogin`(T2)在 T3/T6 一致;`NavRoutes.login(next)`(T5)在 T7/T8/T9/T10 一致;`AssignmentsEvent.NeedLogin`(T9)测试与实现一致;`GradesSyncRequest`/`SyncGradesStep`(现有)在 T7 按真实签名用。

**已知现状约束已在计划注明**:sync/import 自带登录(读存的 creds 喂 request)、TermPicker 死代码(不启用)、不建 AuthStatus、suspend vs 同步 API。
