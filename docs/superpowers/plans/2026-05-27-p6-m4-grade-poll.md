# P6 · M4 出分提醒(后台轮询)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** WorkManager 周期任务静默登录 BIT 教务,diff 签名集发现新成绩 → 推聚合通知。Opt-in,默认 off。CAS 鉴权失败立即停轮 + 清凭据 + 通知,避免锁号循环。

**Architecture:** 复用 P5 `SsoLoginUseCase` / `BitApiClient` / `ImportCredentialPrefs` 已有的登录基建,M1 `JsxsdGradeParser` 解析,F1 `JsxsdDetailParser` 补详情(仅对新条目)。新增 `GradesSyncPrefs`(DataStore)、`DetectNewGradesUseCase`、`GradesPollScheduler`、`GradePollWorker`(HiltWorker)、`GradesNotifier`、Settings 屏幕。`SyncGradesUseCase` 扩一个 `syncForBackground(req): BackgroundSyncResult` 静默版本(主线 `sync()` Flow 不动)。

**Tech Stack:** Kotlin · WorkManager + Hilt-Work(HiltWorkerFactory 已在 `PersonalStudioApp` 配)· DataStore Preferences · NotificationCompat · MockK + JUnit4 + Turbine。

---

## File Structure

**新建(主代码):**
- `app/src/main/java/com/example/personal_studio/data/local/datastore/GradesSyncPrefs.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/DetectNewGradesUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/model/BackgroundSyncResult.kt`
- `app/src/main/java/com/example/personal_studio/core/notification/GradesNotifier.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesPollScheduler.kt`
- `app/src/main/java/com/example/personal_studio/core/workers/GradePollWorker.kt`
- `app/src/main/java/com/example/personal_studio/feature/settings/ui/GradesPollSettingsScreen.kt`
- `app/src/main/java/com/example/personal_studio/feature/settings/vm/GradesPollSettingsViewModel.kt`

**修改:**
- `app/src/main/java/com/example/personal_studio/core/notification/NotificationChannels.kt` — 加 `GRADES_ID`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt` — 加 `syncForBackground(req)`
- `app/src/main/java/com/example/personal_studio/core/workers/BootCompletedReceiver.kt` — 顺带重排成绩轮询
- `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt` — `SETTINGS_GRADES_POLL`
- `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt` — 路由注册
- `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt` — 入口行

**测试:** mirror 路径下 `app/src/test/java/...`(纯 JVM 单测,无 Android 仪器测试)。

---

## Phase A · 数据/工具层

### Task 1: `GradesSyncPrefs`(DataStore)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/local/datastore/GradesSyncPrefs.kt`
- Test: `app/src/test/java/com/example/personal_studio/data/local/datastore/GradesSyncPrefsTest.kt`

- [ ] **Step 1: 写失败测试**(JVM 单测,用 `PreferenceDataStoreFactory.create` 配 in-memory File)

```kotlin
package com.example.personal_studio.data.local.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class GradesSyncPrefsTest {
    private lateinit var tmp: File
    private lateinit var prefs: GradesSyncPrefs

    @Before fun setUp() {
        tmp = File.createTempFile("grades_sync_prefs", ".preferences_pb").also { it.delete() }
        val ds = PreferenceDataStoreFactory.create { tmp }
        prefs = GradesSyncPrefs(ds)
    }
    @After fun tearDown() { tmp.delete() }

    @Test fun `defaults are off and 6h`() = runTest {
        val s = prefs.observe.first()
        assertEquals(false, s.enabled)
        assertEquals(6, s.intervalHours)
        assertEquals(null, s.lastSyncAt)
        assertEquals(emptySet<String>(), s.lastSeenSignature)
    }

    @Test fun `set and read signature round trip`() = runTest {
        prefs.setLastSeenSignature(setOf("a|b|正常|92", "x|y|重修|55"))
        val s = prefs.observe.first()
        assertEquals(setOf("a|b|正常|92", "x|y|重修|55"), s.lastSeenSignature)
    }

    @Test fun `interval and enabled persist`() = runTest {
        prefs.setEnabled(true)
        prefs.setIntervalHours(12)
        prefs.setLastSyncAt(123456L)
        val s = prefs.observe.first()
        assertEquals(true, s.enabled)
        assertEquals(12, s.intervalHours)
        assertEquals(123456L, s.lastSyncAt)
    }
}
```

- [ ] **Step 2: 运行测试,确认 FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesSyncPrefsTest*"`
Expected: FAIL(GradesSyncPrefs 未定义)

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** M4 后台出分轮询的偏好集。Signature 集以 `\n` 分隔的 String 序列化。 */
data class GradesSyncState(
    val enabled: Boolean,
    val intervalHours: Int,
    val lastSyncAt: Long?,
    val lastSeenSignature: Set<String>,
)

@Singleton
class GradesSyncPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val keyEnabled = booleanPreferencesKey("grades_poll_enabled")
    private val keyInterval = intPreferencesKey("grades_poll_interval_hours")
    private val keyLastSyncAt = longPreferencesKey("grades_last_sync_at")
    private val keyLastSig = stringPreferencesKey("grades_last_seen_signature")

    val observe: Flow<GradesSyncState> = dataStore.data.map { p ->
        GradesSyncState(
            enabled = p[keyEnabled] ?: false,
            intervalHours = p[keyInterval] ?: 6,
            lastSyncAt = p[keyLastSyncAt],
            lastSeenSignature = p[keyLastSig]?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
        )
    }

    /** 一次性快照,Worker / BootReceiver 用。 */
    suspend fun snapshot(): GradesSyncState = observe.first()

    suspend fun setEnabled(v: Boolean) = dataStore.edit { it[keyEnabled] = v }
    suspend fun setIntervalHours(v: Int) = dataStore.edit { it[keyInterval] = v }
    suspend fun setLastSyncAt(v: Long) = dataStore.edit { it[keyLastSyncAt] = v }
    suspend fun setLastSeenSignature(sigs: Set<String>) = dataStore.edit {
        it[keyLastSig] = sigs.joinToString("\n")
    }
}
```

- [ ] **Step 4: 运行测试 → PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesSyncPrefsTest*"`
Expected: PASS(3 用例)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/datastore/GradesSyncPrefs.kt \
        app/src/test/java/com/example/personal_studio/data/local/datastore/GradesSyncPrefsTest.kt
git commit -m "p6(m4): GradesSyncPrefs DataStore + 签名集序列化 + 单测"
```

---

### Task 2: `DetectNewGradesUseCase`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/bitgrades/DetectNewGradesUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/DetectNewGradesUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectNewGradesUseCaseTest {
    private fun entry(term: String, code: String, attempt: String, score: String) = GradeEntryEntity(
        termCode = term, termName = term, courseName = code, courseCode = code, credit = 3.0,
        score = score, gradePoint = 4.0, gradeLetter = null, category = null,
        attemptType = attempt, isPass = true, fetchedAt = 1L,
    )
    private fun stateWithSig(sigs: Set<String>) = GradesSyncState(
        enabled = true, intervalHours = 6, lastSyncAt = 0L, lastSeenSignature = sigs,
    )
    private fun mockPrefs(sigs: Set<String>) = mockk<GradesSyncPrefs> {
        coEvery { snapshot() } returns stateWithSig(sigs)
    }

    @Test fun `first run with empty lastSeen returns isFirstRun true and empty newEntries`() = runTest {
        val prefs = mockPrefs(emptySet())
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(entry("t", "A", "正常", "90")))
        assertEquals(true, r.isFirstRun)
        assertTrue(r.newEntries.isEmpty())
        assertEquals(setOf("t|A|正常|90"), r.fullSignature)
    }

    @Test fun `no new entries when current signatures all known`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|90"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(entry("t", "A", "正常", "90")))
        assertEquals(false, r.isFirstRun)
        assertTrue(r.newEntries.isEmpty())
    }

    @Test fun `new course produces new entry`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|90"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(
            entry("t", "A", "正常", "90"),
            entry("t", "B", "正常", "85"),
        ))
        assertEquals(1, r.newEntries.size)
        assertEquals("B", r.newEntries.single().courseCode)
    }

    @Test fun `score change on same course-attempt counts as new`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|80"))   // 旧:80
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(entry("t", "A", "正常", "92")))  // 新:92
        assertEquals(1, r.newEntries.size)
        assertEquals("92", r.newEntries.single().score)
    }

    @Test fun `retake on same course produces new entry`() = runTest {
        val prefs = mockPrefs(setOf("t|A|正常|55"))
        val r = DetectNewGradesUseCase(prefs).invoke(listOf(
            entry("t", "A", "正常", "55"),
            entry("t", "A", "重修", "72"),
        ))
        assertEquals(1, r.newEntries.size)
        assertEquals("重修", r.newEntries.single().attemptType)
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*DetectNewGradesUseCaseTest*"`
Expected: FAIL(DetectNewGradesUseCase 未定义)

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import javax.inject.Inject

/** 当前 entries 与上次签名集求差,产出"本次新增"条目。
 *  签名:`"$termCode|$courseCode|$attemptType|$score"`。
 *  分数变更/重修出分都会产生新签名 → 触发通知。 */
class DetectNewGradesUseCase @Inject constructor(
    private val prefs: GradesSyncPrefs,
) {
    suspend fun invoke(currentEntries: List<GradeEntryEntity>): DiffResult {
        val currentSig = currentEntries.map(::signatureOf).toSet()
        val lastSig = prefs.snapshot().lastSeenSignature
        val newSigs = currentSig - lastSig
        val newEntries = currentEntries.filter { signatureOf(it) in newSigs }
        return DiffResult(
            newEntries = newEntries,
            fullSignature = currentSig,
            isFirstRun = lastSig.isEmpty(),
        )
    }
    companion object {
        fun signatureOf(e: GradeEntryEntity): String =
            "${e.termCode}|${e.courseCode}|${e.attemptType}|${e.score}"
    }
}

data class DiffResult(
    val newEntries: List<GradeEntryEntity>,
    val fullSignature: Set<String>,
    val isFirstRun: Boolean,
)
```

- [ ] **Step 4: 运行 → PASS**(5 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*DetectNewGradesUseCaseTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitgrades/DetectNewGradesUseCase.kt \
        app/src/test/java/com/example/personal_studio/domain/bitgrades/DetectNewGradesUseCaseTest.kt
git commit -m "p6(m4): DetectNewGradesUseCase 签名 diff + 单测"
```

---

### Task 3: `BackgroundSyncResult` + `SyncGradesUseCase.syncForBackground`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/bitgrades/model/BackgroundSyncResult.kt`
- Modify: `app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCaseTest.kt`(已存在,追加用例)

- [ ] **Step 1: 写 `BackgroundSyncResult` sealed type**

```kotlin
package com.example.personal_studio.domain.bitgrades.model

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity

/** 后台同步分支结果。Worker 据此决定:Ok→落库通知, Stop→停轮通知, Transient→retry。 */
sealed interface BackgroundSyncResult {
    /** 拉取并解析成功;entries 可能为空(无成绩也算 Ok)。 */
    data class Ok(val entries: List<GradeEntryEntity>) : BackgroundSyncResult

    /** 需用户介入(WrongCreds/Captcha/Locked/NeedReview/ParseFail) → cancel + notify。 */
    data class Stop(val reason: GradesSyncError) : BackgroundSyncResult

    /** 瞬时错(NetworkFail / Unexpected) → 让 Worker 走 Result.retry()。 */
    object Transient : BackgroundSyncResult
}
```

(`GradesSyncError` 已存在于 `domain/bitgrades/model/SyncGradesModels.kt`,直接 import 即可。)

- [ ] **Step 2: 写失败测试**(追加到 `SyncGradesUseCaseTest`)

```kotlin
    // —— syncForBackground —— //

    @Test fun `syncForBackground wrong password returns Stop with WrongCredentials`() = runTest {
        val api = mockk<BitApiClient>(relaxed = true)
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.WrongCredentials), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))
        val r = useCase.syncForBackground(req())
        assertTrue(r is com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop)
        assertTrue((r as com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop).reason is GradesSyncError.WrongCredentials)
    }

    @Test fun `syncForBackground happy path returns Ok with entries`() = runTest {
        val cas = mockk<BitCasService>(relaxed = true)
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns scoreHtml(
                "<tr><td>2024-2025-2</td><td>高数</td><td>5.0</td><td>92</td><td>4.0</td></tr>",
            )
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns cas
            coEvery { this@mockk.jwms } returns jwms
        }
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))
        val r = useCase.syncForBackground(req())
        assertTrue(r is com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Ok)
        assertEquals(1, (r as com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Ok).entries.size)
    }

    @Test fun `syncForBackground review-gated returns Stop NeedReview`() = runTest {
        val jwms = mockk<BitJwmsService> {
            coEvery { getScoreListHtml() } returns html("<html><body>请先完成评教</body></html>")
        }
        val api = mockk<BitApiClient>(relaxed = true) {
            coEvery { this@mockk.cas } returns mockk(relaxed = true)
            coEvery { this@mockk.jwms } returns jwms
        }
        val useCase = SyncGradesUseCase(api, ssoMock(CasLoginDto.Success), JsxsdGradeParser(), JsxsdDetailParser(), mockk(relaxed = true))
        val r = useCase.syncForBackground(req())
        assertTrue(r is com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop)
        assertTrue((r as com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop).reason is GradesSyncError.NeedReview)
    }
```

- [ ] **Step 3: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGradesUseCaseTest.syncForBackground*"`
Expected: FAIL(syncForBackground 未定义)

- [ ] **Step 4: 在 `SyncGradesUseCase` 加 `syncForBackground` 方法**

直接 append 到现有类(`sync()` Flow 不动)。注意:**`apiClient.open/close` 由 Worker 管,本方法不动 session 生命周期**。

```kotlin
    /** 后台静默同步。假设 apiClient 已 open;不发 Flow,直接返回结果。
     *  不并发拉 cjfx 详情(Worker 拿到 newEntries 后只对新增条目增量拉)。 */
    suspend fun syncForBackground(req: GradesSyncRequest): com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult {
        val login = ssoLogin.invoke(apiClient, req.username, req.password)
        login.toGradesError()?.let { return com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop(it) }
        return try {
            apiClient.cas.activateService(JWMS_SERVICE)
            val resp = apiClient.jwms.getScoreListHtml()
            val html = (resp.body() ?: resp.errorBody())?.string().orEmpty()
            if (parser.isReviewGated(html)) {
                return com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Stop(GradesSyncError.NeedReview)
            }
            val now = System.currentTimeMillis()
            val entries = parser.parse(html, now)
            com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Ok(entries)
        } catch (io: java.io.IOException) {
            com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Transient
        } catch (e: Throwable) {
            com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult.Transient
        }
    }
```

- [ ] **Step 5: 运行 → PASS**(3 个新用例 + 既有用例不被破坏)

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGradesUseCaseTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitgrades/model/BackgroundSyncResult.kt \
        app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt \
        app/src/test/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCaseTest.kt
git commit -m "p6(m4): SyncGradesUseCase.syncForBackground 静默版 + BackgroundSyncResult"
```

---

## Phase B · 通知 + 调度

### Task 4: `NotificationChannels.GRADES_ID` + `GradesNotifier`

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/core/notification/NotificationChannels.kt`
- Create: `app/src/main/java/com/example/personal_studio/core/notification/GradesNotifier.kt`

- [ ] **Step 1: 加 GRADES_ID 渠道**

修改 `NotificationChannels.kt`,在 OVERDUE 渠道之后追加 grades 渠道:

```kotlin
    const val GRADES_ID = "grade_updates"

    // —— ensureCreated() 内追加 ——
    nm.createNotificationChannel(
        NotificationChannel(GRADES_ID, "成绩更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "后台查到新成绩 / 自动查询状态变更"
        }
    )
```

完整改后的 `ensureCreated`:

```kotlin
    fun ensureCreated(context: Context) {
        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS_ID, "提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课程 / DDL / 自定义事件的提前提醒"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(OVERDUE_ID, "已过期", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "DDL 已过期但未标记完成"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(GRADES_ID, "成绩更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "后台查到新成绩 / 自动查询状态变更"
            }
        )
    }
```

- [ ] **Step 2: 写 `GradesNotifier`**

```kotlin
package com.example.personal_studio.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.personal_studio.MainActivity
import com.example.personal_studio.R
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradesNotifier @Inject constructor() {

    /** 发"N 门新成绩"聚合通知;同 id 覆盖,不堆叠多条。 */
    fun notifyNewGrades(context: Context, newEntries: List<GradeEntryEntity>) {
        if (newEntries.isEmpty()) return
        NotificationChannels.ensureCreated(context)
        val title = "${newEntries.size} 门新成绩"
        val head = newEntries.take(5).joinToString("\n") { "• ${it.courseName} ${it.score}" }
        val more = if (newEntries.size > 5) "\n…还有 ${newEntries.size - 5} 门" else ""
        post(
            context = context,
            notificationId = NID_NEW_GRADES,
            title = title,
            shortText = "${newEntries.first().courseName} ${newEntries.first().score}" +
                if (newEntries.size > 1) " 等" else "",
            bigText = head + more,
            deeplink = "personalstudio://grades",
        )
    }

    /** 后台自动查询被停下的提示;同 id 覆盖。 */
    fun notifyStop(context: Context, reason: GradesSyncError) {
        NotificationChannels.ensureCreated(context)
        val text = when (reason) {
            GradesSyncError.WrongCredentials -> "密码错误,凭据已清 — 请打开 App 重新登录"
            GradesSyncError.AccountLocked    -> "账号已锁定,请稍后或修改密码后再启用"
            GradesSyncError.CaptchaRequired  -> "教务系统要求验证码,请到网页端手动登录一次后重启"
            GradesSyncError.NeedReview       -> "教务提示未完成评教,请先评教后再启用"
            is GradesSyncError.ParseFail     -> "教务接口结构可能变化,请等 App 更新"
            else                             -> "未知错误,请打开 App 查看"
        }
        post(
            context = context,
            notificationId = NID_STOP,
            title = "成绩自动查询已停止",
            shortText = text,
            bigText = text,
            deeplink = "personalstudio://settings/grades-poll",
        )
    }

    private fun post(
        context: Context, notificationId: Int,
        title: String, shortText: String, bigText: String, deeplink: String,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deeplink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, NotificationChannels.GRADES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, n) }
    }

    companion object {
        private const val NID_NEW_GRADES = 5_000_001
        private const val NID_STOP = 5_000_002
    }
}
```

- [ ] **Step 3: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/notification/
git commit -m "p6(m4): GRADES_ID 渠道 + GradesNotifier(聚合新成绩 / 停轮提示)"
```

---

### Task 5: `GradesPollScheduler`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesPollScheduler.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/bitgrades/GradesPollSchedulerTest.kt`

- [ ] **Step 1: 写失败测试**(只测纯函数 `buildPeriodicRequest`,WorkManager 行为本身不测)

```kotlin
package com.example.personal_studio.feature.bitgrades

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class GradesPollSchedulerTest {
    @Test fun `buildPeriodicRequest sets interval network constraint and exponential backoff`() {
        val req = GradesPollScheduler.buildPeriodicRequest(intervalHours = 6)
        // PeriodicWorkRequest internals exposed via workSpec
        assertEquals(TimeUnit.HOURS.toMillis(6), req.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, req.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, req.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(30), req.workSpec.backoffDelayDuration)
    }

    @Test fun `different intervals produce different durations`() {
        listOf(3, 6, 12).forEach { h ->
            assertEquals(TimeUnit.HOURS.toMillis(h.toLong()), GradesPollScheduler.buildPeriodicRequest(h).workSpec.intervalDuration)
        }
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesPollSchedulerTest*"`
Expected: FAIL(GradesPollScheduler 未定义)

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.feature.bitgrades

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personal_studio.core.workers.GradePollWorker
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager 包装。enqueue/cancel + 从 prefs 重排(供 Boot 用)。
 *  `buildPeriodicRequest` 拆为伴生函数以便 JVM 单测无须起 WorkManager。 */
@Singleton
class GradesPollScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GradesSyncPrefs,
) {
    fun enqueue(intervalHours: Int) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPeriodicRequest(intervalHours),
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 从 prefs 快照判断:enabled → enqueue 当前间隔;disabled → cancel。
     *  Boot 完成时调用一次即可。 */
    suspend fun rescheduleFromPrefs() {
        val s = prefs.snapshot()
        if (s.enabled) enqueue(s.intervalHours) else cancel()
    }

    companion object {
        const val WORK_NAME = "grades-poll"

        fun buildPeriodicRequest(intervalHours: Int): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return PeriodicWorkRequestBuilder<GradePollWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
                .build()
        }
    }
}
```

(`GradePollWorker` 引用 — Task 6 创建;此 Task 单独编不过,但测试只用伴生函数,需要在 Task 6 后再跑完整编译。Step 4 跑测试可能因为 `GradePollWorker::class` 引用未解析失败,见下。)

- [ ] **Step 4: 暂存,**完整编译跑 Task 6 完成后做** — 现在仅写好代码 + commit

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `Unresolved reference: GradePollWorker`(预期,Task 6 解决)

- [ ] **Step 5: Commit(代码已写好,等 Task 6 编译)**

```bash
git add app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesPollScheduler.kt \
        app/src/test/java/com/example/personal_studio/feature/bitgrades/GradesPollSchedulerTest.kt
git commit -m "p6(m4): GradesPollScheduler — WorkManager enqueue/cancel + 重排;单测 builder 配置"
```

---

## Phase C · Worker

### Task 6: `GradePollWorker`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/core/workers/GradePollWorker.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/workers/GradePollWorkerTest.kt`

- [ ] **Step 1: 写实现**(先写代码以让 Task 5 也编译,然后回头写测试)

```kotlin
package com.example.personal_studio.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personal_studio.core.notification.GradesNotifier
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.DetectNewGradesUseCase
import com.example.personal_studio.domain.bitgrades.JsxsdDetailParser
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@HiltWorker
class GradePollWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val pollPrefs: GradesSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val sync: SyncGradesUseCase,
    private val detector: DetectNewGradesUseCase,
    private val detailParser: JsxsdDetailParser,
    private val apiClient: BitApiClient,
    private val gradesDao: GradesDao,
    private val notifier: GradesNotifier,
    private val scheduler: GradesPollScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!pollPrefs.snapshot().enabled) return Result.success()
        val creds = credPrefs.observeAll().value ?: run {
            pollPrefs.setEnabled(false); return Result.success()
        }
        return try {
            apiClient.open(NetworkMode.LOCAL)
            val result = sync.syncForBackground(
                GradesSyncRequest(creds.username, creds.password, NetworkMode.LOCAL, rememberPwd = true),
            )
            handle(result)
        } catch (e: Throwable) {
            Result.retry()
        } finally {
            apiClient.close()
        }
    }

    private suspend fun handle(result: BackgroundSyncResult): Result = when (result) {
        is BackgroundSyncResult.Stop -> {
            if (result.reason is GradesSyncError.WrongCredentials
                || result.reason is GradesSyncError.AccountLocked) {
                credPrefs.clear()
            }
            pollPrefs.setEnabled(false)
            scheduler.cancel()
            notifier.notifyStop(appContext, result.reason)
            Result.success()
        }
        is BackgroundSyncResult.Transient -> Result.retry()
        is BackgroundSyncResult.Ok -> {
            val diff = detector.invoke(result.entries)
            if (!diff.isFirstRun && diff.newEntries.isNotEmpty()) {
                val enriched = enrichDetails(diff.newEntries)
                gradesDao.upsertAll(enriched)
                notifier.notifyNewGrades(appContext, enriched)
            }
            pollPrefs.setLastSeenSignature(diff.fullSignature)
            pollPrefs.setLastSyncAt(System.currentTimeMillis())
            Result.success()
        }
    }

    /** 仅对本次新增的条目并发拉 cjfx 详情(智能增量)。失败保留原条目不阻断。 */
    private suspend fun enrichDetails(entries: List<GradeEntryEntity>): List<GradeEntryEntity> =
        coroutineScope {
            entries.map { e -> async { enrichOne(e) } }.awaitAll()
        }

    private suspend fun enrichOne(e: GradeEntryEntity): GradeEntryEntity {
        val path = e.detailPath ?: return e
        val info = runCatching {
            val r = apiClient.jwms.getCourseDetailHtml(path)
            if (r.isSuccessful) detailParser.parse((r.body() ?: r.errorBody())?.string().orEmpty())
            else null
        }.getOrNull() ?: return e
        return e.copy(
            courseAvg = info.courseAvg,
            courseMaxScore = info.courseMaxScore,
            courseStudyCount = info.courseStudyCount,
            classRankText = info.classRankText,
            majorRankText = info.majorRankText,
        )
    }
}
```

- [ ] **Step 2: 编译以确认 Task 5 + Task 6 联动通过**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 写 Worker 单测**(4 主路径,mockk 全栈)

```kotlin
package com.example.personal_studio.core.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.example.personal_studio.core.notification.GradesNotifier
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.DetectNewGradesUseCase
import com.example.personal_studio.domain.bitgrades.DiffResult
import com.example.personal_studio.domain.bitgrades.JsxsdDetailParser
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.BackgroundSyncResult
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradePollWorkerTest {

    private fun entry(code: String, score: String) = GradeEntryEntity(
        termCode = "t", termName = "t", courseName = code, courseCode = code,
        credit = 3.0, score = score, gradePoint = 4.0, gradeLetter = null, category = null,
        attemptType = "正常", isPass = true, fetchedAt = 1L,
    )
    private fun stateEnabled(sigs: Set<String> = emptySet()) = GradesSyncState(
        enabled = true, intervalHours = 6, lastSyncAt = null, lastSeenSignature = sigs,
    )
    private fun stateDisabled() = GradesSyncState(
        enabled = false, intervalHours = 6, lastSyncAt = null, lastSeenSignature = emptySet(),
    )
    private fun savedCreds() = SavedCredentials(username = "u", password = "p", lastMode = NetworkMode.LOCAL)

    private fun newWorker(
        pollPrefs: GradesSyncPrefs,
        credPrefs: ImportCredentialPrefs,
        sync: SyncGradesUseCase,
        detector: DetectNewGradesUseCase = mockk(relaxed = true),
        api: BitApiClient = mockk(relaxed = true),
        dao: GradesDao = mockk(relaxed = true),
        notifier: GradesNotifier = mockk(relaxed = true),
        scheduler: GradesPollScheduler = mockk(relaxed = true),
    ): GradePollWorker = GradePollWorker(
        appContext = mockk<Context>(relaxed = true),
        params = mockk<WorkerParameters>(relaxed = true),
        pollPrefs = pollPrefs, credPrefs = credPrefs, sync = sync,
        detector = detector, detailParser = JsxsdDetailParser(),
        apiClient = api, gradesDao = dao, notifier = notifier, scheduler = scheduler,
    )

    @Test fun `pollEnabled false returns success and does not sync`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs> { coEvery { snapshot() } returns stateDisabled() }
        val sync = mockk<SyncGradesUseCase>(relaxed = true)
        val r = newWorker(pollPrefs, mockk(relaxed = true), sync).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify(exactly = 0) { sync.syncForBackground(any()) }
    }

    @Test fun `missing creds disables and returns success`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns stateEnabled() }
        val creds = mockk<ImportCredentialPrefs> { every { observeAll() } returns MutableStateFlow(null) }
        val sync = mockk<SyncGradesUseCase>(relaxed = true)
        val r = newWorker(pollPrefs, creds, sync).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { pollPrefs.setEnabled(false) }
        coVerify(exactly = 0) { sync.syncForBackground(any()) }
    }

    @Test fun `wrong credentials triggers strict stop`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns stateEnabled() }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns BackgroundSyncResult.Stop(GradesSyncError.WrongCredentials)
        }
        val notifier = mockk<GradesNotifier>(relaxed = true)
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val r = newWorker(pollPrefs, creds, sync, notifier = notifier, scheduler = scheduler).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { creds.clear() }
        coVerify { pollPrefs.setEnabled(false) }
        coVerify { scheduler.cancel() }
        coVerify { notifier.notifyStop(any(), GradesSyncError.WrongCredentials) }
    }

    @Test fun `transient error returns retry without disabling`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns stateEnabled() }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val sync = mockk<SyncGradesUseCase> { coEvery { syncForBackground(any()) } returns BackgroundSyncResult.Transient }
        val r = newWorker(pollPrefs, creds, sync).doWork()
        assertEquals(ListenableWorker.Result.retry(), r)
        coVerify(exactly = 0) { pollPrefs.setEnabled(false) }
    }

    @Test fun `first run with no lastSeen builds baseline silently`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns stateEnabled() }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val entries = listOf(entry("A", "90"), entry("B", "85"))
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns BackgroundSyncResult.Ok(entries)
        }
        val detector = mockk<DetectNewGradesUseCase> {
            coEvery { invoke(entries) } returns DiffResult(
                newEntries = emptyList(),
                fullSignature = setOf("t|A|正常|90", "t|B|正常|85"),
                isFirstRun = true,
            )
        }
        val notifier = mockk<GradesNotifier>(relaxed = true)
        val sigSlot = slot<Set<String>>()
        coEvery { pollPrefs.setLastSeenSignature(capture(sigSlot)) } returns Unit
        val r = newWorker(pollPrefs, creds, sync, detector, notifier = notifier).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(setOf("t|A|正常|90", "t|B|正常|85"), sigSlot.captured)
        coVerify(exactly = 0) { notifier.notifyNewGrades(any(), any()) }
    }

    @Test fun `subsequent run with new entries upserts and notifies`() = runTest {
        val pollPrefs = mockk<GradesSyncPrefs>(relaxed = true) { coEvery { snapshot() } returns stateEnabled() }
        val creds = mockk<ImportCredentialPrefs>(relaxed = true) {
            every { observeAll() } returns MutableStateFlow(savedCreds())
        }
        val newOne = entry("C", "92")  // detailPath null → enrich 直接原样
        val sync = mockk<SyncGradesUseCase> {
            coEvery { syncForBackground(any()) } returns BackgroundSyncResult.Ok(listOf(entry("A", "90"), newOne))
        }
        val detector = mockk<DetectNewGradesUseCase> {
            coEvery { invoke(any()) } returns DiffResult(
                newEntries = listOf(newOne),
                fullSignature = setOf("t|A|正常|90", "t|C|正常|92"),
                isFirstRun = false,
            )
        }
        val dao = mockk<GradesDao>(relaxed = true)
        val notifier = mockk<GradesNotifier>(relaxed = true)
        val r = newWorker(pollPrefs, creds, sync, detector, dao = dao, notifier = notifier).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        coVerify { dao.upsertAll(listOf(newOne)) }
        coVerify { notifier.notifyNewGrades(any(), listOf(newOne)) }
    }
}
```

- [ ] **Step 4: 运行 → PASS**(6 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*GradePollWorkerTest*"`
Expected: PASS

- [ ] **Step 5: 也跑 Task 5 的 scheduler 单测**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesPollSchedulerTest*"`
Expected: PASS(2 用例)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/workers/GradePollWorker.kt \
        app/src/test/java/com/example/personal_studio/core/workers/GradePollWorkerTest.kt
git commit -m "p6(m4): GradePollWorker(@HiltWorker)主流程 + 6 路径单测"
```

---

### Task 7: BootCompletedReceiver 顺带重排

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/core/workers/BootCompletedReceiver.kt`

- [ ] **Step 1: 改 BootReceiver,注入 GradesPollScheduler,Boot 后 reschedule**

```kotlin
package com.example.personal_studio.core.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var gradesPollScheduler: GradesPollScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // 1) Timeline 提醒重排(既有)
        val req = OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RescheduleRemindersWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            req,
        )

        // 2) 成绩轮询从 prefs 重排(goAsync 让出 receiver 线程)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                gradesPollScheduler.rescheduleFromPrefs()
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/workers/BootCompletedReceiver.kt
git commit -m "p6(m4): BootCompletedReceiver 顺带从 prefs 重排成绩轮询任务"
```

---

## Phase D · UI

### Task 8: `GradesPollSettingsViewModel`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/settings/vm/GradesPollSettingsViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/settings/vm/GradesPollSettingsViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.feature.settings.vm

import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.local.datastore.SavedCredentials
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

class GradesPollSettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun creds(present: Boolean): ImportCredentialPrefs = mockk(relaxed = true) {
        every { observeAll() } returns MutableStateFlow(
            if (present) SavedCredentials("u", "p", NetworkMode.LOCAL) else null
        )
    }

    @Test fun `disabled when no credentials saved`() = runTest {
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns flowOf(GradesSyncState(false, 6, null, emptySet()))
        }
        val vm = GradesPollSettingsViewModel(poll, creds(false), mockk(relaxed = true))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.credsSaved)
        assertEquals(false, vm.uiState.value.enabled)
        job.cancel()
    }

    @Test fun `enabling persists and schedules`() = runTest {
        val pollState = MutableStateFlow(GradesSyncState(false, 6, null, emptySet()))
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns pollState
            coEvery { setEnabled(true) } answers {
                pollState.value = pollState.value.copy(enabled = true); Unit
            }
        }
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val vm = GradesPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEnableToggle(true)
        advanceUntilIdle()
        coVerify { poll.setEnabled(true) }
        verify { scheduler.enqueue(6) }
        job.cancel()
    }

    @Test fun `changing interval reschedules when enabled`() = runTest {
        val pollState = MutableStateFlow(GradesSyncState(true, 6, null, emptySet()))
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns pollState
            coEvery { setIntervalHours(12) } answers {
                pollState.value = pollState.value.copy(intervalHours = 12); Unit
            }
        }
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val vm = GradesPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onIntervalSelect(12)
        advanceUntilIdle()
        coVerify { poll.setIntervalHours(12) }
        verify { scheduler.enqueue(12) }
        job.cancel()
    }

    @Test fun `disabling cancels schedule`() = runTest {
        val pollState = MutableStateFlow(GradesSyncState(true, 6, null, emptySet()))
        val poll = mockk<GradesSyncPrefs>(relaxed = true) {
            every { observe } returns pollState
            coEvery { setEnabled(false) } answers {
                pollState.value = pollState.value.copy(enabled = false); Unit
            }
        }
        val scheduler = mockk<GradesPollScheduler>(relaxed = true)
        val vm = GradesPollSettingsViewModel(poll, creds(true), scheduler)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onEnableToggle(false)
        advanceUntilIdle()
        coVerify { poll.setEnabled(false) }
        verify { scheduler.cancel() }
        job.cancel()
    }
}
```

- [ ] **Step 2: 运行 → FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesPollSettingsViewModelTest*"`
Expected: FAIL(未定义)

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.GradesSyncPrefs
import com.example.personal_studio.data.local.datastore.GradesSyncState
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.feature.bitgrades.GradesPollScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesPollUiState(
    val credsSaved: Boolean = false,
    val enabled: Boolean = false,
    val intervalHours: Int = 6,
    val lastSyncAt: Long? = null,
)

@HiltViewModel
class GradesPollSettingsViewModel @Inject constructor(
    private val pollPrefs: GradesSyncPrefs,
    private val credPrefs: ImportCredentialPrefs,
    private val scheduler: GradesPollScheduler,
) : ViewModel() {

    val uiState: StateFlow<GradesPollUiState> = combine(
        pollPrefs.observe, credPrefs.observeAll(),
    ) { poll: GradesSyncState, creds -> GradesPollUiState(
        credsSaved = creds != null,
        enabled = poll.enabled,
        intervalHours = poll.intervalHours,
        lastSyncAt = poll.lastSyncAt,
    ) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GradesPollUiState())

    fun onEnableToggle(on: Boolean) = viewModelScope.launch {
        pollPrefs.setEnabled(on)
        if (on) scheduler.enqueue(pollPrefs.snapshot().intervalHours) else scheduler.cancel()
    }

    fun onIntervalSelect(hours: Int) = viewModelScope.launch {
        pollPrefs.setIntervalHours(hours)
        if (pollPrefs.snapshot().enabled) scheduler.enqueue(hours)
    }
}
```

- [ ] **Step 4: 运行 → PASS**(4 用例)

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesPollSettingsViewModelTest*"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/vm/GradesPollSettingsViewModel.kt \
        app/src/test/java/com/example/personal_studio/feature/settings/vm/GradesPollSettingsViewModelTest.kt
git commit -m "p6(m4): GradesPollSettingsViewModel + 单测(开关/间隔/scheduler 联动)"
```

---

### Task 9: `GradesPollSettingsScreen`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/settings/ui/GradesPollSettingsScreen.kt`

- [ ] **Step 1: 写实现**(纯 UI,无单测,编译+真机目检)

```kotlin
package com.example.personal_studio.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.settings.vm.GradesPollSettingsViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesPollSettingsScreen(onBack: () -> Unit, vm: GradesPollSettingsViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Void).systemBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ grades-poll", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))

        // 总开关
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("后台自动查分", color = Foam)
                Text(
                    if (st.credsSaved) "每 ${st.intervalHours} 小时静默登录一次教务系统"
                    else "请先在「从教务系统查询成绩」时勾选'记住密码'",
                    color = FoamDim, style = MaterialTheme.typography.labelMedium,
                )
            }
            Switch(checked = st.enabled, onCheckedChange = vm::onEnableToggle, enabled = st.credsSaved)
        }
        Spacer(Modifier.height(20.dp))

        // 间隔 3 档
        Text("查询间隔", color = FoamMute, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 6, 12).forEach { h ->
                FilterChip(
                    selected = st.intervalHours == h,
                    onClick = { vm.onIntervalSelect(h) },
                    enabled = st.credsSaved,
                    label = { Text("${h}h") },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // 上次同步
        Text(
            "上次同步: " + (st.lastSyncAt?.let { fmt(it) } ?: "—"),
            color = FoamMute, style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(20.dp))

        // 警告 / 说明
        Text(
            """⚠ 后台将以你保存的凭据每 N 小时静默登录教务,
            |  比对发现新成绩后通知。失败(密码错/锁号/验证码)
            |  会立即停轮,需手动重启。""".trimMargin(),
            color = Amber, style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun fmt(t: Long): String {
    val diff = System.currentTimeMillis() - t
    val min = diff / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "${min}分钟前"
        min < 1440 -> "${min / 60}小时前"
        min < 10_080 -> "${min / 1440}天前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/settings/ui/GradesPollSettingsScreen.kt
git commit -m "p6(m4): GradesPollSettingsScreen — 总开关 + 3 档间隔 + 上次同步 + 说明"
```

---

### Task 10: 导航 + Settings 入口

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
- Modify: `app/src/main/java/com/example/personal_studio/feature/settings/ui/SettingsScreen.kt`

- [ ] **Step 1: NavRoutes 加常量**

在 `NavRoutes.kt` 的 settings 子页区追加:

```kotlin
    const val SETTINGS_GRADES_POLL = "settings/grades-poll"
```

- [ ] **Step 2: AppNavHost 注册**

在 `composable(NavRoutes.SETTINGS_NOTIF){...}` 之后追加:

```kotlin
        composable(NavRoutes.SETTINGS_GRADES_POLL) {
            com.example.personal_studio.feature.settings.ui.GradesPollSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
```

注意 deeplink:`GradesNotifier.notifyStop` 用 `personalstudio://settings/grades-poll`。AppNavHost 已有处理 deeplink 的机制(见 Task 4 通知 PendingIntent)。系统会用 Intent data 匹配 NavController route。若现有 deeplink 机制需要显式声明,在该 composable 上加:

```kotlin
        composable(
            NavRoutes.SETTINGS_GRADES_POLL,
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "personalstudio://settings/grades-poll" }),
        ) {
            com.example.personal_studio.feature.settings.ui.GradesPollSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
```

同理 `notifyNewGrades` 的 `personalstudio://grades` deeplink 在 `composable(NavRoutes.GRADES)` 上也加:

```kotlin
        composable(
            NavRoutes.GRADES,
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "personalstudio://grades" }),
        ) { /* 原内容不动 */ }
```

- [ ] **Step 3: SettingsScreen 加入口行**

在 IMPORT / GRADES 入口行之后追加(`NavigableRowWithSubtitle`):

```kotlin
            NavigableRowWithSubtitle(
                key = "GRADES_POLL",
                value = "成绩更新提醒 →",
                subtitle = "后台查分新成绩并通知",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.SETTINGS_GRADES_POLL) },
            )
```

- [ ] **Step 4: 编译 + 全量单测**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL;所有既有测试 + 新 M4 测试通过。

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "p6(m4): NavRoutes/AppNavHost/Settings 入口 + 通知 deeplink"
```

---

## Phase E · 真机 DoD

### Task 11: 真机端到端验证

**Files:** 无代码,纯验证步骤。

- [ ] **Step 1: 装机并启用**

Run: `./gradlew :app:installDebug`

设备:进 App → Settings → 「成绩更新提醒 →」→
1. 未存凭据时开关灰显,显示"请先在'从教务系统查询成绩'时勾选'记住密码'"。
2. 主线同步一次成绩并勾选"记住密码" → 回到此页 → 开关可启用 → 切换 3h/6h/12h chip 灵敏切换。
3. 启用后等几秒;`adb shell dumpsys jobscheduler | grep grades-poll` 应有调度记录。

- [ ] **Step 2: 立刻触发一次 Worker(不等小时级周期)**

Run: `adb shell cmd jobscheduler run -f com.example.personal_studio <jobId>`
(jobId 见 dumpsys 输出)

或者:用 Android Studio 「App Inspection → Background Task Inspector」 找到 `grades-poll` PeriodicWork → 右键 Run Now。

预期:Worker 跑完一次,**首次基线**写入 prefs,无通知。

- [ ] **Step 3: 模拟新成绩 → 触发通知**

最简法:用 SQLite/adb 直接修改本地 grade_entries 的某行 score(例如 88 → 89),再让 Worker run now。Worker 拉到的真实表里 88 → 旧签名;88→未变;但本地落库的是 88(被新值改过)→ diff 出多门"新成绩"。复杂。

更靠谱:**手动清掉 lastSeenSignature**(让 detector 误以为首次)。第一次跑:基线。第二次:再清掉 → 跑一次 → 所有课程都被算"新增"→ 触发通知(用作测试)。
```
adb shell run-as com.example.personal_studio sh -c "rm -rf /data/data/com.example.personal_studio/files/datastore"
adb shell cmd jobscheduler run -f com.example.personal_studio <jobId>   # 1st: 仅基线
adb shell run-as com.example.personal_studio sh -c "rm -rf /data/data/com.example.personal_studio/files/datastore"
adb shell cmd jobscheduler run -f com.example.personal_studio <jobId>   # 2nd: 假装新成绩→通知
```
预期:通知栏出现"N 门新成绩",bigText 列出前 5 门;点通知跳成绩单页。

- [ ] **Step 4: 故意输错密码触发 Stop**

1. App → Settings → 取消"记住密码" 然后再保存一个错密码(或 adb 改 ImportCredentialPrefs 写错密码)。
2. Worker run now。
3. 预期:通知"成绩自动查询已停止 · 密码错误,凭据已清";Settings 屏幕开关自动关闭 + 凭据被清。

- [ ] **Step 5: 关闭开关 → 任务取消**

1. App → Settings → 关闭开关。
2. `adb shell dumpsys jobscheduler | grep grades-poll`:任务应已 cancelled / 不再在调度表。

- [ ] **Step 6: 真机 DoD 清单核对**

按 spec §14 全清单逐项 ✓。

- [ ] **Step 7: 全量单测 + 提交收尾**

```bash
./gradlew :app:testDebugUnitTest
# 应全绿
```

无需 commit(本任务无代码改动)。准备走 PR 流程。

---

## Self-Review

**Spec 覆盖**:Spec §1-§14 各节均有对应任务:
- §1 Scope: Task 1-10 覆盖所有 in-scope 项;out-of-scope 明确不做
- §2 Module Layout: Task 1-9 一一对应每个新建/修改文件
- §3 GradesSyncPrefs: Task 1
- §4 syncForBackground: Task 3
- §5 DetectNewGrades: Task 2
- §6 GradePollWorker: Task 6
- §7 GradesPollScheduler: Task 5
- §8 GradesNotifier: Task 4
- §9 Settings UI: Task 8 + 9
- §10 错误处理对照表: Task 6 的 6 个单测全覆盖(disabled/no-creds/wrong/transient/first-baseline/new-entries)
- §11 测试: 各任务 TDD
- §12 Phase Breakdown: 本计划即是
- §13 Risk Register: Task 6/11 中体现(严格停 + 间隔下限 3h + EncryptedSharedPreferences 复用)
- §14 DoD: Task 11

**Type 一致性**:
- `BackgroundSyncResult.Stop(reason: GradesSyncError)` — Task 3 定义,Task 6 使用 ✓
- `DiffResult(newEntries, fullSignature, isFirstRun)` — Task 2 定义,Task 6 使用 ✓
- `GradesSyncPrefs.snapshot(): GradesSyncState` — Task 1 定义,Task 2/6/8 使用 ✓
- `GradesSyncState(enabled, intervalHours, lastSyncAt, lastSeenSignature)` — Task 1 定义,Task 8 使用 ✓
- `GradesPollScheduler.{enqueue,cancel,rescheduleFromPrefs}` — Task 5 定义,Task 6/7/8 使用 ✓
- `GradesNotifier.{notifyNewGrades(ctx,List), notifyStop(ctx,reason)}` — Task 4 定义,Task 6 使用 ✓

**Placeholder 扫描**:无 TBD / TODO / "fill in" 等;每个 step 有完整代码或精确命令。

**依赖顺序**:Task 5 的代码引用 `GradePollWorker::class` 在 Task 6 之前 — 已在 Task 5 Step 4 说明"该 task 编译不过,完整编译留给 Task 6 后",并在 Task 6 Step 2 显式跑 `compileDebugKotlin`。
