# P6 · BIT 成绩查询 M1（核心）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 P5 BIT 登录基建之上，新增成绩查询（cjcx）→ 落 Room → 终端风 Canvas 可视化 → AI 学情报告 + 跳聊天追问 + 挂科/重修高亮的完整闭环。

**Architecture:** 复用 `SsoLoginUseCase` / `BitApiClient`（CAS 会话、cookie jar、网络模式），仅在网络层新增 `BitCjcxService`（成绩查询 ehall app，自带 warm-up）。成绩两步拉取（成绩列表 + 每学期排名详情，排名解耦降级）→ `ReplaceGradesUseCase` 覆盖式落库 → `ComputeGpaUseCase` 聚合 `GradeBook` → `GradesScreen` 用手写 Canvas 画 GPA 趋势/成绩分布 → `AnalyzeGradesUseCase`（复用 `LLMProvider`）流式报告 + `StartGradeChatUseCase` 建会话跳 P1。

**Tech Stack:** Kotlin · Retrofit + kotlinx-serialization · Room (v7→8, fallbackToDestructive) · Hilt · Jetpack Compose Canvas · JUnit4 + MockWebServer + mockk + Turbine。

**⚠ 协议前提（关键）：** cjcx 的 `appId`、各 `.do` 端点路径、`requestParamStr`/`querySetting` 形状、成绩/排名字段名（KCM/XF/CJ/JD/BJPM/ZYPM…）**全部是假设**。P5 教训：单测全绿只证明代码符合假设、不证明假设正确。本计划先按假设把代码 + 单测写出来（fixture 为合成数据），**最后 Task 23 真机抓包验证并修正字段/端点 + 落地真实脱敏 fixture**。字段名集中在 DTO 的 `@SerialName` 与 `BitCjcxService` 常量里，便于一处修正。

---

## File Structure

**新建：**
- `app/src/main/java/com/example/personal_studio/data/network/bit/dto/GradeRowDto.kt` — 成绩列表 DTO + `GradeListResponse`
- `app/src/main/java/com/example/personal_studio/data/network/bit/dto/GradeRankDto.kt` — 排名 DTO + `GradeRankResponse`
- `app/src/main/java/com/example/personal_studio/data/network/bit/service/BitCjcxService.kt` — cjcx Retrofit 接口
- `app/src/main/java/com/example/personal_studio/data/local/db/entity/GradeEntryEntity.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/entity/TermRankEntity.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/dao/GradesDao.kt`
- `app/src/main/java/com/example/personal_studio/core/util/GpaCalculator.kt`
- `app/src/main/java/com/example/personal_studio/core/util/GradeBucketer.kt`
- `app/src/main/java/com/example/personal_studio/core/charts/ChartMath.kt`
- `app/src/main/java/com/example/personal_studio/core/charts/LineChart.kt`
- `app/src/main/java/com/example/personal_studio/core/charts/BarChart.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/model/GradeModels.kt` — `GradeItem`/`TermRank`/`TermGrades`/`GradeBook`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/model/SyncGradesModels.kt` — `GradesSyncRequest`/`SyncGradesStep`/`GradesSyncError`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/MapGradeUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/ReplaceGradesUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/BuildGradeSummaryUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/AnalyzeGradesUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/bitgrades/StartGradeChatUseCase.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesSyncViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/GradesViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/GradesSyncScreen.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/GradesScreen.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/AiAnalysisSheet.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/GpaOverviewCard.kt`
- `app/src/main/java/com/example/personal_studio/feature/bitgrades/ui/components/TermGradeSection.kt`
- Tests under `app/src/test/java/com/example/personal_studio/...` mirroring package paths
- Fixtures under `app/src/test/resources/bit-fixtures/cjcx-*.json`

**修改：**
- `data/network/bit/BitApiClient.kt` — 新增 `val cjcx`
- `data/local/db/AppDatabase.kt` — 注册 2 实体、`VERSION = 8`、`gradesDao()`
- `core/di/DatabaseModule.kt` — `provideGradesDao`
- `ui/navigation/NavRoutes.kt` — `GRADES` / `GRADES_SYNC`
- `ui/AppNavHost.kt` — 注册两条路由
- `feature/settings/ui/SettingsScreen.kt` — 新增「从教务系统查询成绩」入口行

---

## Phase M1-A · 网络层（DTO + Service + Client）

### Task 1: 成绩列表 DTO + `getGrades` + 解析测试

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/network/bit/dto/GradeRowDto.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/network/bit/service/BitCjcxService.kt`
- Create: `app/src/test/resources/bit-fixtures/cjcx-grades-sample.json`
- Test: `app/src/test/java/com/example/personal_studio/data/network/bit/BitCjcxServiceTest.kt`

- [ ] **Step 1: 写成绩列表 DTO**

`GradeRowDto.kt`（字段名为假设，Task 23 真机修正）：

```kotlin
package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单门课成绩行（cjcx 成绩查询 app）。字段名为假设，真机 DoD 验证后修正。
 * score 用 String：BIT 成绩可能是数字"92"或等级"优"/"通过"。若真机发现 CJ 返回
 * 的是 JSON number 而非 string，改用自定义序列化器或 Double（见 Task 23）。
 */
@Serializable
data class GradeRowDto(
    @SerialName("XNXQDM") val termCode: String? = null,
    @SerialName("XNXQMC") val termName: String? = null,
    @SerialName("KCM") val courseName: String? = null,
    @SerialName("KCH") val courseCode: String? = null,
    @SerialName("XF") val credit: Double? = null,
    @SerialName("CJ") val score: String? = null,
    @SerialName("JD") val gradePoint: Double? = null,
    @SerialName("DJCJMC") val gradeLetter: String? = null,
    @SerialName("KCXZMC") val category: String? = null,
    @SerialName("CXCKDM_DISPLAY") val attemptType: String? = null,
)

@Serializable
data class GradeListResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxstuxqcj") val cxstuxqcj: Rows? = null)
    @Serializable data class Rows(
        val rows: List<GradeRowDto> = emptyList(),
        @SerialName("totalSize") val totalSize: Int? = null,
    )
}
```

- [ ] **Step 2: 写 `BitCjcxService` 接口**

`BitCjcxService.kt`（warm-up + 两个 module 端点；常量为假设）：

```kotlin
package com.example.personal_studio.data.network.bit.service

import com.example.personal_studio.data.network.bit.dto.GradeListResponse
import com.example.personal_studio.data.network.bit.dto.GradeRankResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * BIT 教务"成绩查询"(cjcx) ehall app。与 wdkbby 同 host(jxzxehallapp)、同 cookie。
 * 每个 ehall app 需各自 warm-up（getIndex + getAppConfig(本 app 的 appId) + i18n），
 * 否则 module 端点返回 403 openresty。
 *
 * ⚠ APP_ID 与各 .do 路径、字段为假设 —— 真机 DoD(Task 23) 验证修正。
 */
interface BitCjcxService {

    @GET("jwapp/sys/cjcx/*default/index.do")
    suspend fun getIndex(): Response<ResponseBody>

    @GET("jwapp/sys/funauthapp/api/getAppConfig/cjcx-{appId}.do")
    suspend fun getAppConfig(@Path("appId") appId: String = APP_ID): Response<ResponseBody>

    @GET("jwapp/i18n.do")
    suspend fun switchLang(
        @Query("appName") appName: String = "cjcx",
        @Query("EMAP_LANG") emapLang: String = "zh",
    ): Response<ResponseBody>

    /** 第一步：成绩列表。ehall queryList 风格表单参数；全量(不分页或大页)。 */
    @FormUrlEncoded
    @POST("jwapp/sys/cjcx/modules/cjcx/cxstuxqcj.do")
    suspend fun getGrades(
        @Field("querySetting") querySetting: String = "[]",
        @Field("pageSize") pageSize: Int = 1000,
        @Field("pageNumber") pageNumber: Int = 1,
        @Field("*order") order: String = "-XNXQDM",
    ): Response<GradeListResponse>

    /** 第二步：某学期班级/专业排名详情（对应"获取详细信息"）。 */
    @FormUrlEncoded
    @POST("jwapp/sys/cjcx/modules/cjcx/cxstupm.do")
    suspend fun getRankDetail(
        @Field("requestParamStr") requestParamStr: String,
    ): Response<GradeRankResponse>

    companion object {
        /** TBD —— 真机抓包确认 cjcx 的真实 appId。 */
        const val APP_ID = "4585275880135870"
    }
}
```

> 注意：`getRankDetail` 引用了 `GradeRankResponse`，它在 Task 2 创建。**先建 Task 2 的 DTO 再编译**，或本 Task 暂时注释 `getRankDetail` 直到 Task 2。建议执行顺序：Task 1 写 DTO+service 但先注释 `getRankDetail` 与其 import → Step 4 编译通过 → Task 2 取消注释。

- [ ] **Step 3: 写合成 fixture** `cjcx-grades-sample.json`

```json
{
  "datas": {
    "cxstuxqcj": {
      "totalSize": 3,
      "rows": [
        {"XNXQDM":"2024-2025-2","XNXQMC":"2024-2025学年 第二学期","KCM":"高等数学A","KCH":"MATH101","XF":5.0,"CJ":"92","JD":4.0,"DJCJMC":"A","KCXZMC":"必修","CXCKDM_DISPLAY":"正常"},
        {"XNXQDM":"2024-2025-2","XNXQMC":"2024-2025学年 第二学期","KCM":"大学物理","KCH":"PHYS101","XF":4.0,"CJ":"55","JD":0.0,"DJCJMC":"F","KCXZMC":"必修","CXCKDM_DISPLAY":"正常"},
        {"XNXQDM":"2024-2025-1","XNXQMC":"2024-2025学年 第一学期","KCM":"线性代数","KCH":"MATH102","XF":3.0,"CJ":"88","JD":3.7,"DJCJMC":"B","KCXZMC":"必修","CXCKDM_DISPLAY":"正常"}
      ]
    }
  }
}
```

- [ ] **Step 4: 写解析测试**

`BitCjcxServiceTest.kt`（仿 `BitJwappServiceTest`）：

```kotlin
package com.example.personal_studio.data.network.bit

import com.example.personal_studio.data.network.bit.service.BitCjcxService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class BitCjcxServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: BitCjcxService

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create()
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `getGrades parses rows`() = runBlocking {
        val body = javaClass.getResourceAsStream("/bit-fixtures/cjcx-grades-sample.json")!!
            .bufferedReader().readText()
        server.enqueue(MockResponse().setBody(body))

        val resp = service.getGrades()

        assertEquals(true, resp.isSuccessful)
        val rows = resp.body()!!.datas.cxstuxqcj!!.rows
        assertEquals(3, rows.size)
        assertEquals("高等数学A", rows[0].courseName)
        assertEquals(5.0, rows[0].credit!!, 0.001)
        assertEquals("92", rows[0].score)
        assertEquals(4.0, rows[0].gradePoint!!, 0.001)
        assertEquals("2024-2025-1", rows[2].termCode)
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*BitCjcxServiceTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/network/bit/dto/GradeRowDto.kt \
        app/src/main/java/com/example/personal_studio/data/network/bit/service/BitCjcxService.kt \
        app/src/test/resources/bit-fixtures/cjcx-grades-sample.json \
        app/src/test/java/com/example/personal_studio/data/network/bit/BitCjcxServiceTest.kt
git commit -m "p6(net): cjcx 成绩列表 DTO + BitCjcxService + 解析测试"
```

---

### Task 2: 排名详情 DTO + `getRankDetail` 解析测试

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/network/bit/dto/GradeRankDto.kt`
- Create: `app/src/test/resources/bit-fixtures/cjcx-rank-sample.json`
- Modify: `BitCjcxService.kt`（取消注释 `getRankDetail`）
- Test: `BitCjcxServiceTest.kt`（追加用例）

- [ ] **Step 1: 写排名 DTO**

```kotlin
package com.example.personal_studio.data.network.bit.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 某学期排名详情（"获取详细信息"）。字段名为假设，真机修正。 */
@Serializable
data class GradeRankDto(
    @SerialName("XNXQDM") val termCode: String? = null,
    @SerialName("BJPM") val classRank: Int? = null,
    @SerialName("BJZRS") val classTotal: Int? = null,
    @SerialName("ZYPM") val majorRank: Int? = null,
    @SerialName("ZYZRS") val majorTotal: Int? = null,
)

@Serializable
data class GradeRankResponse(val datas: Datas) {
    @Serializable data class Datas(@SerialName("cxstupm") val cxstupm: Rows? = null)
    @Serializable data class Rows(val rows: List<GradeRankDto> = emptyList())
}
```

- [ ] **Step 2: 取消注释 service 的 `getRankDetail`**（含其 import），确认 Task 1 已就绪。

- [ ] **Step 3: 写 fixture** `cjcx-rank-sample.json`

```json
{"datas":{"cxstupm":{"rows":[{"XNXQDM":"2024-2025-2","BJPM":5,"BJZRS":32,"ZYPM":18,"ZYZRS":120}]}}}
```

- [ ] **Step 4: 追加测试用例到 `BitCjcxServiceTest`**

```kotlin
    @Test fun `getRankDetail parses rank row`() = runBlocking {
        val body = javaClass.getResourceAsStream("/bit-fixtures/cjcx-rank-sample.json")!!
            .bufferedReader().readText()
        server.enqueue(MockResponse().setBody(body))

        val resp = service.getRankDetail("""{"XNXQDM":"2024-2025-2"}""")

        assertEquals(true, resp.isSuccessful)
        val row = resp.body()!!.datas.cxstupm!!.rows.single()
        assertEquals(5, row.classRank)
        assertEquals(120, row.majorTotal)
    }
```

- [ ] **Step 5: 运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*BitCjcxServiceTest*"`
Expected: PASS（2 个用例）

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "p6(net): cjcx 排名详情 DTO + getRankDetail + 解析测试"
```

---

### Task 3: `BitApiClient` 暴露 `cjcx`

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/network/bit/BitApiClient.kt`

- [ ] **Step 1: 加 `cjcx` 访问器**（cjcx 与 wdkbby 同 host，复用 `jwappRetrofit`）

在 `val jwapp` 之后插入：

```kotlin
    /** 成绩查询服务，与 jwapp 同 host(jxzxehallapp)，复用同一 Retrofit。 */
    val cjcx: com.example.personal_studio.data.network.bit.service.BitCjcxService
        get() = jwappRetrofit?.create() ?: error("BitApiClient: session not open")
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/network/bit/BitApiClient.kt
git commit -m "p6(net): BitApiClient 暴露 cjcx 成绩服务"
```

---

## Phase M1-B · Room 数据层

### Task 4: `GradeEntryEntity` + `TermRankEntity` + `GradesDao` + DB 注册

**Files:**
- Create: `data/local/db/entity/GradeEntryEntity.kt`
- Create: `data/local/db/entity/TermRankEntity.kt`
- Create: `data/local/db/dao/GradesDao.kt`
- Modify: `data/local/db/AppDatabase.kt`
- Modify: `core/di/DatabaseModule.kt`

- [ ] **Step 1: 写 `GradeEntryEntity`**

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grade_entries",
    indices = [Index(value = ["termCode", "courseCode", "attemptType"], unique = true)],
)
data class GradeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termCode: String,
    val termName: String,
    val courseName: String,
    val courseCode: String,
    val credit: Double,
    val score: String,
    val gradePoint: Double?,
    val gradeLetter: String?,
    val category: String?,
    val attemptType: String,
    val isPass: Boolean,
    val fetchedAt: Long,
)
```

- [ ] **Step 2: 写 `TermRankEntity`**

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 单学期排名；总排名用保留键 termCode = "OVERALL"。 */
@Entity(tableName = "term_ranks")
data class TermRankEntity(
    @PrimaryKey val termCode: String,
    val termName: String,
    val weightedGpa: Double,
    val classRank: Int?,
    val classTotal: Int?,
    val majorRank: Int?,
    val majorTotal: Int?,
    val fetchedAt: Long,
)
```

- [ ] **Step 3: 写 `GradesDao`**

```kotlin
package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GradesDao {
    @Query("SELECT * FROM grade_entries ORDER BY termCode DESC, courseName")
    fun observeAll(): Flow<List<GradeEntryEntity>>

    @Query("SELECT * FROM grade_entries")
    suspend fun listAll(): List<GradeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<GradeEntryEntity>)

    @Query("DELETE FROM grade_entries")
    suspend fun clearGrades(): Int

    @Query("SELECT * FROM term_ranks")
    fun observeRanks(): Flow<List<TermRankEntity>>

    @Query("SELECT * FROM term_ranks")
    suspend fun listRanks(): List<TermRankEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRanks(rows: List<TermRankEntity>)

    @Query("DELETE FROM term_ranks")
    suspend fun clearRanks(): Int
}
```

- [ ] **Step 4: 注册到 `AppDatabase`**

修改 `AppDatabase.kt`：
- 加 import：`GradeEntryEntity`、`TermRankEntity`、`GradesDao`
- `entities` 数组末尾加 `GradeEntryEntity::class, TermRankEntity::class,`
- `const val VERSION = 7` → `8`
- 抽象方法加：`abstract fun gradesDao(): com.example.personal_studio.data.local.db.dao.GradesDao`

- [ ] **Step 5: 加 Hilt provider**

在 `DatabaseModule.kt` 的 `provideTimelineDao` 之后加：

```kotlin
    @Provides
    fun provideGradesDao(db: AppDatabase): com.example.personal_studio.data.local.db.dao.GradesDao =
        db.gradesDao()
```

- [ ] **Step 6: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（Room 检测到 schema 变更；因 `fallbackToDestructiveMigration` 已开启，dev 期无需写迁移——既定做法：开发库数据可丢弃）

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "p6(data): grade_entries + term_ranks 实体 + GradesDao + DB v7→8"
```

---

### Task 5: `ReplaceGradesUseCase`（Room 仪器测试）

**Files:**
- Create: `domain/bitgrades/ReplaceGradesUseCase.kt`
- Test: `app/src/androidTest/java/com/example/personal_studio/domain/bitgrades/ReplaceGradesUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**（仪器测试，真 Room in-memory）

```kotlin
package com.example.personal_studio.domain.bitgrades

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplaceGradesUseCaseTest {
    private lateinit var db: AppDatabase
    private lateinit var useCase: ReplaceGradesUseCase

    private fun grade(term: String, code: String, attempt: String = "正常") = GradeEntryEntity(
        termCode = term, termName = term, courseName = code, courseCode = code,
        credit = 3.0, score = "90", gradePoint = 4.0, gradeLetter = "A",
        category = "必修", attemptType = attempt, isPass = true, fetchedAt = 1L,
    )
    private fun rank(term: String) = TermRankEntity(term, term, 3.8, 1, 30, 5, 100, 1L)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        useCase = ReplaceGradesUseCase(db.gradesDao())
    }
    @After fun tearDown() = db.close()

    @Test fun replace_clears_then_writes() = runBlocking {
        useCase.invoke(listOf(grade("2023-2024-1", "OLD")), listOf(rank("2023-2024-1")))
        useCase.invoke(
            listOf(grade("2024-2025-1", "NEW1"), grade("2024-2025-2", "NEW2")),
            listOf(rank("2024-2025-1"), rank("OVERALL")),
        )
        val all = db.gradesDao().listAll()
        assertEquals(2, all.size)
        assertEquals(setOf("NEW1", "NEW2"), all.map { it.courseCode }.toSet())
        assertEquals(2, db.gradesDao().listRanks().size)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*ReplaceGradesUseCaseTest*"`（需连真机/模拟器）
Expected: 编译失败（`ReplaceGradesUseCase` 不存在）

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import androidx.room.withTransaction
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import javax.inject.Inject

/** 覆盖式写入：清空旧成绩+排名，写入新数据。成绩无手输来源，故全量覆盖。 */
class ReplaceGradesUseCase @Inject constructor(private val dao: GradesDao) {
    suspend fun invoke(entries: List<GradeEntryEntity>, ranks: List<TermRankEntity>) {
        dao.clearGrades()
        dao.clearRanks()
        dao.upsertAll(entries)
        dao.upsertRanks(ranks)
    }
}
```

> 注：本实现不强制事务（Step 1 测试不验证原子性）。若 code-review 要求原子性，可注入 `AppDatabase` 并用 `db.withTransaction { ... }` 包裹四步。

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*ReplaceGradesUseCaseTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "p6(domain): ReplaceGradesUseCase 覆盖式落库 + Room 仪器测试"
```

---

## Phase M1-C · 领域纯逻辑（GPA / 分桶 / 映射 / 聚合）

### Task 6: `GpaCalculator.weightedGpa`

**Files:**
- Create: `core/util/GpaCalculator.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/util/GpaCalculatorTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GpaCalculatorTest {
    @Test fun `weighted gpa ignores null-point courses`() {
        // (5学分×4.0 + 3×3.0) / (5+3) = 29/8 = 3.625；P/NP(null)不计
        val gpa = GpaCalculator.weightedGpa(listOf(5.0 to 4.0, 3.0 to 3.0, 2.0 to null))
        assertEquals(3.625, gpa, 0.0001)
    }
    @Test fun `empty or all-null yields zero`() {
        assertEquals(0.0, GpaCalculator.weightedGpa(emptyList()), 0.0001)
        assertEquals(0.0, GpaCalculator.weightedGpa(listOf(3.0 to null)), 0.0001)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*GpaCalculatorTest*"`
Expected: FAIL（未定义）

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.core.util

/** GPA 计算。M1 仅含加权 GPA；What-if 反推/预测在 M2 追加。 */
object GpaCalculator {
    /** 学分加权 GPA：Σ(credit×point)/Σ(credit)；point==null 的课（P/NP）不计入。 */
    fun weightedGpa(items: List<Pair<Double, Double?>>): Double {
        var sumCredit = 0.0
        var sumWeighted = 0.0
        for ((credit, point) in items) {
            if (point == null) continue
            sumCredit += credit
            sumWeighted += credit * point
        }
        return if (sumCredit == 0.0) 0.0 else sumWeighted / sumCredit
    }
}
```

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*GpaCalculatorTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/util/GpaCalculator.kt \
        app/src/test/java/com/example/personal_studio/core/util/GpaCalculatorTest.kt
git commit -m "p6(domain): GpaCalculator.weightedGpa + 单测"
```

---

### Task 7: 领域模型 `GradeModels`

**Files:**
- Create: `domain/bitgrades/model/GradeModels.kt`

- [ ] **Step 1: 写模型**（无测试——纯数据类；后续任务消费时受测）

```kotlin
package com.example.personal_studio.domain.bitgrades.model

/** 单门课（领域层，UI 直接消费）。 */
data class GradeItem(
    val courseName: String,
    val courseCode: String,
    val credit: Double,
    val score: String,
    val gradePoint: Double?,
    val gradeLetter: String?,
    val category: String?,
    val attemptType: String,
    val isPass: Boolean,
)

/** 排名（班级/专业），任一可缺。 */
data class TermRank(
    val classRank: Int?, val classTotal: Int?,
    val majorRank: Int?, val majorTotal: Int?,
) {
    /** 专业排名百分比（前 X%），数据不全时为 null。 */
    val majorPercentile: Int?
        get() = if (majorRank != null && majorTotal != null && majorTotal > 0)
            Math.ceil(majorRank * 100.0 / majorTotal).toInt() else null
}

data class TermGrades(
    val termCode: String,
    val termName: String,
    val courses: List<GradeItem>,
    val weightedGpa: Double,
    val rank: TermRank?,
)

/** 成绩单聚合根。terms 按 termCode 倒序（最新在前）。 */
data class GradeBook(
    val terms: List<TermGrades>,
    val overallGpa: Double,
    val totalCredits: Double,
    val overallRank: TermRank?,
) {
    val isEmpty: Boolean get() = terms.isEmpty()
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/bitgrades/model/GradeModels.kt
git commit -m "p6(domain): GradeBook/TermGrades/GradeItem/TermRank 模型"
```

---

### Task 8: `MapGradeUseCase`

**Files:**
- Create: `domain/bitgrades/MapGradeUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/MapGradeUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.network.bit.dto.GradeRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGradeUseCaseTest {
    private val mapper = MapGradeUseCase()

    @Test fun `maps a normal course`() {
        val e = mapper.invoke(GradeRowDto(
            termCode = "2024-2025-2", termName = "24春", courseName = "高数",
            courseCode = "M1", credit = 5.0, score = "92", gradePoint = 4.0,
            gradeLetter = "A", category = "必修", attemptType = "正常",
        ), fetchedAt = 7L)!!
        assertEquals("高数", e.courseName)
        assertEquals(5.0, e.credit, 0.001)
        assertTrue(e.isPass)
        assertEquals(7L, e.fetchedAt)
    }

    @Test fun `null courseName or termCode is dropped`() {
        assertNull(mapper.invoke(GradeRowDto(termCode = "x", courseName = null), 1L))
        assertNull(mapper.invoke(GradeRowDto(termCode = null, courseName = "y"), 1L))
    }

    @Test fun `failing gradePoint marks not pass`() {
        val e = mapper.invoke(GradeRowDto(
            termCode = "t", courseName = "物理", gradePoint = 0.0, score = "55",
        ), 1L)!!
        assertFalse(e.isPass)
        assertEquals("正常", e.attemptType) // 默认值
    }

    @Test fun `pass inferred from 等级 word when no gradePoint`() {
        val e = mapper.invoke(GradeRowDto(
            termCode = "t", courseName = "体育", gradePoint = null, score = "通过",
        ), 1L)!!
        assertTrue(e.isPass)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*MapGradeUseCaseTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.network.bit.dto.GradeRowDto
import javax.inject.Inject

/** GradeRowDto → GradeEntryEntity。课程名或学期缺失则丢弃(返回 null)。 */
class MapGradeUseCase @Inject constructor() {

    fun invoke(dto: GradeRowDto, fetchedAt: Long): GradeEntryEntity? {
        val name = dto.courseName?.takeIf { it.isNotBlank() } ?: return null
        val term = dto.termCode?.takeIf { it.isNotBlank() } ?: return null
        val score = dto.score?.trim().orEmpty()
        return GradeEntryEntity(
            termCode = term,
            termName = dto.termName?.takeIf { it.isNotBlank() } ?: term,
            courseName = name,
            courseCode = dto.courseCode.orEmpty(),
            credit = dto.credit ?: 0.0,
            score = score,
            gradePoint = dto.gradePoint,
            gradeLetter = dto.gradeLetter,
            category = dto.category,
            attemptType = dto.attemptType?.takeIf { it.isNotBlank() } ?: "正常",
            isPass = computePass(dto.gradePoint, score, dto.gradeLetter),
            fetchedAt = fetchedAt,
        )
    }

    private fun computePass(point: Double?, score: String, letter: String?): Boolean {
        if (point != null) return point > 0.0
        val passWords = listOf("优", "良", "中", "及格", "合格", "通过")
        if (letter != null && passWords.any { it in letter }) return true
        if (passWords.any { it in score }) return true
        if ("不及格" in score || "不合格" in score || "缺考" in score) return false
        score.toDoubleOrNull()?.let { return it >= 60.0 }
        return true // 未知 → 不武断判挂科
    }
}
```

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*MapGradeUseCaseTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "p6(domain): MapGradeUseCase + 单测"
```

---

### Task 9: `GradeBucketer`（成绩分布分桶）

**Files:**
- Create: `core/util/GradeBucketer.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/util/GradeBucketerTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.core.util

import com.example.personal_studio.domain.bitgrades.model.GradeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeBucketerTest {
    private fun item(score: String, point: Double? = null) =
        GradeItem("c", "c", 3.0, score, point, null, null, "正常", true)

    @Test fun `numeric scores bucket into A-F bands`() {
        val buckets = GradeBucketer.bucket(listOf(
            item("92"), item("85"), item("85"), item("73"), item("61"), item("40"),
        ))
        // 顺序固定 A,B,C,D,F；只保留 count>0
        assertEquals(listOf("A" to 1, "B" to 2, "C" to 1, "D" to 1, "F" to 1),
            buckets.map { it.label to it.count })
    }

    @Test fun `non-numeric scores bucket by raw label`() {
        val buckets = GradeBucketer.bucket(listOf(item("优"), item("优"), item("良")))
        assertEquals(setOf("优" to 2, "良" to 1), buckets.map { it.label to it.count }.toSet())
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradeBucketerTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.core.util

import com.example.personal_studio.domain.bitgrades.model.GradeItem

data class GradeBucket(val label: String, val count: Int)

/**
 * 成绩分布分桶。若大多数成绩是数字 → A/B/C/D/F 分段（A≥90,B80-89,C70-79,D60-69,F<60）；
 * 否则按原始等级字符串分桶（优/良/通过…）。
 */
object GradeBucketer {
    private val BAND_ORDER = listOf("A", "B", "C", "D", "F")

    fun bucket(items: List<GradeItem>): List<GradeBucket> {
        if (items.isEmpty()) return emptyList()
        val numeric = items.mapNotNull { it.score.toDoubleOrNull() }
        val numericRatio = numeric.size.toDouble() / items.size
        return if (numericRatio >= 0.5) bucketNumeric(items) else bucketLabel(items)
    }

    private fun band(score: Double): String = when {
        score >= 90 -> "A"; score >= 80 -> "B"; score >= 70 -> "C"
        score >= 60 -> "D"; else -> "F"
    }

    private fun bucketNumeric(items: List<GradeItem>): List<GradeBucket> {
        val counts = items.mapNotNull { it.score.toDoubleOrNull() }
            .groupingBy { band(it) }.eachCount()
        return BAND_ORDER.mapNotNull { b -> counts[b]?.let { GradeBucket(b, it) } }
    }

    private fun bucketLabel(items: List<GradeItem>): List<GradeBucket> =
        items.groupingBy { it.score.ifBlank { "—" } }.eachCount()
            .map { GradeBucket(it.key, it.value) }
            .sortedByDescending { it.count }
}
```

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradeBucketerTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "p6(domain): GradeBucketer 成绩分布分桶 + 单测"
```

---

### Task 10: `ComputeGpaUseCase`（库内成绩 → `GradeBook`）

**Files:**
- Create: `domain/bitgrades/ComputeGpaUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/ComputeGpaUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeGpaUseCaseTest {
    private val useCase = ComputeGpaUseCase()
    private fun g(term: String, code: String, credit: Double, point: Double?) =
        GradeEntryEntity(0, term, term, code, code, credit, "x", point, null, null, "正常", true, 1L)

    @Test fun `groups by term newest-first and computes per-term + overall gpa`() {
        val book = useCase.invoke(
            entries = listOf(
                g("2024-2025-1", "A", 4.0, 3.0),
                g("2024-2025-2", "B", 2.0, 4.0),
            ),
            ranks = listOf(TermRankEntity("2024-2025-2", "24春", 4.0, 1, 30, 5, 100, 1L)),
        )
        assertEquals(listOf("2024-2025-2", "2024-2025-1"), book.terms.map { it.termCode })
        assertEquals(4.0, book.terms[0].weightedGpa, 0.001)
        assertEquals(5, book.terms[0].rank!!.majorRank)
        // overall = (4×3 + 2×4)/(4+2) = 20/6 = 3.333
        assertEquals(3.3333, book.overallGpa, 0.001)
        assertEquals(6.0, book.totalCredits, 0.001)
    }

    @Test fun `term without rank row has null rank`() {
        val book = useCase.invoke(listOf(g("t", "A", 3.0, 3.0)), emptyList())
        assertEquals(null, book.terms[0].rank)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*ComputeGpaUseCaseTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.GpaCalculator
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.bitgrades.model.TermRank
import javax.inject.Inject

/** 库内成绩 + 排名 → GradeBook。GPA 一律由本类从成绩重算（term_ranks.weightedGpa
 *  仅作冗余存储，不作权威来源），避免漂移。 */
class ComputeGpaUseCase @Inject constructor() {

    fun invoke(entries: List<GradeEntryEntity>, ranks: List<TermRankEntity>): GradeBook {
        val rankByTerm = ranks.associateBy { it.termCode }
        val terms = entries.groupBy { it.termCode }
            .map { (code, rows) ->
                val items = rows.map { it.toItem() }
                TermGrades(
                    termCode = code,
                    termName = rows.first().termName,
                    courses = items.sortedByDescending { it.credit },
                    weightedGpa = GpaCalculator.weightedGpa(items.map { it.credit to it.gradePoint }),
                    rank = rankByTerm[code]?.toTermRank(),
                )
            }
            .sortedByDescending { it.termCode }
        val overallGpa = GpaCalculator.weightedGpa(entries.map { it.credit to it.gradePoint })
        val totalCredits = entries.filter { it.gradePoint != null }.sumOf { it.credit }
        return GradeBook(
            terms = terms,
            overallGpa = overallGpa,
            totalCredits = totalCredits,
            overallRank = rankByTerm["OVERALL"]?.toTermRank(),
        )
    }

    private fun GradeEntryEntity.toItem() = GradeItem(
        courseName, courseCode, credit, score, gradePoint, gradeLetter, category, attemptType, isPass,
    )
    private fun TermRankEntity.toTermRank() = TermRank(classRank, classTotal, majorRank, majorTotal)
}
```

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*ComputeGpaUseCaseTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "p6(domain): ComputeGpaUseCase 聚合 GradeBook + 单测"
```

---

## Phase M1-D · 同步编排

### Task 11: 同步模型 `SyncGradesModels`

**Files:**
- Create: `domain/bitgrades/model/SyncGradesModels.kt`

- [ ] **Step 1: 写模型**

```kotlin
package com.example.personal_studio.domain.bitgrades.model

import com.example.personal_studio.data.network.bit.NetworkMode

data class GradesSyncRequest(
    val username: String,
    val password: String,
    val networkMode: NetworkMode,
    val rememberPwd: Boolean,
)

/** 同步进度事件（无 Preview 确认环节——成绩不覆盖手输数据，直接落库）。 */
sealed class SyncGradesStep {
    object LoggingIn : SyncGradesStep()
    object FetchingGrades : SyncGradesStep()
    object FetchingRanks : SyncGradesStep()
    object Persisting : SyncGradesStep()
    data class Done(val termCount: Int, val courseCount: Int) : SyncGradesStep()
    data class Failed(val err: GradesSyncError) : SyncGradesStep()
}

/** 用户可见失败。排名不可用是非致命的(降级为 null)，不在此列。 */
sealed class GradesSyncError {
    object WrongCredentials : GradesSyncError()
    object AccountLocked : GradesSyncError()
    object CaptchaRequired : GradesSyncError()
    data class NetworkFail(val cause: Throwable) : GradesSyncError()
    data class ParseFail(val message: String) : GradesSyncError()
    object EmptyGrades : GradesSyncError()
    data class Unexpected(val cause: Throwable) : GradesSyncError()
}
```

- [ ] **Step 2: 编译 + Commit**

```bash
./gradlew :app:compileDebugKotlin
git add -A && git commit -m "p6(domain): SyncGradesStep/GradesSyncError/GradesSyncRequest 模型"
```

---

### Task 12: `SyncGradesUseCase`（Flow 编排）

**Files:**
- Create: `domain/bitgrades/SyncGradesUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/SyncGradesUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**（mockk 全栈 + Turbine）

```kotlin
package com.example.personal_studio.domain.bitgrades

import app.cash.turbine.test
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.GradeListResponse
import com.example.personal_studio.data.network.bit.dto.GradeRankResponse
import com.example.personal_studio.data.network.bit.dto.GradeRowDto
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncGradesUseCaseTest {

    private fun req() = GradesSyncRequest("u", "p", NetworkMode.LOCAL, false)

    @Test fun `wrong password emits Failed and closes session`() = runTest {
        val sso = mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.WrongCredentials
        }
        val api = mockk<BitApiClient>(relaxed = true)
        val useCase = SyncGradesUseCase(api, sso, MapGradeUseCase(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            val f = awaitItem() as SyncGradesStep.Failed
            assertTrue(f.err is GradesSyncError.WrongCredentials)
            awaitComplete()
        }
        coVerify(exactly = 1) { api.close() }
    }

    @Test fun `happy path persists and emits Done with rank degraded gracefully`() = runTest {
        val sso = mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.Success
        }
        val grades = Response.success(GradeListResponse(GradeListResponse.Datas(
            cxstuxqcj = GradeListResponse.Rows(rows = listOf(
                GradeRowDto(termCode = "2024-2025-2", termName = "24春", courseName = "高数",
                    courseCode = "M1", credit = 5.0, score = "92", gradePoint = 4.0),
            )))))
        val cjcx = mockk<com.example.personal_studio.data.network.bit.service.BitCjcxService>(relaxed = true) {
            coEvery { getGrades(any(), any(), any(), any()) } returns grades
            // 排名详情失败 → 非致命，rank 留 null
            coEvery { getRankDetail(any()) } returns Response.error(500,
                okhttp3.ResponseBody.Companion.create(null, ""))
        }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.cjcx } returns cjcx }
        val replacer = mockk<ReplaceGradesUseCase>(relaxed = true)
        val useCase = SyncGradesUseCase(api, sso, MapGradeUseCase(), replacer)

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue(awaitItem() is SyncGradesStep.FetchingRanks)
            assertTrue(awaitItem() is SyncGradesStep.Persisting)
            val done = awaitItem() as SyncGradesStep.Done
            assertTrue(done.courseCount == 1)
            awaitComplete()
        }
        coVerify(exactly = 1) { replacer.invoke(any(), any()) }
        coVerify(exactly = 1) { api.close() }
    }

    @Test fun `empty grades emits Failed-EmptyGrades`() = runTest {
        val sso = mockk<com.example.personal_studio.domain.bitimport.SsoLoginUseCase> {
            coEvery { this@mockk.invoke(any(), any(), any()) } returns CasLoginDto.Success
        }
        val empty = Response.success(GradeListResponse(GradeListResponse.Datas(
            cxstuxqcj = GradeListResponse.Rows(rows = emptyList()))))
        val cjcx = mockk<com.example.personal_studio.data.network.bit.service.BitCjcxService>(relaxed = true) {
            coEvery { getGrades(any(), any(), any(), any()) } returns empty
        }
        val api = mockk<BitApiClient>(relaxed = true) { coEvery { this@mockk.cjcx } returns cjcx }
        val useCase = SyncGradesUseCase(api, sso, MapGradeUseCase(), mockk(relaxed = true))

        useCase.sync(req()).test {
            assertTrue(awaitItem() is SyncGradesStep.LoggingIn)
            assertTrue(awaitItem() is SyncGradesStep.FetchingGrades)
            assertTrue((awaitItem() as SyncGradesStep.Failed).err is GradesSyncError.EmptyGrades)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGradesUseCaseTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.core.util.GpaCalculator
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.data.local.db.entity.TermRankEntity
import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import javax.inject.Inject

/**
 * 成绩同步编排。复用 P5 的 SsoLoginUseCase + BitApiClient。两步拉取：
 * 成绩列表(致命失败)→ 每学期排名详情(非致命，失败则 rank 留 null)。始终 close()。
 */
class SyncGradesUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
    private val mapper: MapGradeUseCase,
    private val replacer: ReplaceGradesUseCase,
) {
    fun sync(req: GradesSyncRequest): Flow<SyncGradesStep> = flow {
        try {
            apiClient.open(req.networkMode)

            emit(SyncGradesStep.LoggingIn)
            val login = ssoLogin.invoke(apiClient, req.username, req.password)
            login.toGradesError()?.let { emit(SyncGradesStep.Failed(it)); return@flow }

            // cjcx warm-up（每个 ehall app 各需一次，否则 403）
            apiClient.cjcx.getIndex()
            apiClient.cjcx.getAppConfig()
            apiClient.cjcx.switchLang()

            emit(SyncGradesStep.FetchingGrades)
            val gradeResp = apiClient.cjcx.getGrades()
            val rows = gradeResp.body()?.datas?.cxstuxqcj?.rows ?: emptyList()
            if (rows.isEmpty()) { emit(SyncGradesStep.Failed(GradesSyncError.EmptyGrades)); return@flow }
            val now = System.currentTimeMillis()
            val entries = rows.mapNotNull { mapper.invoke(it, now) }
            if (entries.isEmpty()) { emit(SyncGradesStep.Failed(GradesSyncError.EmptyGrades)); return@flow }

            emit(SyncGradesStep.FetchingRanks)
            val ranks = buildTermRanks(entries, now)

            emit(SyncGradesStep.Persisting)
            replacer.invoke(entries, ranks)

            val termCount = entries.map { it.termCode }.distinct().size
            emit(SyncGradesStep.Done(termCount, entries.size))
        } catch (io: IOException) {
            emit(SyncGradesStep.Failed(GradesSyncError.NetworkFail(io)))
        } catch (e: Throwable) {
            emit(SyncGradesStep.Failed(GradesSyncError.Unexpected(e)))
        } finally {
            apiClient.close()
        }
    }

    /** 每学期一条 TermRankEntity（weightedGpa 必有；排名 best-effort）+ OVERALL 一条。 */
    private suspend fun buildTermRanks(
        entries: List<GradeEntryEntity>, now: Long,
    ): List<TermRankEntity> {
        val byTerm = entries.groupBy { it.termCode }
        val rows = byTerm.map { (code, list) ->
            val gpa = GpaCalculator.weightedGpa(list.map { it.credit to it.gradePoint })
            val detail = runCatching {
                apiClient.cjcx.getRankDetail("""{"XNXQDM":"$code"}""")
                    .takeIf { it.isSuccessful }?.body()?.datas?.cxstupm?.rows?.firstOrNull()
            }.getOrNull()
            TermRankEntity(
                termCode = code, termName = list.first().termName, weightedGpa = gpa,
                classRank = detail?.classRank, classTotal = detail?.classTotal,
                majorRank = detail?.majorRank, majorTotal = detail?.majorTotal, fetchedAt = now,
            )
        }
        val overallGpa = GpaCalculator.weightedGpa(entries.map { it.credit to it.gradePoint })
        val overall = TermRankEntity("OVERALL", "总计", overallGpa, null, null, null, null, now)
        return rows + overall
    }

    private fun CasLoginDto.toGradesError(): GradesSyncError? = when (this) {
        CasLoginDto.Success -> null
        CasLoginDto.WrongCredentials -> GradesSyncError.WrongCredentials
        CasLoginDto.AccountLocked -> GradesSyncError.AccountLocked
        CasLoginDto.CaptchaRequired -> GradesSyncError.CaptchaRequired
        is CasLoginDto.UnknownFailure -> GradesSyncError.ParseFail("CAS: $body")
    }
}
```

> 测试里 `getGrades(any(),any(),any(),any())` 对应 4 个 `@Field` 参数（querySetting/pageSize/pageNumber/order）。若你调整了 service 形参个数，同步改测试的 `coEvery`。

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGradesUseCaseTest*"`
Expected: PASS（3 用例）

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "p6(domain): SyncGradesUseCase 两步拉取编排 + 排名降级 + 单测"
```

---

## Phase M1-E · AI 分析

### Task 13: `BuildGradeSummaryUseCase`（脱敏摘要）

**Files:**
- Create: `domain/bitgrades/BuildGradeSummaryUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/BuildGradeSummaryUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.bitgrades.model.TermRank
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGradeSummaryUseCaseTest {
    @Test fun `summary contains gpa terms courses and flags, no PII`() {
        val book = GradeBook(
            terms = listOf(TermGrades("2024-2025-2", "24春",
                courses = listOf(
                    GradeItem("高数", "M1", 5.0, "92", 4.0, "A", "必修", "正常", true),
                    GradeItem("物理", "P1", 4.0, "55", 0.0, "F", "必修", "正常", false),
                ),
                weightedGpa = 2.22, rank = TermRank(5, 32, 18, 120))),
            overallGpa = 2.22, totalCredits = 9.0,
            overallRank = TermRank(null, null, 18, 120),
        )
        val s = BuildGradeSummaryUseCase().invoke(book)
        assertTrue("高数" in s && "物理" in s)
        assertTrue("挂科" in s)            // 不及格标记
        assertTrue("专业" in s)            // 排名
        assertTrue("GPA" in s)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*BuildGradeSummaryUseCaseTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.domain.bitgrades.model.GradeBook
import javax.inject.Inject
import java.util.Locale

/** GradeBook → 紧凑脱敏文本（无姓名/学号）。AI 报告 prompt 与聊天 seed 共用。 */
class BuildGradeSummaryUseCase @Inject constructor() {
    fun invoke(book: GradeBook): String = buildString {
        appendLine("总GPA=${fmt(book.overallGpa)} 总学分=${fmt1(book.totalCredits)}")
        book.overallRank?.majorPercentile?.let { appendLine("专业排名约 前${it}%") }
        book.terms.forEach { t ->
            val rankStr = t.rank?.let { r ->
                buildString {
                    r.classRank?.let { append(" 班级$it/${r.classTotal}") }
                    r.majorRank?.let { append(" 专业$it/${r.majorTotal}") }
                }
            }.orEmpty()
            appendLine("【${t.termName}】GPA=${fmt(t.weightedGpa)}$rankStr")
            t.courses.forEach { c ->
                append("- ${c.courseName} 学分${fmt1(c.credit)} 成绩${c.score}")
                c.gradePoint?.let { append(" 绩点${fmt(it)}") }
                if (!c.isPass) append(" [挂科]")
                if (c.attemptType != "正常") append(" [${c.attemptType}]")
                appendLine()
            }
        }
    }
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)
}
```

- [ ] **Step 4: 运行 → 通过** + **Commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*BuildGradeSummaryUseCaseTest*"
git add -A && git commit -m "p6(ai): BuildGradeSummaryUseCase 脱敏成绩摘要 + 单测"
```

---

### Task 14: `AnalyzeGradesUseCase`（流式 AI 报告）

**Files:**
- Create: `domain/bitgrades/AnalyzeGradesUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/AnalyzeGradesUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**（mock LLMProvider 的 `generate` Flow）

```kotlin
package com.example.personal_studio.domain.bitgrades

import app.cash.turbine.test
import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzeGradesUseCaseTest {
    @Test fun `streams text deltas from llm`() = runTest {
        val llm = mockk<LLMProvider> {
            every { generate(any(), any()) } returns flowOf(
                LlmChunk.Text("你的"), LlmChunk.Text("趋势向好"), LlmChunk.Done("m"),
            )
        }
        val book = GradeBook(emptyList(), 3.5, 30.0, null)
        val out = StringBuilder()
        AnalyzeGradesUseCase(llm, BuildGradeSummaryUseCase()).invoke(book).test {
            out.append(awaitItem()); out.append(awaitItem()); awaitComplete()
        }
        assertEquals("你的趋势向好", out.toString())
    }
}
```

> 确认 `LlmChunk` 的成员：`LlmChunk.Text(delta)` / `LlmChunk.Done(model)` / `LlmChunk.Error(message, retryable)`（见 `SendMessageUseCase`）。`generate` 签名：`generate(messages, temperature=0.7f)`。

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*AnalyzeGradesUseCaseTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.remote.llm.LLMProvider
import com.example.personal_studio.data.remote.llm.LlmChunk
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** 流式 AI 学情报告。emit 文本增量；出错抛异常由 ViewModel 捕获。 */
class AnalyzeGradesUseCase @Inject constructor(
    private val llm: LLMProvider,
    private val buildSummary: BuildGradeSummaryUseCase,
) {
    fun invoke(book: GradeBook): Flow<String> = flow {
        val summary = buildSummary.invoke(book)
        llm.generate(
            messages = listOf(
                LlmMessage(LlmRole.SYSTEM, SYSTEM_PROMPT),
                LlmMessage(LlmRole.USER, summary),
            ),
        ).collect { chunk ->
            when (chunk) {
                is LlmChunk.Text -> emit(chunk.delta)
                is LlmChunk.Error -> throw RuntimeException(chunk.message)
                is LlmChunk.Done -> {}
            }
        }
    }

    companion object {
        const val SYSTEM_PROMPT = """你是嵌在终端风学习 App 里的学业分析助手。基于给定成绩单数据输出一份简洁报告，分四部分，用 markdown 小标题：
## 趋势
## 强项
## 弱项
## 建议
要求：中文、具体、可执行；点名具体课程/学期作证据；不要寒暄、不要客套结尾；不编造数据里没有的信息。"""
    }
}
```

- [ ] **Step 4: 运行 → 通过** + **Commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*AnalyzeGradesUseCaseTest*"
git add -A && git commit -m "p6(ai): AnalyzeGradesUseCase 流式学情报告 + 单测"
```

---

### Task 15: `StartGradeChatUseCase`（建会话跳聊天）

**Files:**
- Create: `domain/bitgrades/StartGradeChatUseCase.kt`
- Test: `app/src/test/java/com/example/personal_studio/domain/bitgrades/StartGradeChatUseCaseTest.kt`

- [ ] **Step 1: 写失败测试**（mock ChatRepository）

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.domain.model.MessageRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StartGradeChatUseCaseTest {
    @Test fun `creates session and seeds system context`() = runTest {
        val repo = mockk<ChatRepository> {
            coEvery { createSession(any()) } returns 42L
            coEvery { appendMessage(any(), any(), any(), any(), any(), any(), any()) } returns 1L
        }
        val book = GradeBook(
            terms = listOf(TermGrades("2024-2025-2", "24春", emptyList(), 3.5, null)),
            overallGpa = 3.5, totalCredits = 30.0, overallRank = null,
        )
        val sid = StartGradeChatUseCase(repo, BuildGradeSummaryUseCase()).invoke(book)
        assertEquals(42L, sid)
        coVerify { repo.createSession(match { it.contains("成绩") }) }
        coVerify { repo.appendMessage(42L, MessageRole.SYSTEM, any(), null, any(), any(), any()) }
    }
}
```

> `appendMessage` 有默认参 `generationMs/tokenCount/modelUsed`；mockk 的 `coEvery`/`coVerify` 需列出全部 7 个参数位（用 `any()`）。

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*StartGradeChatUseCaseTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.domain.model.MessageRole
import javax.inject.Inject

/** 建一个预置成绩上下文的聊天会话，返回 sessionId 供导航到 P1 聊天详情。
 *  上下文作为 SYSTEM 消息写入：进入 LLM 上下文，但聊天 UI 隐藏 SYSTEM 消息。 */
class StartGradeChatUseCase @Inject constructor(
    private val chatRepo: ChatRepository,
    private val buildSummary: BuildGradeSummaryUseCase,
) {
    suspend fun invoke(book: GradeBook): Long {
        val title = "成绩分析 · ${book.terms.firstOrNull()?.termName ?: "全部"}"
        val sessionId = chatRepo.createSession(title)
        val ctx = "以下是我的成绩单数据，请基于它回答我后续的问题：\n\n" + buildSummary.invoke(book)
        chatRepo.appendMessage(
            sessionId = sessionId,
            role = MessageRole.SYSTEM,
            content = ctx,
            attachedImagePath = null,
        )
        return sessionId
    }
}
```

> **确认**：聊天 UI（`ChatDetailScreen`）渲染消息列表时跳过 `MessageRole.SYSTEM`。若它会显示 SYSTEM 消息，在该屏的消息过滤处加 `filter { it.role != MessageRole.SYSTEM }`（执行时核对一行，属小改）。

- [ ] **Step 4: 运行 → 通过** + **Commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*StartGradeChatUseCaseTest*"
git add -A && git commit -m "p6(ai): StartGradeChatUseCase 建会话+seed成绩上下文 + 单测"
```

---

## Phase M1-F · 手写 Canvas 图表

### Task 16: `ChartMath`（坐标轴边界，纯函数）

**Files:**
- Create: `core/charts/ChartMath.kt`
- Test: `app/src/test/java/com/example/personal_studio/core/charts/ChartMathTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.core.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test fun `gpa bounds round to half steps within 0_maxPoint`() {
        // 值 3.1..3.9 → 下界 floor 到 3.0、上界 ceil 到 4.0
        assertEquals(3.0 to 4.0, ChartMath.gpaBounds(listOf(3.1, 3.9), 4.0))
    }
    @Test fun `empty returns full scale`() {
        assertEquals(0.0 to 4.0, ChartMath.gpaBounds(emptyList(), 4.0))
    }
    @Test fun `single value still gives non-zero span`() {
        val (lo, hi) = ChartMath.gpaBounds(listOf(3.5), 4.0)
        assertEquals(true, hi > lo)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*ChartMathTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**

```kotlin
package com.example.personal_studio.core.charts

import kotlin.math.ceil
import kotlin.math.floor

object ChartMath {
    /** 给 GPA 类序列算"好看"的 y 轴上下界（0.5 取整，夹在 [0, maxPoint]）。 */
    fun gpaBounds(values: List<Double>, maxPoint: Double = 4.0): Pair<Double, Double> {
        if (values.isEmpty()) return 0.0 to maxPoint
        val rawLo = values.min()
        val rawHi = values.max()
        var lo = (floor(rawLo * 2) / 2).coerceAtLeast(0.0)
        var hi = (ceil(rawHi * 2) / 2).coerceAtMost(maxPoint)
        if (hi <= lo) { lo = (lo - 0.5).coerceAtLeast(0.0); hi = (lo + 1.0).coerceAtMost(maxPoint) }
        return lo to hi
    }
}
```

- [ ] **Step 4: 运行 → 通过** + **Commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*ChartMathTest*"
git add -A && git commit -m "p6(charts): ChartMath y轴边界 + 单测"
```

---

### Task 17: `LineChart`（GPA 趋势折线 Canvas）

**Files:**
- Create: `core/charts/LineChart.kt`

- [ ] **Step 1: 写 composable**（无单测；@Preview 目检）

```kotlin
package com.example.personal_studio.core.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule

/** 单点序列。label 显示在 x 轴下方（如学期短名）。 */
data class LinePoint(val label: String, val value: Double)

@Composable
fun GpaLineChart(
    points: List<LinePoint>,
    modifier: Modifier = Modifier,
    maxPoint: Double = 4.0,
) {
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(start = 28.dp, end = 8.dp, top = 8.dp, bottom = 22.dp)) {
            if (points.isEmpty()) return@Canvas
            val (lo, hi) = ChartMath.gpaBounds(points.map { it.value }, maxPoint)
            val span = (hi - lo).coerceAtLeast(0.001)
            val w = size.width; val h = size.height
            fun x(i: Int) = if (points.size == 1) w / 2 else w * i / (points.size - 1)
            fun y(v: Double) = h - ((v - lo) / span * h).toFloat()

            // 网格线（3 条）
            for (g in 0..2) {
                val gy = h * g / 2
                drawLine(Rule, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            }
            // 折线 + 点（phosphor 辉光：先粗后细两道）
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                val px = x(i); val py = y(p.value)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Phosphor.copy(alpha = 0.25f), style = Stroke(width = 6f))
            drawPath(path, Phosphor, style = Stroke(width = 2f))
            points.forEachIndexed { i, p ->
                drawCircle(Phosphor, radius = 4f, center = Offset(x(i), y(p.value)))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E0C)
@Composable
private fun GpaLineChartPreview() {
    GpaLineChart(points = listOf(
        LinePoint("大一上", 3.0), LinePoint("大一下", 3.4),
        LinePoint("大二上", 3.6), LinePoint("大二下", 3.8),
    ))
}
```

> x 轴标签绘制可在执行时按需补充（用 `drawText(measurer, ...)`）；M1 核心是折线本身。若你想标签，循环里 `drawText(measurer, points[i].label, topLeft=Offset(x(i)-12, h+4))`，注意 measurer 已 `remember`。`FoamDim` 已 import 供标签色。

- [ ] **Step 2: 编译** + **Commit**

```bash
./gradlew :app:compileDebugKotlin
git add -A && git commit -m "p6(charts): GpaLineChart 终端风趋势折线 + preview"
```

---

### Task 18: `BarChart`（成绩分布水平条 Canvas）

**Files:**
- Create: `core/charts/BarChart.kt`

- [ ] **Step 1: 写 composable**

```kotlin
package com.example.personal_studio.core.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.core.util.GradeBucket
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

/** 水平条形分布。F 段用 Carmine(红)，其余 Phosphor(绿)。 */
@Composable
fun GradeBarChart(buckets: List<GradeBucket>, modifier: Modifier = Modifier) {
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, color = FoamMute, modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .fillMaxWidth(fraction = b.count.toFloat() / maxCount)
                        .height(14.dp)
                        .background(if (b.label == "F") Carmine else Phosphor),
                )
                Spacer(Modifier.width(8.dp))
                Text("${b.count}", color = FoamMute, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
```

> `fillMaxWidth(fraction)` 需在能撑满的父级里；外层包一个权重容器即可（GradesScreen 里给定宽度）。条形最小可见宽度可在执行时按需补 `.widthIn(min = 2.dp)`。

- [ ] **Step 2: 编译** + **Commit**

```bash
./gradlew :app:compileDebugKotlin
git add -A && git commit -m "p6(charts): GradeBarChart 成绩分布水平条"
```

---

## Phase M1-G · UI（ViewModel + 屏幕 + 导航）

### Task 19: `GradesSyncViewModel`（同步向导状态机）

**Files:**
- Create: `feature/bitgrades/GradesSyncViewModel.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/bitgrades/GradesSyncViewModelTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.example.personal_studio.feature.bitgrades

import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GradesSyncViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `failed login surfaces error`() = runTest {
        val sync = mockk<SyncGradesUseCase> {
            every { sync(any()) } returns flowOf(
                SyncGradesStep.LoggingIn,
                SyncGradesStep.Failed(GradesSyncError.WrongCredentials),
            )
        }
        val vm = GradesSyncViewModel(sync, mockk(relaxed = true))
        vm.onUsernameChange("u"); vm.onPasswordChange("p")
        vm.onSync()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is GradesSyncError.WrongCredentials)
    }

    @Test fun `done sets done flag`() = runTest {
        val sync = mockk<SyncGradesUseCase> {
            every { sync(any()) } returns flowOf(
                SyncGradesStep.LoggingIn, SyncGradesStep.FetchingGrades,
                SyncGradesStep.Persisting, SyncGradesStep.Done(2, 10),
            )
        }
        val vm = GradesSyncViewModel(sync, mockk(relaxed = true))
        vm.onUsernameChange("u"); vm.onPasswordChange("p"); vm.onSync()
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.done)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesSyncViewModelTest*"`
Expected: FAIL

- [ ] **Step 3: 写实现**（仿 `ImportViewModel`，复用 `ImportCredentialPrefs`）

```kotlin
package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.SyncGradesUseCase
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.GradesSyncRequest
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesSyncUiState(
    val username: String = "",
    val password: String = "",
    val rememberPwd: Boolean = false,
    val networkMode: NetworkMode = NetworkMode.LOCAL,
    val showPassword: Boolean = false,
    val progressSteps: List<String> = emptyList(),
    val syncing: Boolean = false,
    val error: GradesSyncError? = null,
    val done: Boolean = false,
)

@HiltViewModel
class GradesSyncViewModel @Inject constructor(
    private val syncUseCase: SyncGradesUseCase,
    private val credPrefs: ImportCredentialPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GradesSyncUiState())
    val uiState: StateFlow<GradesSyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            credPrefs.observeAll().collect { saved ->
                if (saved != null) _uiState.update {
                    it.copy(username = saved.username, password = saved.password,
                        rememberPwd = true, networkMode = saved.lastMode ?: NetworkMode.LOCAL)
                }
            }
        }
    }

    fun onUsernameChange(s: String) = _uiState.update { it.copy(username = s, error = null) }
    fun onPasswordChange(s: String) = _uiState.update { it.copy(password = s, error = null) }
    fun onRememberToggle(b: Boolean) = _uiState.update { it.copy(rememberPwd = b) }
    fun onNetworkModeChange(m: NetworkMode) = _uiState.update { it.copy(networkMode = m) }
    fun onShowPasswordToggle() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun onDismissError() = _uiState.update { it.copy(error = null) }
    fun onRetry() = _uiState.update { it.copy(error = null, progressSteps = emptyList()) }

    fun onSync() {
        val st = _uiState.value
        val req = GradesSyncRequest(st.username, st.password, st.networkMode, st.rememberPwd)
        viewModelScope.launch {
            syncUseCase.sync(req).collect { step -> _uiState.update { reduce(it, step) } }
        }
    }

    private fun reduce(st: GradesSyncUiState, step: SyncGradesStep): GradesSyncUiState = when (step) {
        SyncGradesStep.LoggingIn -> st.copy(syncing = true, progressSteps = listOf("登录中..."))
        SyncGradesStep.FetchingGrades -> st.copy(progressSteps = st.progressSteps + "拉取成绩")
        SyncGradesStep.FetchingRanks -> st.copy(progressSteps = st.progressSteps + "拉取排名详情")
        SyncGradesStep.Persisting -> st.copy(progressSteps = st.progressSteps + "写入本地")
        is SyncGradesStep.Done -> {
            if (st.rememberPwd) credPrefs.save(st.username, st.password, st.networkMode)
            else credPrefs.clear()
            st.copy(syncing = false, done = true)
        }
        is SyncGradesStep.Failed -> {
            if (step.err is GradesSyncError.WrongCredentials || step.err is GradesSyncError.AccountLocked) {
                credPrefs.clear()
            }
            st.copy(syncing = false, error = step.err)
        }
    }
}
```

> `credPrefs.save/clear` 是同步方法（见 `ImportCredentialPrefs`），可直接在 `reduce` 调用。

- [ ] **Step 4: 运行 → 通过** + **Commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*GradesSyncViewModelTest*"
git add -A && git commit -m "p6(ui): GradesSyncViewModel 同步向导状态机 + 单测"
```

---

### Task 20: `GradesSyncScreen` + Route（复用 P5 wizard 组件）

**Files:**
- Create: `feature/bitgrades/ui/GradesSyncScreen.kt`

- [ ] **Step 1: 写屏幕 + 入口 Route**（复用 `WizardScaffold`、`ErrorBanner` 需要 `ImportError`——本屏改用自有错误渲染，因 `GradesSyncError` 与 `ImportError` 不同类型）

```kotlin
package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.feature.bitgrades.GradesSyncViewModel
import com.example.personal_studio.feature.bitimport.ui.components.WizardScaffold
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun GradesSyncRoute(onClose: () -> Unit, onDone: () -> Unit, vm: GradesSyncViewModel = hiltViewModel()) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(st.done) { if (st.done) onDone() }

    WizardScaffold(
        stepNumber = 1, totalSteps = 1, title = "query-grades",
        onBack = onClose,
        primaryLabel = if (st.syncing) "查询中..." else "查询成绩",
        onPrimary = { if (!st.syncing) vm.onSync() },
        primaryEnabled = st.username.isNotBlank() && st.password.isNotBlank() && !st.syncing,
    ) {
        st.error?.let { GradeErrorBanner(it, onDismiss = vm::onDismissError, onRetry = vm::onRetry) }

        OutlinedTextField(st.username, vm::onUsernameChange, label = { Text("学号") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(st.password, vm::onPasswordChange, label = { Text("密码") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (st.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = vm::onShowPasswordToggle) { Text(if (st.showPassword) "隐藏" else "显示") } })
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(st.rememberPwd, vm::onRememberToggle)
            Text("记住密码（Keystore 加密）", color = Foam)
        }
        Row {
            FilterChip(st.networkMode == NetworkMode.LOCAL, { vm.onNetworkModeChange(NetworkMode.LOCAL) }, { Text("校内") })
            Spacer(Modifier.width(8.dp))
            FilterChip(st.networkMode == NetworkMode.WEBVPN, { vm.onNetworkModeChange(NetworkMode.WEBVPN) }, { Text("校外") })
        }
        if (st.progressSteps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            st.progressSteps.forEach { Text("> $it", color = Phosphor) }
        }
    }
}

@Composable
private fun GradeErrorBanner(err: GradesSyncError, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val (color, text) = when (err) {
        GradesSyncError.WrongCredentials -> Carmine to "密码错误，请重新输入。"
        GradesSyncError.AccountLocked -> Carmine to "账号已锁定，请稍后或改密码后重试。"
        GradesSyncError.CaptchaRequired -> Amber to "需要验证码：请到网页端手动登录一次再重试。"
        is GradesSyncError.NetworkFail -> Carmine to "网络异常，请检查网络后重试。"
        is GradesSyncError.ParseFail -> Carmine to "数据解析失败：${err.message}（可能接口改版）。"
        GradesSyncError.EmptyGrades -> Amber to "教务系统暂无成绩数据。"
        is GradesSyncError.Unexpected -> Carmine to (err.cause.message ?: "未知错误。")
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(text, color = color)
        Row {
            TextButton(onRetry) { Text("[重试]", color = color) }
            Spacer(Modifier.weight(1f))
            TextButton(onDismiss) { Text("[关闭]", color = color) }
        }
    }
}
```

- [ ] **Step 2: 编译** + **Commit**

```bash
./gradlew :app:compileDebugKotlin
git add -A && git commit -m "p6(ui): GradesSyncScreen 复用 WizardScaffold 的成绩查询向导"
```

---

### Task 21: `GradesViewModel` + `GradesScreen` + 组件

**Files:**
- Create: `feature/bitgrades/GradesViewModel.kt`
- Create: `feature/bitgrades/ui/components/GpaOverviewCard.kt`
- Create: `feature/bitgrades/ui/components/TermGradeSection.kt`
- Create: `feature/bitgrades/ui/GradesScreen.kt`
- Create: `feature/bitgrades/ui/AiAnalysisSheet.kt`
- Test: `app/src/test/java/com/example/personal_studio/feature/bitgrades/GradesViewModelTest.kt`

- [ ] **Step 1: 写 `GradesViewModel` 失败测试**

```kotlin
package com.example.personal_studio.feature.bitgrades

import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.data.local.db.entity.GradeEntryEntity
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GradesViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `book reflects dao grades`() = runTest {
        val dao = mockk<GradesDao> {
            every { observeAll() } returns flowOf(listOf(
                GradeEntryEntity(0,"2024-2025-2","24春","高数","M1",5.0,"92",4.0,"A","必修","正常",true,1L)))
            every { observeRanks() } returns flowOf(emptyList())
        }
        val vm = GradesViewModel(dao, ComputeGpaUseCase(), mockk(relaxed = true), mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.book.terms.size)
        assertEquals(4.0, vm.uiState.value.book.overallGpa, 0.001)
    }
}
```

- [ ] **Step 2: 运行 → 失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesViewModelTest*"`
Expected: FAIL

- [ ] **Step 3: 写 `GradesViewModel`**

```kotlin
package com.example.personal_studio.feature.bitgrades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.local.db.dao.GradesDao
import com.example.personal_studio.domain.bitgrades.AnalyzeGradesUseCase
import com.example.personal_studio.domain.bitgrades.ComputeGpaUseCase
import com.example.personal_studio.domain.bitgrades.StartGradeChatUseCase
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradesUiState(
    val book: GradeBook = GradeBook(emptyList(), 0.0, 0.0, null),
    val analysis: String = "",
    val analyzing: Boolean = false,
    val analysisError: String? = null,
)

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val dao: GradesDao,
    private val computeGpa: ComputeGpaUseCase,
    private val analyze: AnalyzeGradesUseCase,
    private val startChat: StartGradeChatUseCase,
) : ViewModel() {

    private val _local = MutableStateFlow(GradesUiState())

    val uiState: StateFlow<GradesUiState> = combine(
        dao.observeAll(), dao.observeRanks(), _local,
    ) { grades, ranks, local ->
        local.copy(book = computeGpa.invoke(grades, ranks))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GradesUiState())

    fun onAnalyze() {
        val book = uiState.value.book
        if (book.isEmpty) return
        _local.update { it.copy(analyzing = true, analysis = "", analysisError = null) }
        viewModelScope.launch {
            try {
                analyze.invoke(book).collect { delta -> _local.update { it.copy(analysis = it.analysis + delta) } }
            } catch (e: Throwable) {
                _local.update { it.copy(analysisError = e.message ?: "分析失败") }
            } finally {
                _local.update { it.copy(analyzing = false) }
            }
        }
    }

    /** 建会话并回调 sessionId 供导航。 */
    fun onAskInChat(onReady: (Long) -> Unit) {
        val book = uiState.value.book
        if (book.isEmpty) return
        viewModelScope.launch { onReady(startChat.invoke(book)) }
    }
}
```

> 注意 `combine` 与 `_local` 合并：`analysis`/`analyzing` 来自 `_local`，`book` 来自 dao。上方 `combine` 的 lambda 用 `local.copy(book=...)` 已正确合流。

- [ ] **Step 4: 运行 → 通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*GradesViewModelTest*"`
Expected: PASS

- [ ] **Step 5: 写 `GpaOverviewCard`**

```kotlin
package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

@Composable
fun GpaOverviewCard(book: GradeBook, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("总 GPA", String.format(Locale.US, "%.2f", book.overallGpa))
        Stat("总学分", String.format(Locale.US, "%.1f", book.totalCredits))
        Stat("专业排名", book.overallRank?.majorPercentile?.let { "前 $it%" } ?: "—")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, color = Phosphor, style = MaterialTheme.typography.titleLarge)
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium)
    }
}
```

- [ ] **Step 6: 写 `TermGradeSection`**（含挂科/重修高亮——功能 D）

```kotlin
package com.example.personal_studio.feature.bitgrades.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.util.Locale

@Composable
fun TermGradeSection(term: TermGrades, initiallyExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Text(if (expanded) "▾ ${term.termName}" else "▸ ${term.termName}", color = Phosphor)
            Spacer(Modifier.weight(1f))
            Text("GPA ${String.format(Locale.US, "%.2f", term.weightedGpa)}", color = FoamMute)
            term.rank?.let { r ->
                r.majorRank?.let { Text("  专业$it/${r.majorTotal}", color = FoamMute) }
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                term.courses.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp)) {
                        Text(c.courseName, color = if (!c.isPass) Carmine else Foam,
                            modifier = Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1f", c.credit), color = FoamMute,
                            modifier = Modifier.width(40.dp))
                        Text(c.score, color = if (!c.isPass) Carmine else Foam,
                            modifier = Modifier.width(48.dp))
                        if (!c.isPass) Text("⚠挂科", color = Carmine)
                        else if (c.attemptType != "正常") Text(c.attemptType, color = Amber)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: 写 `AiAnalysisSheet`**

```kotlin
package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.Phosphor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisSheet(
    text: String,
    analyzing: Boolean,
    error: String?,
    onAskInChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("$ ai-analysis", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = Carmine) }
            Text(text.ifBlank { if (analyzing) "分析中..." else "" }, color = Foam,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
            Spacer(Modifier.height(12.dp))
            if (!analyzing && text.isNotBlank()) {
                Button(onAskInChat, Modifier.fillMaxWidth()) { Text("在聊天里追问 →") }
            }
        }
    }
}
```

- [ ] **Step 8: 写 `GradesScreen`**（拼装）

```kotlin
package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.charts.GpaLineChart
import com.example.personal_studio.core.charts.GradeBarChart
import com.example.personal_studio.core.charts.LinePoint
import com.example.personal_studio.core.util.GradeBucketer
import com.example.personal_studio.feature.bitgrades.GradesViewModel
import com.example.personal_studio.feature.bitgrades.ui.components.GpaOverviewCard
import com.example.personal_studio.feature.bitgrades.ui.components.TermGradeSection
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun GradesScreen(
    onBack: () -> Unit,
    onSync: () -> Unit,
    onOpenChat: (Long) -> Unit,
    vm: GradesViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Void).statusBarsPadding().padding(16.dp)
        .verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ transcript", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onSync) { Text("↻ 同步") }
        }

        if (st.book.isEmpty) {
            Spacer(Modifier.height(48.dp))
            Text("还没有成绩数据", color = FoamMute)
            Button(onSync, Modifier.padding(top = 12.dp)) { Text("从教务系统查询成绩") }
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        GpaOverviewCard(st.book)

        // GPA 趋势（学期正序：旧→新）
        Spacer(Modifier.height(20.dp))
        Text("GPA 趋势", color = FoamMute)
        val points = st.book.terms.reversed().map { LinePoint(shortTerm(it.termName), it.weightedGpa) }
        GpaLineChart(points)

        // 成绩分布（全部课程）
        Spacer(Modifier.height(20.dp))
        Text("成绩分布", color = FoamMute)
        val allCourses = st.book.terms.flatMap { it.courses }
        GradeBarChart(GradeBucketer.bucket(allCourses))

        // 分学期列表
        Spacer(Modifier.height(20.dp))
        st.book.terms.forEachIndexed { i, t -> TermGradeSection(t, initiallyExpanded = i == 0) }

        Spacer(Modifier.height(20.dp))
        Button({ showSheet = true; vm.onAnalyze() }, Modifier.fillMaxWidth()) { Text("生成 AI 分析") }
    }

    if (showSheet) {
        AiAnalysisSheet(
            text = st.analysis, analyzing = st.analyzing, error = st.analysisError,
            onAskInChat = { vm.onAskInChat { sid -> showSheet = false; onOpenChat(sid) } },
            onDismiss = { showSheet = false },
        )
    }
}

/** "2024-2025学年 第二学期" → "24-25下" 之类的短名（尽力而为）。 */
private fun shortTerm(name: String): String =
    name.replace("学年", "").replace("第一学期", "上").replace("第二学期", "下")
        .replace(" ", "").take(8)
```

- [ ] **Step 9: 编译** + **Commit**

```bash
./gradlew :app:compileDebugKotlin
git add -A && git commit -m "p6(ui): GradesViewModel + GradesScreen + 概览/学期/AI sheet + 挂科高亮"
```

---

### Task 22: 导航 + Settings 入口

**Files:**
- Modify: `ui/navigation/NavRoutes.kt`
- Modify: `ui/AppNavHost.kt`
- Modify: `feature/settings/ui/SettingsScreen.kt`

- [ ] **Step 1: 加路由常量**

`NavRoutes.kt` 在 `IMPORT_WIZARD` 之后加：

```kotlin
    // BIT 成绩查询 (P6)
    const val GRADES = "grades"
    const val GRADES_SYNC = "grades/sync"
```

- [ ] **Step 2: 注册路由**

`AppNavHost.kt` 在 `composable(NavRoutes.IMPORT_WIZARD){...}` 之后加：

```kotlin
        composable(NavRoutes.GRADES) {
            com.example.personal_studio.feature.bitgrades.ui.GradesScreen(
                onBack = { navController.popBackStack() },
                onSync = { navController.navigate(NavRoutes.GRADES_SYNC) },
                onOpenChat = { sid -> navController.navigate(NavRoutes.chatDetail(sid)) },
            )
        }
        composable(NavRoutes.GRADES_SYNC) {
            com.example.personal_studio.feature.bitgrades.ui.GradesSyncRoute(
                onClose = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
```

- [ ] **Step 3: 加 Settings 入口行**

`SettingsScreen.kt` 在 `key = "IMPORT"` 的 `NavigableRowWithSubtitle` 之后加：

```kotlin
            NavigableRowWithSubtitle(
                key = "GRADES",
                value = "从教务系统查询成绩 →",
                subtitle = "成绩单 · 可视化 · AI 分析",
                onClick = { onNavigate(com.example.personal_studio.ui.navigation.NavRoutes.GRADES) },
            )
```

- [ ] **Step 4: 编译 + 全量单测**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL；所有既有 + 新增单测通过

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "p6(nav): GRADES/GRADES_SYNC 路由 + Settings 成绩查询入口"
```

---

## Phase M1-H · 真机验证（DoD）

### Task 23: 真机抓包验证 + 修正协议 + 落地脱敏 fixture

> 这是 M1 的关键收尾。前面所有 cjcx 字段/端点/appId 都是**假设**。本任务在真机上跑通真实账号，用 logcat 抓真实响应，对照修正，并把脱敏后的真实 JSON 固化为 fixture。

**Files:**
- Modify（按真机发现）: `BitCjcxService.kt`（APP_ID / 路径 / 表单参数）、`GradeRowDto.kt` / `GradeRankDto.kt`（`@SerialName`）、必要时 `MapGradeUseCase.kt`
- Replace: `cjcx-grades-sample.json` / `cjcx-rank-sample.json`（换成脱敏的真实响应）

- [ ] **Step 1: 临时开 BODY 日志**

`core/di/BitNetworkModule.kt` 把 `HttpLoggingInterceptor` 临时设为 `Level.BODY`（**仅本地调试，提交前必须改回 BASIC**——BODY 会打印加密密码与 Set-Cookie）。

- [ ] **Step 2: 装机 + 实操**

Run: `./gradlew :app:installDebug`
在设备上：Settings → 从教务系统查询成绩 → 输入真账号 → 查询。

- [ ] **Step 3: 抓日志**

Run: `adb logcat -d -s okhttp.OkHttpClient:V > cjcx-capture.txt`
检查：warm-up 是否 200（appId 对不对，否则 403）；`cxstuxqcj.do` 与 `cxstupm.do` 路径是否存在；成绩/排名 JSON 的真实 key 名；`CJ` 是字符串还是数字。

- [ ] **Step 4: 对照修正**

逐项核对并修正：
- `BitCjcxService.APP_ID` 与各 `.do` 路径、`getGrades` 表单参数（querySetting/pageSize/order 的真实形态）
- `GradeRowDto`/`GradeRankDto` 的 `@SerialName`（KCM/XF/CJ/JD/BJPM/ZYPM…）
- 若 `CJ` 是 JSON number：把 `score` 改为读 `JsonElement`/`Double` 或加宽松序列化（`Json { isLenient = true }` 不够——number→String 仍报错，需自定义 serializer 或把字段设 `Double?` 再 `toString`）
- 若成绩接口只支持逐学期：在 `SyncGradesUseCase` 先 `getTerms` 再循环 `getGrades(term)`（service 增 `getTerms`，逐学期合并 rows）

- [ ] **Step 5: 脱敏并替换 fixture**

把抓到的真实成绩/排名 JSON 脱敏（移除学号/姓名样式串，课程可保留），替换两个 fixture 文件，重跑 `BitCjcxServiceTest` 确认仍通过（字段已对齐真实）。

- [ ] **Step 6: 关 BODY 日志**

把 `BitNetworkModule` 的日志级别改回 `Level.BASIC`。**确认 committed code 无 BODY**。

- [ ] **Step 7: DoD 勾验**（见下方清单）逐项真机过一遍。

- [ ] **Step 8: 全量单测 + Commit**

```bash
./gradlew :app:testDebugUnitTest
git add -A && git commit -m "p6(grades): 真机验证 cjcx 协议，修正字段/端点 + 脱敏 fixture，M1 闭环"
```

---

## Definition of Done（M1，真机）

- [ ] Settings →「从教务系统查询成绩」打开；空库显示 CTA
- [ ] 校内真账号端到端：登录 → 拉成绩 → 拉排名 → 落库 → 成绩单显示概览/趋势/分布/分学期列表
- [ ] 挂科（不及格）红色高亮；重修标记正确
- [ ] GPA 趋势折线、成绩分布柱状用 Canvas 正确渲染（多学期）
- [ ] 排名详情拿不到时优雅降级（成绩照常，排名/百分比显示"—"）
- [ ] 「生成 AI 分析」流式输出四段报告；「在聊天里追问」跳 P1 且模型能引用成绩上下文
- [ ] 密码错误 → WrongCredentials banner + 清 Keystore；记住密码下次预填
- [ ] 抓取的脱敏 fixture 已 commit；`BitCjcxServiceTest` 对齐真实字段后通过
- [ ] committed code 日志级别为 BASIC（非 BODY）
- [ ] 所有既有单测 + 新增 P6 单测全绿

---

## 后续里程碑（各自独立 plan）

- **M2 What-if 计算器**：`GpaCalculator.requiredAverageForTarget`/`project` + `GpaPlannerUseCase` + `WhatIfScreen`（纯本地，单测重）。M1 合并、协议已验证后再写 plan。
- **M3 分享卡片**：`ShareCardScreen` + `rememberGraphicsLayer().toImageBitmap()` + FileProvider 分享。
- **M4 出分提醒**：`GradesSyncPrefs` + `DetectNewGradesUseCase` + `GradePollWorker`(HiltWorker, WorkManager) + `GradesNotifier` + `NotificationChannels.GRADES_ID` + Settings 开关。**强依赖 M1 真机验证后的 cjcx 协议**，故必须在 M1 闭环后再写。

---

## Self-Review 备注（已核对）

- **Spec 覆盖**：成绩两步拉取(Task 1-2,12)、Room 落库(Task 4-5)、GPA/分布(Task 6,9,10)、Canvas 图表(Task 16-18)、AI 报告+追问(Task 13-15,21)、挂科高亮(Task 21 TermGradeSection)、入口(Task 22)、真机 DoD(Task 23) 均有任务。M2-M4 明确拆为独立 plan。
- **类型一致**：`GradeBook/TermGrades/GradeItem/TermRank`(Task 7) 贯穿 Task 10/13/15/21；`SyncGradesStep/GradesSyncError`(Task 11) 贯穿 Task 12/19；`GradeBucket`(Task 9) 用于 Task 18；`LinePoint`(Task 17) 用于 Task 21；`ImportCredentialPrefs.save/clear/observeAll` 签名与既有一致。
- **占位**：cjcx `APP_ID`/端点/字段是**显式标注的协议假设**（非遗漏占位），Task 23 真机修正——这是本项目既定方法论（P5 同款），不可在写代码前消除。
- **依赖顺序提醒**：Task 1 的 `getRankDetail` 引用 Task 2 的 `GradeRankResponse`，已在 Task 1 注明先注释、Task 2 取消注释。
