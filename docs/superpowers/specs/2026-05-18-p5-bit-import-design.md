# P5 · BIT 教务系统课表导入 — Design Spec

**Status:** Approved (brainstorm completed 2026-05-18)
**Owner:** ggggh123
**References:** [BIT101-Android](https://github.com/BIT101-dev/BIT101-Android) (AGPL-3.0 — **studied for protocol shape only, no code copied**)

---

## 1 · Scope & Goals

Provide a one-tap "从教务系统导入课表" path that authenticates against BIT 统一身份认证 (CAS SSO) and pulls the current (or user-selected) semester's course schedule into the existing `timeline_items` table.

**In scope:**
- BIT CAS login (`login.bit.edu.cn`) with AES-encrypted password — supports both 校内 direct and 校外 WebVPN routing
- Pull schedule via the `wdkbby` (我的课表) app under `/jwapp/...`
- Map BIT's `ScheduleRow` payload → `TimelineItemEntity(sourceType = IMPORTED_PORTAL)`, expanding the SKZC week-bitmap into per-occurrence rows
- 4-screen wizard UX with terminal aesthetic
- Optional "记住密码" via `EncryptedSharedPreferences` (Android Keystore-backed)
- Semester anchor smart default: auto-backsolve from BIT's `getWeekAndDate()` when `SemesterPreferences.startDate` is unset; respect user override otherwise

**Explicitly out of scope:**
- Background auto-sync (no WorkManager — re-import is always user-triggered)
- Diff-based merge (we wipe + rewrite the `(semester × IMPORTED_PORTAL)` slice, see §5)
- Exam schedule / classroom availability / lexue (BB) integration — BIT101 has them; P5 stays course-only
- Schools other than BIT — the design hardcodes BIT endpoints; a future P6+ could abstract a `SchoolAdapter` interface
- Captcha image OCR — if BIT's CAS demands a captcha, we surface a "go to web login" prompt rather than implement OCR

---

## 2 · Architecture — Module Layout

```
core/util/
├─ AesCbcCrypto.kt           — clean-room AES-CBC + PKCS5 + random IV for CAS password
└─ SkzcExpander.kt           — "11011" → [1, 2, 4, 5]

data/network/bit/            — new sub-package; isolated from core /data/network
├─ BitApiClient.kt           — single OkHttpClient + Retrofit instance switched per session
├─ BitCookieJar.kt           — in-memory CookieJar; cleared on session end
├─ BitUrlsConfig.kt          — LOCAL_BASE / WEBVPN_BASE
├─ dto/{CasLoginDto, TermDto, ScheduleRowDto, WeekDateDto}.kt
└─ service/{BitCasService, BitJwappService}.kt   — Retrofit interfaces

data/repository/
└─ ImportRepository.kt       — orchestrates Login → fetch term → fetch schedule → map → persist

data/local/datastore/
└─ ImportCredentialPrefs.kt  — EncryptedSharedPreferences-backed {username, password, lastMode}

domain/import/
├─ model/
│  ├─ ImportCredentials.kt   — (username, password, rememberPwd, networkMode)
│  ├─ NetworkMode.kt         — LOCAL | WEBVPN
│  ├─ ImportRequest.kt       — credentials + optional override termCode
│  ├─ ImportResult.kt        — (successCount, replacedCount, termCode, semesterStartDate)
│  ├─ ImportStep.kt          — sealed; progress events flowing to UI
│  └─ ImportError.kt         — sealed; user-facing failure modes
├─ ImportCoursesUseCase.kt   — top-level flow<ImportStep> orchestrator
├─ SsoLoginUseCase.kt        — CAS init+post pair
├─ ResolveSemesterAnchorUseCase.kt
├─ MapBitCourseUseCase.kt
└─ ReplaceImportedCoursesUseCase.kt

feature/import/
├─ ImportNavGraph.kt         — internal NavHost across 4 wizard screens
├─ ImportViewModel.kt        — state machine driving the wizard
└─ ui/
   ├─ ImportCredentialsScreen.kt   — screen 1
   ├─ ImportTermPickerScreen.kt    — screen 2
   ├─ ImportProgressScreen.kt      — screen 3
   └─ ImportPreviewScreen.kt       — screen 4
```

**Why a dedicated `data/network/bit/` sub-package:** Future P6+ may add other school adapters; isolating BIT-specific protocol code from the shared core network module makes that extension cheap. P5 itself does **not** introduce the `SchoolAdapter` abstraction (YAGNI — only one school today).

---

## 3 · Data Model — Zero Schema Changes

P4 pre-allocated `sourceType` and `sourceExternalId` columns on `timeline_items`. P5 finally consumes them:

- `sourceType = IMPORTED_PORTAL` for every row written by the importer
- `sourceExternalId = KCH` (BIT 课程号) — used only as a stable external identifier; **not** used as a unique key for conflict resolution (see §5)
- `seriesId` is allocated fresh per import session (`maxSeriesId() + 1 + offset`) so imported series never collide with `MANUAL` series

**No `AppDatabase` version bump.** No migration logic. The DAO grows two new queries (§5).

**New DAO queries on `TimelineDao`:**

```kotlin
@Query("SELECT COUNT(*) FROM timeline_items WHERE sourceType = 'IMPORTED_PORTAL' AND startAt >= :startInclusive AND startAt < :endExclusive")
suspend fun countImportedInRange(startInclusive: Long, endExclusive: Long): Int

@Query("DELETE FROM timeline_items WHERE sourceType = 'IMPORTED_PORTAL' AND startAt >= :startInclusive AND startAt < :endExclusive")
suspend fun deleteImportedInRange(startInclusive: Long, endExclusive: Long): Int
```

---

## 4 · Network & Auth Layer

### 4.1 OkHttp setup (Hilt-provided)

```kotlin
@Provides @Singleton @Named("bit")
fun bitClient(cookieJar: BitCookieJar): OkHttpClient =
    OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, SECONDS)
        .readTimeout(30, SECONDS)
        .followRedirects(true)           // CAS issues a 302 on login success
        .apply { if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor()) }
        .build()
```

**Single OkHttpClient instance, shared cookie jar.** Retrofit is *not* injected; `BitApiClient.open(mode)` constructs it on session start with the chosen base URL, and `close()` drops it (also clearing the cookie jar).

### 4.2 URL switching

```kotlin
object BitUrlsConfig {
    const val LOCAL_BASE  = "https://login.bit.edu.cn"
    const val WEBVPN_BASE = "https://webvpn.bit.edu.cn"
}
```

Retrofit interfaces use absolute paths (`@GET("/cas/login")`, `@POST("/jwapp/...")`). The host comes from base URL.

**WebVPN path rewriting:** the spec assumes BIT's webvpn gateway transparently rewrites paths (host-only redirection). If real-device testing during `p5-polish` reveals path-prefix rewriting is required, a `WebVpnPathInterceptor` will be added as a minimal patch — this is a known risk recorded in §11.

### 4.3 Cookie jar — in-memory only

```kotlin
class BitCookieJar @Inject constructor() : CookieJar {
    private val store = mutableMapOf<String, List<Cookie>>()
    @Synchronized override fun saveFromResponse(...) { ... }
    @Synchronized override fun loadForRequest(...) { ... }
    fun clear() { store.clear() }
}
```

Cookies are dropped after every import. Re-imports re-authenticate via Keystore-saved password. Rationale: session cookies are a higher-value leak target than encrypted credentials.

### 4.4 AES-CBC password encryption (clean-room)

```kotlin
object AesCbcCrypto {
    fun encryptPassword(plain: String, salt: String, iv: ByteArray = randomIv()): String {
        val key = SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val plaintext = (randomBase64(64) + plain).toByteArray(Charsets.UTF_8)  // anti-replay prefix
        return Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP)
    }
    private fun randomIv(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private fun randomBase64(n: Int): String { /* SecureRandom + Base64 */ }
}
```

**Test strategy:** the `iv: ByteArray = randomIv()` default is overridable for deterministic unit tests. A fixture is captured once from a real BIT login response (salt + iv + plaintext → expected ciphertext) and asserted byte-for-byte.

### 4.5 CAS login flow

`SsoLoginUseCase.invoke(apiClient, username, password)`:

1. `GET /cas/login` → returns an HTML page containing `<input name="execution">` and a `<input name="croypto">` (the AES key salt).
2. Parse the HTML with regex (`execution = "(.+?)"` and `croypto = "(.+?)"`) — small parser, but cheaper than pulling in jsoup.
3. `AesCbcCrypto.encryptPassword(password, salt = croypto)`.
4. `POST /cas/login` form-encoded with `(username, password=cipherText, execution, croypto, captcha_payload="", type=UsernamePassword, _eventId=submit)`.
5. Success signal: response is HTTP 302 (followed automatically) with `Location` pointing to a `service`-URL containing a CAS `ticket=ST-...` parameter. Failure: HTTP 200 with `class="login-error"` div in the body.

**Captcha avoidance:** following BIT101's documented observation, never call `/cas/getCaptcha.htl`; without an active captcha image session, CAS does not require a captcha code. This works for typical low-failure-rate accounts. If a captcha *is* triggered (e.g., the user has been trying wrong passwords elsewhere), we detect via parsing the login error and surface `ImportError.CaptchaRequired`.

### 4.6 Credential storage (Keystore)

```kotlin
class ImportCredentialPrefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "bit_import_creds", MasterKey.Builder(ctx).build(),
        AES256_SIV, AES256_GCM,
    )
    var username: String?            // delegated
    var encryptedPassword: String?
    var lastNetworkMode: NetworkMode?
    fun clear()
}
```

**Lifecycle:**

| Event | Action |
|---|---|
| User checks "记住密码" + login succeeds | write `username` + `password` + `lastMode` |
| User unchecks + login succeeds | `clear()` (also clears any prior save) |
| User opens credentials screen | prefill from prefs if present; auto-check the box |
| Login fails with `WrongCredentials` or `AccountLocked` | `clear()` (saved credential is stale) + show error |

---

## 5 · Domain Layer

### 5.1 SKZC week-bitmap expansion

```kotlin
fun expandSkzc(skzc: String): List<Int> =
    skzc.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }
```

**Edge cases (covered by unit tests):**
- All zeros → empty list → caller skips this row
- Length < 1 or > 30 → throw `ParseFail`
- Non `0/1` character → throw `ParseFail`

### 5.2 `MapBitCourseUseCase`

Per BIT `ScheduleRowDto`, returns `List<TimelineItemEntity>` with one row per expanded week.

**Field mapping:**

| BIT field | Our field | Notes |
|---|---|---|
| `KCM` | `title` | Required; missing → ParseFail |
| `SKJS` | `instructor` | Comma-separated string preserved verbatim |
| `JASMC` | (combined into `location`) | Classroom name |
| `XXXQMC` | (combined into `location`) | Campus name — prefixed: `"良乡·信息楼301"`; if campus is null, just classroom |
| `XF` (Int) | `credits` | `.toFloat()`; null OK |
| `KCH` | `sourceExternalId` | BIT course code, stable across re-imports for the same course |
| `SKXQ` | `weekdayCode` | 1-7 |
| `KSJC` / `JSJC` | `periodIndex` / `periodEndIndex` | If JSJC missing, defaults to KSJC |
| `SKZC` | (expanded into multiple rows via `weekIndexInSemester`) | One row per `'1'` bit |
| `KCXZDM_DISPLAY` / `XS` / `KKDWDM_DISPLAY` | `notes` | Joined as `"必修 · 80学时 · 计算机学院"`; null if all empty |
| `YPSJDD` | (dropped) | Redundant with structured fields |

**`seriesId` allocation:** at the start of each import session, query `dao.maxSeriesId() + 1` as the base. Maintain an in-session `Map<KCH, seriesId>`; first time a KCH is seen, allocate `base + (size of map)`. This guarantees no collision with MANUAL series (which use existing or newly-allocated IDs from the same source).

**Epoch computation:** `weekIdx + weekday + periodStart/End` → `(LocalDate, LocalTime → epoch ms)` via P4's existing `SemesterPreferences.startDate` and `TimetablePreferences` period grid. Reuses the same conversion logic from `CreateCourseSeriesUseCase`.

### 5.3 `ResolveSemesterAnchorUseCase` (smart default C)

```kotlin
suspend fun resolve(weekAndDate: WeekDateDto): LocalDate {
    semesterPrefs.startDate.first()?.let { return it }       // user already set → respect
    // Backsolve: weekAndDate.data contains current week's days with their (weekday, date).
    // Take the earliest date, find that day's weekday from BIT's response, walk back to that
    // week's Monday, then subtract (currentWeekIdx - 1) weeks to reach week-1 Monday.
    val firstDay = weekAndDate.data.minByOrNull { it.date }!!
    val currentWeekMonday = LocalDate.parse(firstDay.date)
        .minusDays((firstDay.week - 1).toLong())
    val semesterMonday = currentWeekMonday.minusWeeks((weekAndDate.currentWeek - 1).toLong())
    semesterPrefs.setStartDate(semesterMonday)
    return semesterMonday
}
```

UI shows a Toast on auto-write: `"已根据教务系统设置学期开始日期：YYYY-MM-DD"`. No toast when respecting an existing value.

**⚠ Term-anchor coupling caveat.** `SemesterPreferences` holds a *single* start date — implicitly "the current semester's". `getWeekAndDate()` likewise always returns *current* week info, regardless of what `XNXQDM` the user later picks. So if the user selects a **non-current** term in Screen 2 (e.g., importing next semester ahead of time), the backsolved anchor will be wrong for that term, and the resulting course `startAt` timestamps will fall on the wrong calendar weeks. Mitigation: Screen 2 displays an inline warning when the user's selection differs from `getCurrentTerm()`:

> ⚠ 切换到非当前学期将使用现有学期开始日期来计算时间，可能不准确。建议先到 Settings → 学期 手动设置该学期开始日期。

The `[抓取课表 →]` button remains enabled (advanced users may have already set the correct date) but the warning is unmissable. Storing per-term anchors is deferred to a future iteration; for P5 MVP the single-anchor limit is documented and surfaced.

### 5.4 `ReplaceImportedCoursesUseCase` (conflict strategy B)

```kotlin
suspend fun replaceForSemester(
    semesterStart: LocalDate,
    zone: ZoneId,
    newItems: List<TimelineItemEntity>,
): Int {
    val totalWeeks = 25                                // covers any normal semester length
    val startEpoch = semesterStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val endEpoch = semesterStart.plusWeeks(totalWeeks.toLong())
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val deleted = dao.deleteImportedInRange(startEpoch, endEpoch)
    dao.insertAll(newItems)
    return deleted
}
```

**What stays untouched:**
- All `sourceType = MANUAL` rows
- Imported rows in other semesters (whose `startAt` falls outside the 25-week window)

**What gets wiped:**
- All `sourceType = IMPORTED_PORTAL` rows whose `startAt` falls in the current semester window

**Known acceptable loss:** per-occurrence overrides (e.g., user changed `location` for one session via P4's "改地点(本次)" flow) on imported courses are erased. This is a low-frequency operation; if the user complains, P6+ can introduce a `localPatches` table that survives re-import.

### 5.5 `ImportCoursesUseCase` — top-level orchestrator

Returns `Flow<ImportStep>` so the UI's progress screen can render checkmarks in real time:

```kotlin
sealed class ImportStep {
    object LoggingIn : ImportStep()
    object FetchingTerm : ImportStep()
    object FetchingWeekDate : ImportStep()
    data class FetchingSchedule(val termCode: String) : ImportStep()
    object Mapping : ImportStep()
    data class Preview(val items: List<TimelineItemEntity>, val term: TermDto, val countToReplace: Int) : ImportStep()
    object Writing : ImportStep()
    data class Done(val result: ImportResult) : ImportStep()
    object Cancelled : ImportStep()
    data class Failed(val err: ImportError) : ImportStep()
}
```

**Confirmation handoff:** between `Preview` and `Writing`, the flow `awaitConfirm()`s on a `Channel<Boolean>(RENDEZVOUS)` exposed via `ImportViewModel.onConfirm()` / `onCancel()`. This decouples the suspendable use case from Compose state.

`finally { apiClient.close() }` always runs — Retrofit instance and cookie jar are dropped on success, cancel, or failure.

---

## 6 · UI — 4-Screen Wizard

Each screen mounts in an internal `NavHost` rooted at `ImportNavGraph`. Shared top bar: `$ import-wizard [N/4]`. Shared bottom row: `[← 返回] [继续 →]` (final screen: `[取消] [确认导入]`).

### Screen 1 · `ImportCredentialsScreen`

```
$ login.bit.edu.cn

  学号       [____________]
  密码       [____________]  👁
  [✓] 记住密码（用 Keystore 加密保存）

  网络模式
   ○ 校内（直连 login.bit.edu.cn）
   ● 校外（WebVPN 转发）

  [ 登录 → ]
```

- Prefill from `ImportCredentialPrefs` when present; auto-check the box and select last-used network mode
- Eye icon toggles password visibility
- `BackHandler` allows returning to the entry point (Settings / empty-state CourseWeekGrid)
- Tapping `[登录 →]` triggers ViewModel → starts the flow, which runs `LoggingIn → FetchingTerm` before navigating to Screen 2

### Screen 2 · `ImportTermPickerScreen`

```
$ select-term

  当前学期:  2024-2025-2  ▼

  [其他可选]
    2024-2025-1
    2024-2025-2  (当前)
    2025-2026-1  (提前开放)

  [ 抓取课表 → ]
```

- Default selection is the response of `getCurrentTerm()`; `getTerms()` populates the dropdown
- Tags: "(当前)" and "(提前开放)" — proactive disclosure
- **Non-current term warning** (see §5.3): if the user changes the selection away from `getCurrentTerm()`, render a yellow banner above the button: `"⚠ 切换到非当前学期将使用现有学期开始日期来计算时间，可能不准确"` — button stays enabled but the warning is sticky
- `[← 返回]` allowed; returns to Screen 1 but **keeps state** (terms list and credentials are still loaded)
- Tapping `[抓取课表 →]` continues the flow into `FetchingWeekDate → FetchingSchedule → Mapping → Preview`

### Screen 3 · `ImportProgressScreen` (unbacked)

```
$ fetching...

  ✓ 登录成功
  ✓ 拉取学期信息
  ✓ 反推学期开始日期 (2026-02-24)
  ○ 抓取 2024-2025-2 课表...    [spinner]
  ○ 字段映射

  (此屏不可返回，请稍候 5-15 秒)
```

- `BackHandler { /* no-op */ }` — system back is swallowed to avoid mid-flight cancellation; users can wait or kill the app
- Toast `"已根据教务系统设置学期开始日期：YYYY-MM-DD"` fires the moment `ResolveSemesterAnchorUseCase` auto-writes
- Any `ImportStep.Failed` here flips this screen into an inline error variant (red top frame + error message + `[重试]` button that pops back to Screen 1 preserving prefilled credentials)

### Screen 4 · `ImportPreviewScreen`

```
$ confirm-import

  学期:     2024-2025-2
  课程:     8 门
  节次:     96 节 (展开后)
  学期开始: 2026-02-24

  [展开列表 ▼]
    高等数学A · 张三 · 周一 1-2 / 周三 3-4
    线性代数 · 李四 · 周二 5-6
    ...

  ⚠ 将覆盖学期 2024-2025-2 已有的 12 条旧导入数据
     MANUAL 手输的课程和其他学期不受影响

  [取消]  [ 确认导入 ]
```

- Course list is collapsed by default; tap the chevron to expand a LazyColumn
- Warning row hidden if `countToReplace == 0`
- `[确认导入]` resolves the `awaitConfirm()` channel with `true`; `[取消]` resolves with `false` and pops back to Settings
- On `Done`: Snackbar `"导入成功 · 8 门课 · 覆盖 12 条旧数据"` + auto-navigate back to Settings

---

## 7 · Entry Points

**A · Settings → "从教务系统导入课表"** (permanent entry)
- Subsection: 课程 → "从教务系统导入"
- Tapping launches `ImportNavGraph` as a full-screen modal

**B · CourseWeekGridScreen empty-state card** (first-run discovery)
- When the user opens the week-grid view and the database has zero `COURSE` rows (any source), instead of the empty 7×N grid, render:
  ```
  📥 还没有课程？
  [ 从教务系统一键导入 ]
  
  [手动添加] 也可以
  ```
- Tapping the button launches `ImportNavGraph`; the secondary "手动添加" link opens the existing `AddCourseScreen`

Both entry points feed into the same `ImportNavGraph`, so the wizard implementation is single-rooted.

---

## 8 · Error Handling — User-Facing Recovery

| `ImportError` | Trigger | UX | Recovery action |
|---|---|---|---|
| `WrongCredentials` | CAS 200 with `class="login-error"` | Red banner on Screen 1: `"密码错误，请重新输入"` | Clear Keystore; user re-enters password |
| `AccountLocked` | CAS error text contains `"账号已锁定"` | Red banner on Screen 1: `"账号已锁定，请稍后或修改密码后重试"` | Clear Keystore; user opens BIT web to unlock |
| `CaptchaRequired` | CAS response contains a captcha form | Yellow banner on Screen 1: `"教务系统要求验证码——这通常意味着此前多次输错。请稍后再试，或访问网页端手动登录一次"` + `[打开浏览器]` button | Linkify to `https://login.bit.edu.cn` |
| `NetworkFail` | OkHttp `IOException` / `SocketTimeoutException` | Red banner on Screen 1 or 3: `"网络异常，请检查网络后重试"` | `[重试]` button reruns the flow |
| `ParseFail(msg)` | HTML / JSON structure changed | Red banner on Screen 3: `"教务系统返回数据格式异常，可能是接口改版"` + show `msg` | `[反馈]` button opens GitHub issue with diag info |
| `NoCurrentTerm` | `getCurrentTerm()` empty | Toast on Screen 2: `"教务系统未返回当前学期，请手动选择"` | Term list still shown; user picks manually |
| `EmptySchedule` | `getSchedule()` returns 0 rows | Screen 4 preview shows 0 courses + `"该学期教务系统中无课程数据"` | `[确认导入]` disabled |

**Diagnostic info attached to issue link:** app version, Android version, ROM, anonymised stack trace, last successful step, the offending HTML/JSON snippet (truncated to 500 chars).

---

## 9 · Testing Strategy

| Layer | Test type | Fixtures |
|---|---|---|
| `AesCbcCrypto` | JVM unit | Deterministic IV injection; one real BIT login response captured and replayed |
| `SkzcExpander` | JVM unit | All-zeros, malformed, single/double-week, full 16-bit, 20-bit cases |
| `MapBitCourseUseCase` | JVM unit | Captured `ScheduleRowDto` JSON — typical course, course with multiple instructors, course with missing endPeriod, cross-week pattern |
| `ResolveSemesterAnchorUseCase` | JVM unit | `unset → backsolve`, `set → respect`, edge: BIT returns currentWeek=0 |
| `ReplaceImportedCoursesUseCase` | Room instrumented | Mix of IMPORTED_PORTAL + MANUAL rows; verify only the targeted slice is deleted |
| `SsoLoginUseCase` | JVM unit | MockWebServer: serve login page HTML, then 302 (success) / 200 with error class (wrong pwd) / captcha-required HTML |
| `BitJwappService` | JVM unit | MockWebServer with anonymised real JSON for each endpoint |
| `ImportCoursesUseCase` | JVM unit | Full mock stack; assert `Flow<ImportStep>` emits the expected sequence including `awaitConfirm()` paths (confirm / cancel) |
| `ImportViewModel` | JVM unit | StateFlow transitions across all 4 screens; channel rendezvous correctness; error mapping coverage |
| Real-device DoD | Manual | Real BIT account: 校内 success / 校外 WebVPN success / wrong password / select non-current term / re-import covers prior |

**Fixture capture protocol:** during `p5-polish` real-device DoD, use OkHttp `HttpLoggingInterceptor` to capture the live responses. Anonymise (replace student-id-like patterns, instructor names, course codes only if sensitive) and commit under `app/src/test/resources/bit-fixtures/`. All subsequent unit tests load from these fixtures — no network dependency in CI.

---

## 10 · Phase Breakdown

Same five-phase rhythm as P4 (each tagged, each gets spec-compliance + code-review sub-agent passes):

### Phase 1 · `p5-net` (network + crypto foundation, no UI)
- `AesCbcCrypto` with deterministic-IV unit tests
- `BitCookieJar`, `BitApiClient`, `BitUrlsConfig`
- DTOs: `CasLoginDto`, `TermDto`, `ScheduleRowDto`, `WeekDateDto`
- Retrofit services: `BitCasService`, `BitJwappService`
- New `@Provides @Singleton @Named("bit")` in `NetworkModule`
- MockWebServer-backed service unit tests with captured fixtures
- ~8–10 commits

### Phase 2 · `p5-import` (domain layer, no UI)
- All `domain/import/model/*.kt` data classes (incl. `ImportStep` and `ImportError` sealed classes)
- `SsoLoginUseCase`, `ResolveSemesterAnchorUseCase`, `MapBitCourseUseCase`, `ReplaceImportedCoursesUseCase`
- `ImportCoursesUseCase` (the `Flow<ImportStep>` orchestrator)
- 2 new DAO queries on `TimelineDao` (`countImportedInRange`, `deleteImportedInRange`)
- Full unit-test coverage of mapping logic and orchestrator flow
- Instrumented test for `ReplaceImportedCoursesUseCase` (Room-backed)
- ~8–10 commits

### Phase 3 · `p5-ui` (wizard screens + ViewModel)
- `ImportViewModel` with state machine + `Channel<Boolean>` confirm gate
- 4 screen Composables + shared scaffolding
- `ImportNavGraph` internal NavHost
- Error banner Composables for each `ImportError` variant
- ViewModel unit tests covering all flow paths (success / each error / cancel)
- ~8–10 commits

### Phase 4 · `p5-polish` (entry points + Keystore + DoD)
- Settings entry under "课程" section
- CourseWeekGridScreen empty-state card with primary import CTA
- `ImportCredentialPrefs` (EncryptedSharedPreferences)
- Wire prefill / save / clear logic into `ImportViewModel`
- Real-device DoD on a BIT account (校内 + 校外)
- Capture & commit anonymised fixtures
- ~5–7 commits

**Total**: 1 PR `feature/p5-bit-import`, 4 phase tags, ~30–35 commits.

---

## 11 · Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| BIT changes CAS HTML structure (e.g. renames `execution` field) | Medium | High (login breaks) | Parse via regex with permissive fallback + `ParseFail` UX gives users a feedback path |
| BIT WebVPN requires path-prefix rewriting in addition to host swap | Low–Medium | Medium (校外 path fails) | Add `WebVpnPathInterceptor` as a targeted fix during `p5-polish` real-device testing |
| Captcha enforcement becomes default for all accounts | Low | High (no auto-login) | `CaptchaRequired` UX redirects to web; future option: WebView fallback in P6 |
| User on a non-BIT school tries to use the feature | Low | Low (feature simply fails) | Settings entry copy clarifies "BIT 北理工教务系统"; future P6 may add multi-school support |
| `seriesId` allocation races with concurrent imports | Very low (no concurrent path exposed) | High (DB corruption) | `ImportCoursesUseCase` holds a coroutine-scoped lock; entry points disable re-tap during flow |
| AES IV randomness on old Android versions | Low | Low (replay-able cipher, still encrypted) | `SecureRandom` on Android API 24+ uses `/dev/urandom`; project minSdk is 26 |
| Single `SemesterPreferences.startDate` doesn't fit multi-term imports | Medium | Medium (non-current term's `startAt` falls on wrong dates) | Surface a Screen-2 warning when user selects non-current term; defer per-term anchor table to a future iteration |
| Real BIT account misuse / abuse | N/A | N/A | App stores password only locally in Keystore; no telemetry; user can clear via "退出登录" |

---

## 12 · Open Questions (deferred, NOT blocking implementation)

- *Does the user want a "退出登录 / 清除凭据" Settings entry?* — Implicitly yes; will add to `p5-polish` under the import settings subsection.
- *Should re-import write a row to a future "import history" table?* — Out of scope for P5; user can read `IMPORTED_PORTAL` rows directly to know what's there.
- *Multi-school support (Tsinghua, BUPT, etc.)?* — Deferred to P6+. The `data/network/bit/` sub-package boundary leaves the door open without committing to it now.

---

## 13 · Definition of Done

Real-device DoD checklist (executed in `p5-polish`):

- [ ] Settings → "从教务系统导入" launches the wizard
- [ ] CourseWeekGridScreen empty state shows the import CTA
- [ ] 校内 mode succeeds end-to-end on real BIT account: login → term picker → progress → preview → confirm → CourseWeekGrid shows imported courses
- [ ] 校外 (WebVPN) mode succeeds end-to-end from a non-campus network
- [ ] Wrong password shows the right banner and clears Keystore
- [ ] Selecting a non-current term in the picker fetches that term's data
- [ ] Re-import on a semester with existing IMPORTED_PORTAL data: preview shows accurate `countToReplace`; after confirm, old IMPORTED_PORTAL rows are gone and MANUAL rows remain
- [ ] Imported courses render correctly in TimelineScreen (single-day) and CourseWeekGridScreen (7×N)
- [ ] Keystore "记住密码" works: re-opening wizard prefills credentials
- [ ] Auto-backsolved `SemesterPreferences.startDate` toast displays on first import when prefs are unset; suppressed when prefs are set
- [ ] All 118+ existing unit tests still pass; new P5 tests pass
