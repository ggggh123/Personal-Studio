# P3 · Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Knowledge Base feature — let users archive chat replies (per-message or per-session) and scanner pages into structured KB entries (5-section knowledge summary + optional standardized question for photo/scan sources), browse them by category, full-text-search them in Chinese, surface "wrong-questions" (错题集) as a first-class second-level view, and edit / regenerate / delete entries.

**Architecture:** MVVM + Repository, same shape as P1/P2. Domain-layer UseCases orchestrate `KnowledgeRepository` which owns Room tables `kb_categories` + `kb_entries` + `kb_relations` + a `kb_entries_fts` FTS4 shadow table. A `BigramTokenizer` pre-cuts CJK text before write to FTS to side-step the SQLite-ICU/jieba packaging problem. The "Add to KB" flow extends `LLMProvider.generateStructured` to accept `List<LlmMessage>` (so vision is supported), runs JSON parse → 1× retry → fallback, and presents a `SavePreviewModal` where every field is editable before commit. Three source types share one ViewModel: `CHAT_MESSAGE` (per AI bubble), `CHAT_SESSION` (per session), `SCAN` (per scanner page).

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (Terminal theme from P0), Hilt + KSP, Room 2.6+ with FTS4, Kotlin Coroutines + Flow, OkHttp (existing), kotlinx.serialization (existing), KaTeX WebView component (existing from P1).

**Spec reference:** `docs/superpowers/specs/2026-04-25-p3-knowledge-base-design.md` — read first; this plan instantiates it without re-stating rationale.

**Key locked decisions** (per spec §10):
1. Sticks to 2026-04-20 master spec §4.3 direction; this plan refines details only
2. 错题集 = independent second-level entry + specialized detail (image + standardized question + 5-section summary)
3. Single LLM call returns both `standardizedQuestion` (nullable) and 5-section `summaryMarkdown`
4. Add-to-KB triggers from per-message AND per-session AND per-scan-page
5. 7 default categories pre-seeded; AI may propose new ones (user must accept)
6. Full edit suite on detail page: rename / change category / delete / edit summary markdown / regenerate
7. Approach A — execute the full spec end-to-end in 7 phases, ~8.5 days
8. SCAN sourceType retained — scan-page → LLM OCR + summary route
9. Default seed: `数学 / 物理 / 化学 / 生物 / 英语 / 编程 / 其它` (last one is non-deletable fallback)
10. Single-branch workflow (`feature/p3-knowledge`); 7 phases; PR + tag `p3-knowledge-mvp` at end
11. Room v4 → v5, destructive (dev test data is throwaway, per memory `feedback_dev_db_data.md`)

---

## File Structure

### Created

**Data layer — Room**
- `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbCategoryEntity.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbEntryEntity.kt` (incl. `KbSourceType` enum)
- `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbRelationEntity.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbEntryFtsEntity.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/dao/KbCategoryDao.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/dao/KbEntryDao.kt`
- `app/src/main/java/com/example/personal_studio/data/local/db/dao/KbFtsDao.kt`

**Data layer — Repository + KB infra**
- `app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepository.kt` (interface lives here for parity with `ChatRepository.kt` style)
- `app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt`
- `app/src/main/java/com/example/personal_studio/data/kb/KbImageStore.kt` (filesDir/kb/<id>.jpg copy/delete helpers)

**Core**
- `app/src/main/java/com/example/personal_studio/core/bigram/BigramTokenizer.kt`
- `app/src/main/java/com/example/personal_studio/core/di/KnowledgeModule.kt`

**Domain**
- `app/src/main/java/com/example/personal_studio/domain/model/KbModels.kt` (KbEntry / KbCategory / KbEntryDraft / KbDraftSource / KbSource / KbDraftFallbackReason)
- `app/src/main/java/com/example/personal_studio/domain/knowledge/SaveToKnowledgeUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/knowledge/RegenerateEntryUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/knowledge/UpdateEntryUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/knowledge/DeleteEntryUseCase.kt`
- `app/src/main/java/com/example/personal_studio/domain/knowledge/SearchKbUseCase.kt`

**Feature — knowledge**
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbHomeScreen.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbMistakesScreen.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbMistakesViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/SavePreviewModal.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/SaveToKnowledgeViewModel.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbEntryRow.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbMistakeRow.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryChipRow.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/RelatedEntriesSection.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/SummaryMarkdownEditor.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryPickerSheet.kt`
- `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbSearchBar.kt`

**Tests — unit**
- `app/src/test/java/com/example/personal_studio/core/bigram/BigramTokenizerTest.kt`
- `app/src/test/java/com/example/personal_studio/data/repository/FakeKnowledgeRepository.kt`
- `app/src/test/java/com/example/personal_studio/data/remote/llm/FakeLLMProvider.kt` (extend if exists, else create)
- `app/src/test/java/com/example/personal_studio/domain/knowledge/SaveToKnowledgeUseCaseTest.kt`
- `app/src/test/java/com/example/personal_studio/domain/knowledge/RegenerateEntryUseCaseTest.kt`
- `app/src/test/java/com/example/personal_studio/domain/knowledge/UpdateEntryUseCaseTest.kt`
- `app/src/test/java/com/example/personal_studio/domain/knowledge/DeleteEntryUseCaseTest.kt`
- `app/src/test/java/com/example/personal_studio/domain/knowledge/SearchKbUseCaseTest.kt`
- `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModelTest.kt`
- `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbMistakesViewModelTest.kt`
- `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModelTest.kt`
- `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/SaveToKnowledgeViewModelTest.kt`

**Tests — instrumented**
- `app/src/androidTest/java/com/example/personal_studio/data/local/db/KbDaoTest.kt`
- `app/src/androidTest/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImplTest.kt`
- `app/src/androidTest/java/com/example/personal_studio/feature/knowledge/KbHomeScreenTest.kt`
- `app/src/androidTest/java/com/example/personal_studio/feature/knowledge/KbEntryDetailScreenTest.kt`
- `app/src/androidTest/java/com/example/personal_studio/feature/knowledge/SavePreviewModalTest.kt`

### Modified

- `app/src/main/java/com/example/personal_studio/data/local/db/AppDatabase.kt` (v4 → v5 with new entities + DAO accessors + onCreate seed callback)
- `app/src/main/java/com/example/personal_studio/data/local/db/TypeConverters.kt` (add `KbSourceType` ↔ String)
- `app/src/main/java/com/example/personal_studio/core/di/DatabaseModule.kt` (provide new DAOs)
- `app/src/main/java/com/example/personal_studio/data/remote/llm/LLMProvider.kt` (add `generateStructured(messages, schema)` overload as primary; demote old text-only signature to default)
- `app/src/main/java/com/example/personal_studio/data/remote/llm/OpenAiCompatibleProvider.kt` (implement new overload reusing `serializeMessage` + `response_format = json_object`)
- `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt` (add `KB_MISTAKES`, `KB_DETAIL` route + builder)
- `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt` (register knowledge destinations)
- `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt` ([+ archive] under each AI bubble; [+ archive session] in topbar trailing)
- `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt` ([archive page to KB] in long-press menu)
- `app/src/main/java/com/example/personal_studio/feature/scanner/doc/PageEditScreen.kt` ([+ archive] button)
- `app/src/main/java/com/example/personal_studio/ui/placeholder/FeaturePlaceholders.kt` (keep `KnowledgePlaceholder` as empty-state Composable; remove its NavHost wiring)

---

## Phase 0 — Branch + branch hygiene

### Task 0: Create feature branch

**Files:** none

- [ ] **Step 1: Verify clean tree on `main`**

Run: `git status --short`
Expected: only the two untracked files (`29e78a69....png`, `img.jpg`). No staged/modified files.

- [ ] **Step 2: Create + switch to feature branch**

Run: `git checkout -b feature/p3-knowledge`
Expected: `Switched to a new branch 'feature/p3-knowledge'`

- [ ] **Step 3: Verify spec is on the branch**

Run: `git log --oneline -3`
Expected: top commit is `19bbde9 docs(p3): brainstorm-converged spec for Knowledge Base feature`.

---

## Phase 1 — Data Layer

Goal: Room v5 with 4 new tables, DAOs, type converter, BigramTokenizer + tests, KnowledgeRepositoryImpl with full CRUD (no LLM yet), Hilt wiring, default-category seed callback. Build green; tests green; on-device DB inspector shows 7 seeded categories.

### Task 1: Domain models — `KbModels.kt`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/model/KbModels.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.example.personal_studio.domain.model

/** Source category of a KB entry — drives prompt assembly + back-link. */
enum class KbSource { CHAT_MESSAGE, CHAT_SESSION, SCAN }

/** Pure domain category (no Room types). */
data class KbCategory(
    val id: Long,
    val name: String,
    val seeded: Boolean,
)

/** Pure domain entry. `categoryId == null` is allowed; UI groups it under "其它". */
data class KbEntry(
    val id: Long,
    val title: String,
    val categoryId: Long?,
    val categoryName: String?,                  // joined from kb_categories (null when categoryId is null)
    val source: KbSource,
    val sourceChatMessageId: Long?,
    val sourceChatSessionId: Long?,
    val sourceScanPageId: Long?,
    val originalImagePath: String?,
    val standardizedQuestion: String?,          // non-null = belongs to mistakes collection
    val summaryMarkdown: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isMistake: Boolean get() = standardizedQuestion != null
}

/** Where the draft was triggered from. Sealed so Repository pattern-matches it. */
sealed class KbDraftSource {
    data class FromChatMessage(val sessionId: Long, val aiMessageId: Long) : KbDraftSource()
    data class FromChatSession(val sessionId: Long) : KbDraftSource()
    data class FromScanPage(val docId: Long, val pageId: Long) : KbDraftSource()
}

/** Why a draft is in fallback state (used by SavePreviewModal banner). */
enum class KbDraftFallbackReason { JSON_PARSE_FAILED, MISSING_REQUIRED_FIELDS }

/**
 * In-flight draft returned by [draftFromSource]. Either a parsed AI response or a
 * fallback skeleton (when LLM JSON couldn't be parsed twice). User edits in the
 * SavePreviewModal then commits — see KnowledgeRepository.saveEntry.
 */
data class KbEntryDraft(
    val source: KbDraftSource,
    val title: String,
    val categorySuggestion: String,             // resolved against existing categories at commit time
    val standardizedQuestion: String?,          // null when isQuestion=false
    val summaryMarkdown: String,
    val relatedEntryTitles: List<String>,
    val originalImagePath: String?,             // staged copy in filesDir/kb/tmp_<uuid>.jpg
    val isFallback: Boolean = false,
    val fallbackReason: KbDraftFallbackReason? = null,
)
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/model/KbModels.kt
git commit -m "kb(domain): add KbEntry/KbCategory/KbDraft domain models"
```

---

### Task 2: Room entities + `KbSourceType` converter

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbCategoryEntity.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbEntryEntity.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbRelationEntity.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/entity/KbEntryFtsEntity.kt`
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/TypeConverters.kt`

- [ ] **Step 1: Write `KbCategoryEntity`**

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kb_categories",
    indices = [Index("name", unique = true)],
)
data class KbCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val name: String,
    val seeded: Boolean = false,
    val createdAt: Long,
)
```

- [ ] **Step 2: Write `KbEntryEntity` (incl. enum)**

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class KbSourceType { CHAT_MESSAGE, CHAT_SESSION, SCAN }

@Entity(
    tableName = "kb_entries",
    indices = [
        Index("createdAt"),
        Index("categoryId"),
        Index("sourceType"),
    ],
)
data class KbEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long?,
    val sourceType: KbSourceType,
    val sourceChatMessageId: Long?,
    val sourceChatSessionId: Long?,
    val sourceScanPageId: Long?,
    val originalImagePath: String?,
    val standardizedQuestion: String?,
    val summaryMarkdown: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastReviewedAt: Long? = null,
)
```

- [ ] **Step 3: Write `KbRelationEntity`**

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "kb_relations",
    primaryKeys = ["fromEntryId", "toEntryId"],
    foreignKeys = [
        ForeignKey(
            entity = KbEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KbEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["toEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("toEntryId")],
)
data class KbRelationEntity(
    val fromEntryId: Long,
    val toEntryId: Long,
    val weight: Float = 1f,
)
```

- [ ] **Step 4: Write `KbEntryFtsEntity`**

```kotlin
package com.example.personal_studio.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(tokenizer = "unicode61")
@Entity(tableName = "kb_entries_fts")
data class KbEntryFtsEntity(
    @ColumnInfo(name = "rowid") val rowid: Long,
    val titleBigrams: String,
    val summaryBigrams: String,
    val standardizedQuestionBigrams: String,
)
```

- [ ] **Step 5: Extend `TypeConverters`**

Replace the contents of `app/src/main/java/com/example/personal_studio/data/local/db/TypeConverters.kt` with:

```kotlin
package com.example.personal_studio.data.local.db

import androidx.room.TypeConverter
import com.example.personal_studio.data.local.db.entity.KbSourceType
import com.example.personal_studio.data.local.db.entity.MessageRole

class Converters {
    @TypeConverter fun roleToString(role: MessageRole): String = role.name
    @TypeConverter fun stringToRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter fun kbSourceToString(value: KbSourceType): String = value.name
    @TypeConverter fun stringToKbSource(value: String): KbSourceType = KbSourceType.valueOf(value)
}
```

- [ ] **Step 6: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (AppDatabase will fail to register the new entities until Task 5 — that's fine because nothing references them yet.)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/db/entity/KbCategoryEntity.kt app/src/main/java/com/example/personal_studio/data/local/db/entity/KbEntryEntity.kt app/src/main/java/com/example/personal_studio/data/local/db/entity/KbRelationEntity.kt app/src/main/java/com/example/personal_studio/data/local/db/entity/KbEntryFtsEntity.kt app/src/main/java/com/example/personal_studio/data/local/db/TypeConverters.kt
git commit -m "kb(db): add 4 entities + KbSourceType converter"
```

---

### Task 3: DAOs

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/dao/KbCategoryDao.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/dao/KbEntryDao.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/local/db/dao/KbFtsDao.kt`

- [ ] **Step 1: Write `KbCategoryDao`**

```kotlin
package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KbCategoryDao {

    @Query("SELECT * FROM kb_categories ORDER BY seeded DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<KbCategoryEntity>>

    @Query("SELECT * FROM kb_categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): KbCategoryEntity?

    @Query("SELECT * FROM kb_categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): KbCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(c: KbCategoryEntity): Long

    @Query("DELETE FROM kb_categories WHERE id = :id AND seeded = 0")
    suspend fun deleteUnseeded(id: Long): Int
}
```

- [ ] **Step 2: Write `KbEntryDao`**

```kotlin
package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KbEntryDao {

    @Insert
    suspend fun insert(e: KbEntryEntity): Long

    @Update
    suspend fun update(e: KbEntryEntity)

    @Query("DELETE FROM kb_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM kb_entries WHERE id = :id")
    fun observe(id: Long): Flow<KbEntryEntity?>

    @Query("SELECT * FROM kb_entries WHERE id = :id")
    suspend fun get(id: Long): KbEntryEntity?

    @Query(
        """SELECT * FROM kb_entries
           WHERE (:categoryId IS NULL OR categoryId = :categoryId)
             AND (:notesOnly = 0 OR standardizedQuestion IS NULL)
           ORDER BY createdAt DESC""",
    )
    fun observeAll(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntryEntity>>

    @Query(
        """SELECT * FROM kb_entries
           WHERE standardizedQuestion IS NOT NULL
           ORDER BY createdAt DESC""",
    )
    fun observeMistakes(): Flow<List<KbEntryEntity>>

    @Query(
        """SELECT e.* FROM kb_entries e
           JOIN kb_entries_fts f ON f.rowid = e.id
           WHERE kb_entries_fts MATCH :ftsQuery
           ORDER BY e.createdAt DESC""",
    )
    fun searchFlow(ftsQuery: String): Flow<List<KbEntryEntity>>

    @Query(
        """SELECT e.* FROM kb_entries e
           JOIN kb_entries_fts f ON f.rowid = e.id
           WHERE kb_entries_fts MATCH :ftsQuery
           ORDER BY e.createdAt DESC""",
    )
    suspend fun searchOnce(ftsQuery: String): List<KbEntryEntity>

    @Query(
        """SELECT e.* FROM kb_entries e
           JOIN kb_relations r ON r.toEntryId = e.id
           WHERE r.fromEntryId = :id
           ORDER BY r.weight DESC""",
    )
    fun observeRelated(id: Long): Flow<List<KbEntryEntity>>

    @Query("SELECT * FROM kb_entries WHERE title IN (:titles)")
    suspend fun findByTitles(titles: List<String>): List<KbEntryEntity>

    @Query("SELECT title FROM kb_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentTitles(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE standardizedQuestion IS NULL")
    fun countNotes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kb_entries WHERE standardizedQuestion IS NOT NULL")
    fun countMistakes(): Flow<Int>

    @Query("SELECT categoryId AS id, COUNT(*) AS count FROM kb_entries WHERE categoryId IS NOT NULL GROUP BY categoryId")
    fun countByCategory(): Flow<List<CategoryCountRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(rels: List<KbRelationEntity>)

    data class CategoryCountRow(val id: Long, val count: Int)
}
```

- [ ] **Step 3: Write `KbFtsDao`**

```kotlin
package com.example.personal_studio.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity

@Dao
interface KbFtsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fts: KbEntryFtsEntity)

    @Query("DELETE FROM kb_entries_fts WHERE rowid = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 4: Compile-check (will fail at AppDatabase until Task 5 — acceptable here only as a syntax check)**

Run: `./gradlew :app:compileDebugKotlin -x kspDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/db/dao/KbCategoryDao.kt app/src/main/java/com/example/personal_studio/data/local/db/dao/KbEntryDao.kt app/src/main/java/com/example/personal_studio/data/local/db/dao/KbFtsDao.kt
git commit -m "kb(db): add KbCategoryDao + KbEntryDao + KbFtsDao"
```

---

### Task 4: BigramTokenizer with TDD

**Files:**
- Create: `app/src/test/java/com/example/personal_studio/core/bigram/BigramTokenizerTest.kt`
- Create: `app/src/main/java/com/example/personal_studio/core/bigram/BigramTokenizer.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.personal_studio.core.bigram

import org.junit.Assert.assertEquals
import org.junit.Test

class BigramTokenizerTest {

    @Test fun `index empty string yields empty`() {
        assertEquals("", BigramTokenizer.tokenizeForIndex(""))
    }

    @Test fun `index single CJK char yields the char`() {
        assertEquals("数", BigramTokenizer.tokenizeForIndex("数"))
    }

    @Test fun `index two-char CJK yields one bigram`() {
        assertEquals("数学", BigramTokenizer.tokenizeForIndex("数学"))
    }

    @Test fun `index four-char CJK yields three sliding bigrams`() {
        assertEquals("微积 积分 分极", BigramTokenizer.tokenizeForIndex("微积分极"))
    }

    @Test fun `index ASCII word is lowercased and kept whole`() {
        assertEquals("compose", BigramTokenizer.tokenizeForIndex("Compose"))
    }

    @Test fun `index mixed language splits on whitespace then per-chunk strategy`() {
        // "Compose 状态恢复" -> ASCII chunk "compose" + CJK bigrams of 状态恢复 (3 bigrams).
        assertEquals("compose 状态 态恢 恢复", BigramTokenizer.tokenizeForIndex("Compose 状态恢复"))
    }

    @Test fun `index treats commas and full-width punctuation as separators`() {
        assertEquals("数学 物理", BigramTokenizer.tokenizeForIndex("数学，物理"))
    }

    @Test fun `query empty returns empty`() {
        assertEquals("", BigramTokenizer.tokenizeForQuery(""))
    }

    @Test fun `query single CJK char wraps quoted`() {
        assertEquals("\"数\"", BigramTokenizer.tokenizeForQuery("数"))
    }

    @Test fun `query two-char CJK is one quoted bigram`() {
        assertEquals("\"数学\"", BigramTokenizer.tokenizeForQuery("数学"))
    }

    @Test fun `query CJK chunk joins bigrams with implicit AND (space)`() {
        assertEquals("\"微积\" \"积分\"", BigramTokenizer.tokenizeForQuery("微积分"))
    }

    @Test fun `query multiple chunks join with implicit AND (space)`() {
        assertEquals("\"微积\" \"积分\" \"极限\"", BigramTokenizer.tokenizeForQuery("微积分 极限"))
    }

    @Test fun `query ASCII word is lowercased and quoted`() {
        assertEquals("\"compose\"", BigramTokenizer.tokenizeForQuery("Compose"))
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.core.bigram.BigramTokenizerTest"`
Expected: FAILED — `Unresolved reference: BigramTokenizer`.

- [ ] **Step 3: Write `BigramTokenizer`**

```kotlin
package com.example.personal_studio.core.bigram

/**
 * Pre-tokenizes text for the FTS4 shadow table without needing a CJK tokenizer.
 *
 * Strategy:
 *  - Split by whitespace + punctuation into chunks.
 *  - ASCII chunks (`code < 128`) are lowercased and kept whole.
 *  - CJK chunks are split into sliding bigrams (length 1 → kept as the single char).
 *
 * The query side joins per-chunk bigrams with space-separated implicit AND, since
 * stock Android SQLite FTS3/4 doesn't support the literal `AND` keyword (it's built
 * without `SQLITE_ENABLE_FTS3_PARENTHESIS`, so only standard query syntax is
 * available — space = implicit AND, the `OR` keyword IS supported, but `AND`/`NOT`/
 * parentheses are not). Semantically the result is the same: a 4-char term like
 * 微积分极 must have all three bigrams 微积, 积分, 分极 present in the document.
 * Cross-chunk noise is mitigated by encouraging users to add spaces between
 * conceptual terms; the ViewModel layer additionally falls back to OR when the
 * implicit-AND query returns < 5 hits.
 */
object BigramTokenizer {

    private val SEP = Regex("[\\s\\p{Punct}\\p{IsPunctuation}]+")

    /** Index time. Returns space-separated tokens to insert into FTS. */
    fun tokenizeForIndex(text: String): String {
        if (text.isBlank()) return ""
        val out = StringBuilder()
        for (chunk in text.split(SEP)) {
            if (chunk.isEmpty()) continue
            if (chunk.all { it.code < 128 }) {
                if (out.isNotEmpty()) out.append(' ')
                out.append(chunk.lowercase())
            } else {
                appendBigrams(chunk, out)
            }
        }
        return out.toString()
    }

    /**
     * Query time. Returns an FTS MATCH expression with implicit-AND semantics
     * (space-separated quoted tokens). See class KDoc for why the literal `AND`
     * keyword can't be used on stock Android SQLite.
     */
    fun tokenizeForQuery(input: String): String {
        if (input.isBlank()) return ""
        val parts = mutableListOf<String>()
        for (chunk in input.split(SEP)) {
            if (chunk.isEmpty()) continue
            if (chunk.all { it.code < 128 }) {
                parts += "\"${chunk.lowercase()}\""
            } else {
                val sb = StringBuilder()
                appendBigrams(chunk, sb)
                val bigrams = sb.toString().split(' ').filter { it.isNotBlank() }
                if (bigrams.isNotEmpty()) {
                    parts += bigrams.joinToString(" ") { "\"$it\"" }
                }
            }
        }
        return parts.joinToString(" ")
    }

    private fun appendBigrams(word: String, out: StringBuilder) {
        if (word.length == 1) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(word)
            return
        }
        for (i in 0 until word.length - 1) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(word, i, i + 2)
        }
    }
}
```

- [ ] **Step 4: Run tests — verify all pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.core.bigram.BigramTokenizerTest"`
Expected: BUILD SUCCESSFUL, 13 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/bigram/BigramTokenizer.kt app/src/test/java/com/example/personal_studio/core/bigram/BigramTokenizerTest.kt
git commit -m "kb(core): BigramTokenizer with TDD — index + query for FTS"
```

---

### Task 5: AppDatabase v4 → v5 + seed callback + DatabaseModule wiring

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/local/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/personal_studio/core/di/DatabaseModule.kt`

- [ ] **Step 1: Update `AppDatabase` to v5 with new entities + DAO accessors**

Replace the file contents:

```kotlin
package com.example.personal_studio.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.local.db.entity.ChatMessageEntity
import com.example.personal_studio.data.local.db.entity.ChatSessionEntity
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import com.example.personal_studio.data.local.db.entity.ScanDocumentEntity
import com.example.personal_studio.data.local.db.entity.ScanPageEntity

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ScanDocumentEntity::class,
        ScanPageEntity::class,
        KbCategoryEntity::class,
        KbEntryEntity::class,
        KbRelationEntity::class,
        KbEntryFtsEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun scanDocumentDao(): ScanDocumentDao
    abstract fun scanPageDao(): ScanPageDao
    abstract fun kbCategoryDao(): KbCategoryDao
    abstract fun kbEntryDao(): KbEntryDao
    abstract fun kbFtsDao(): KbFtsDao

    companion object {
        const val VERSION = 5
        const val NAME = "personal-studio.db"

        /** Default seed inserted by [KbSeedCallback] on first DB creation. */
        val DEFAULT_KB_CATEGORIES: List<String> = listOf(
            "数学", "物理", "化学", "生物", "英语", "编程", "其它",
        )
    }
}
```

- [ ] **Step 2: Add `KbSeedCallback` next to `AppDatabase`**

Create file `app/src/main/java/com/example/personal_studio/data/local/db/KbSeedCallback.kt`:

```kotlin
package com.example.personal_studio.data.local.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Seeds the 7 default KB categories on first DB creation. Triggered by
 * destructive-migration too because Room calls onCreate after a wipe.
 */
class KbSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (name in AppDatabase.DEFAULT_KB_CATEGORIES) {
            db.execSQL(
                "INSERT OR IGNORE INTO kb_categories (parentId, name, seeded, createdAt) VALUES (NULL, ?, 1, ?)",
                arrayOf(name, now),
            )
        }
    }
}
```

- [ ] **Step 3: Wire callback + new DAOs in `DatabaseModule`**

Replace the contents of `app/src/main/java/com/example/personal_studio/core/di/DatabaseModule.kt`:

```kotlin
package com.example.personal_studio.core.di

import android.content.Context
import androidx.room.Room
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.KbSeedCallback
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(KbSeedCallback())
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()
    @Provides fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideScanDocumentDao(db: AppDatabase): ScanDocumentDao = db.scanDocumentDao()
    @Provides fun provideScanPageDao(db: AppDatabase): ScanPageDao = db.scanPageDao()
    @Provides fun provideKbCategoryDao(db: AppDatabase): KbCategoryDao = db.kbCategoryDao()
    @Provides fun provideKbEntryDao(db: AppDatabase): KbEntryDao = db.kbEntryDao()
    @Provides fun provideKbFtsDao(db: AppDatabase): KbFtsDao = db.kbFtsDao()

    @Provides
    @Singleton
    fun provideChatRepository(
        sessionDao: ChatSessionDao,
        messageDao: ChatMessageDao,
    ): com.example.personal_studio.data.repository.ChatRepository =
        com.example.personal_studio.data.repository.ChatRepositoryImpl(sessionDao, messageDao)
}
```

- [ ] **Step 4: Build to confirm Room schema generation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. KSP generates `app/schemas/.../AppDatabase/5.json`.

- [ ] **Step 5: Verify schema written**

Run: `ls app/schemas/com.example.personal_studio.data.local.db.AppDatabase/`
Expected: includes `5.json`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/local/db/AppDatabase.kt app/src/main/java/com/example/personal_studio/data/local/db/KbSeedCallback.kt app/src/main/java/com/example/personal_studio/core/di/DatabaseModule.kt app/schemas/com.example.personal_studio.data.local.db.AppDatabase/5.json
git commit -m "kb(db): bump Room to v5 + seed default categories on create"
```

---

### Task 6: DAO instrumented test (smoke for Room v5 schema)

**Files:**
- Create: `app/src/androidTest/java/com/example/personal_studio/data/local/db/KbDaoTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.personal_studio.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import com.example.personal_studio.data.local.db.entity.KbSourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KbDaoTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .addCallback(KbSeedCallback())
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    @Test fun seedCategories_inserted_onCreate() = runBlocking {
        val all = db.kbCategoryDao().observeAll().first()
        assertEquals(7, all.size)
        assertTrue(all.all { it.seeded })
        assertEquals(setOf("数学", "物理", "化学", "生物", "英语", "编程", "其它"), all.map { it.name }.toSet())
    }

    @Test fun insertEntry_thenObserveById_returnsIt() = runBlocking {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        val id = db.kbEntryDao().insert(
            KbEntryEntity(
                title = "二次函数判别式",
                categoryId = mathId,
                sourceType = KbSourceType.CHAT_MESSAGE,
                sourceChatMessageId = 42L,
                sourceChatSessionId = 7L,
                sourceScanPageId = null,
                originalImagePath = null,
                standardizedQuestion = null,
                summaryMarkdown = "## 核心概念\n…",
                createdAt = now,
                updatedAt = now,
            ),
        )
        val out = db.kbEntryDao().observe(id).first()
        assertNotNull(out)
        assertEquals("二次函数判别式", out!!.title)
        assertEquals(mathId, out.categoryId)
    }

    @Test fun observeMistakes_filtersByStandardizedQuestionNotNull() = runBlocking {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        // notes
        db.kbEntryDao().insert(
            KbEntryEntity(
                title = "笔记A", categoryId = mathId, sourceType = KbSourceType.CHAT_MESSAGE,
                sourceChatMessageId = 1, sourceChatSessionId = 1, sourceScanPageId = null,
                originalImagePath = null, standardizedQuestion = null,
                summaryMarkdown = "...", createdAt = now, updatedAt = now,
            ),
        )
        // mistake
        db.kbEntryDao().insert(
            KbEntryEntity(
                title = "题目B", categoryId = mathId, sourceType = KbSourceType.SCAN,
                sourceChatMessageId = null, sourceChatSessionId = null, sourceScanPageId = 99,
                originalImagePath = "/tmp/x.jpg", standardizedQuestion = "求 x 的值",
                summaryMarkdown = "...", createdAt = now, updatedAt = now,
            ),
        )
        val mistakes = db.kbEntryDao().observeMistakes().first()
        assertEquals(1, mistakes.size)
        assertEquals("题目B", mistakes[0].title)
    }

    @Test fun ftsRoundtrip_indexAndMatch() = runBlocking {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        val id = db.kbEntryDao().insert(
            KbEntryEntity(
                title = "二次函数判别式", categoryId = mathId,
                sourceType = KbSourceType.CHAT_MESSAGE, sourceChatMessageId = 1,
                sourceChatSessionId = 1, sourceScanPageId = null,
                originalImagePath = null, standardizedQuestion = null,
                summaryMarkdown = "判别式 b 平方 减 4ac",
                createdAt = now, updatedAt = now,
            ),
        )
        db.kbFtsDao().upsert(
            KbEntryFtsEntity(
                rowid = id,
                titleBigrams = "二次 次函 函数 判别 别式",
                summaryBigrams = "判别 别式",
                standardizedQuestionBigrams = "",
            ),
        )
        val hits = db.kbEntryDao().searchOnce("\"判别\" \"别式\"")
        assertEquals(1, hits.size)
        assertEquals(id, hits[0].id)
    }

    @Test fun deletingEntry_cascadesRelations() = runBlocking {
        val now = System.currentTimeMillis()
        val mathId = db.kbCategoryDao().findByName("数学")!!.id
        fun mkEntry(title: String) = KbEntryEntity(
            title = title, categoryId = mathId, sourceType = KbSourceType.CHAT_MESSAGE,
            sourceChatMessageId = 1, sourceChatSessionId = 1, sourceScanPageId = null,
            originalImagePath = null, standardizedQuestion = null,
            summaryMarkdown = "...", createdAt = now, updatedAt = now,
        )
        val a = db.kbEntryDao().insert(mkEntry("A"))
        val b = db.kbEntryDao().insert(mkEntry("B"))
        db.kbEntryDao().insertRelations(listOf(KbRelationEntity(a, b, 1f)))
        assertEquals(1, db.kbEntryDao().observeRelated(a).first().size)
        db.kbEntryDao().delete(a)
        // relation should be gone
        assertEquals(0, db.kbEntryDao().observeRelated(a).first().size)
    }
}
```

- [ ] **Step 2: Build the androidTest variant**

Run: `./gradlew :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run on a connected device or emulator (only if available)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.personal_studio.data.local.db.KbDaoTest"`
Expected: 5 tests pass. If no device/emulator is connected, defer this step until Phase 7's full DoD.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/example/personal_studio/data/local/db/KbDaoTest.kt
git commit -m "kb(db): instrumented DAO smoke — seed/insert/mistakes/FTS/cascade"
```

---

### Task 7: `KbImageStore` — copy/delete originalImagePath helpers

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/kb/KbImageStore.kt`

- [ ] **Step 1: Write the helper**

```kotlin
package com.example.personal_studio.data.kb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the `filesDir/kb/` directory: staging temp copies during draft preview,
 * promoting them to `<entryId>.jpg` on commit, and cleaning up on entry delete.
 */
@Singleton
class KbImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseDir: File get() = File(context.filesDir, "kb").apply { mkdirs() }

    /** Copies [sourcePath] to a temp file in kb/. Returns the temp file's absolute path. */
    fun stageCopy(sourcePath: String): String {
        val src = File(sourcePath)
        require(src.exists()) { "source image not found: $sourcePath" }
        val dst = File(baseDir, "tmp_${UUID.randomUUID()}.jpg")
        src.copyTo(dst, overwrite = true)
        return dst.absolutePath
    }

    /** Renames a staged temp file to `<entryId>.jpg`. Returns the final absolute path. */
    fun promote(stagedPath: String, entryId: Long): String {
        val staged = File(stagedPath)
        require(staged.exists()) { "staged image not found: $stagedPath" }
        val final = File(baseDir, "$entryId.jpg")
        if (final.exists()) final.delete()
        staged.renameTo(final)
        return final.absolutePath
    }

    /** Best-effort delete; safe to call even if file is missing. */
    fun deleteForEntry(entryId: Long) {
        File(baseDir, "$entryId.jpg").delete()
    }

    /** Best-effort cleanup of a staged temp (cancel path). */
    fun deleteStaged(stagedPath: String?) {
        if (stagedPath.isNullOrBlank()) return
        runCatching { File(stagedPath).delete() }
    }
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/kb/KbImageStore.kt
git commit -m "kb(data): KbImageStore for filesDir/kb/ stage/promote/delete"
```

---

### Task 8: `KnowledgeRepository` interface + Impl (CRUD only — LLM call stubbed)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepository.kt`
- Create: `app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.example.personal_studio.data.repository

import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {

    // -- read --
    fun observeAllEntries(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntry>>
    fun observeMistakes(): Flow<List<KbEntry>>
    fun observeEntry(id: Long): Flow<KbEntry?>
    fun observeRelated(id: Long): Flow<List<KbEntry>>
    fun observeCategories(): Flow<List<KbCategory>>
    fun observeNotesCount(): Flow<Int>
    fun observeMistakesCount(): Flow<Int>
    fun observeCategoryCounts(): Flow<Map<Long, Int>>

    /** AND-mode FTS search; empty input → empty list. */
    fun search(query: String): Flow<List<KbEntry>>
    /** OR-mode FTS search used as fallback when AND returns < 5 results. */
    suspend fun searchOr(query: String): List<KbEntry>

    // -- write --
    suspend fun saveEntry(draft: KbEntryDraft): Long
    suspend fun updateEntry(entry: KbEntry)
    suspend fun deleteEntry(id: Long)
    suspend fun upsertCategory(name: String): Long

    // -- LLM bridge (implemented in Phase 2; throws NotImplementedError until then) --
    suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft
}
```

- [ ] **Step 2: Write the implementation**

```kotlin
package com.example.personal_studio.data.repository

import androidx.room.withTransaction
import com.example.personal_studio.core.bigram.BigramTokenizer
import com.example.personal_studio.data.kb.KbImageStore
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.dao.KbCategoryDao
import com.example.personal_studio.data.local.db.dao.KbEntryDao
import com.example.personal_studio.data.local.db.dao.KbFtsDao
import com.example.personal_studio.data.local.db.entity.KbCategoryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbEntryFtsEntity
import com.example.personal_studio.data.local.db.entity.KbRelationEntity
import com.example.personal_studio.data.local.db.entity.KbSourceType
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val entryDao: KbEntryDao,
    private val categoryDao: KbCategoryDao,
    private val ftsDao: KbFtsDao,
    private val imageStore: KbImageStore,
) : KnowledgeRepository {

    // -- read --

    override fun observeAllEntries(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntry>> =
        combine(
            entryDao.observeAll(categoryId, notesOnly),
            categoryDao.observeAll(),
        ) { entries, cats -> entries.toDomain(cats) }

    override fun observeMistakes(): Flow<List<KbEntry>> =
        combine(
            entryDao.observeMistakes(),
            categoryDao.observeAll(),
        ) { entries, cats -> entries.toDomain(cats) }

    override fun observeEntry(id: Long): Flow<KbEntry?> =
        combine(entryDao.observe(id), categoryDao.observeAll()) { e, cats ->
            e?.let { listOf(it).toDomain(cats).firstOrNull() }
        }

    override fun observeRelated(id: Long): Flow<List<KbEntry>> =
        combine(entryDao.observeRelated(id), categoryDao.observeAll()) { es, cats ->
            es.toDomain(cats)
        }

    override fun observeCategories(): Flow<List<KbCategory>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeNotesCount(): Flow<Int> = entryDao.countNotes()
    override fun observeMistakesCount(): Flow<Int> = entryDao.countMistakes()
    override fun observeCategoryCounts(): Flow<Map<Long, Int>> =
        entryDao.countByCategory().map { rows -> rows.associate { it.id to it.count } }

    override fun search(query: String): Flow<List<KbEntry>> {
        val ftsQ = BigramTokenizer.tokenizeForQuery(query)
        if (ftsQ.isBlank()) return flowOf(emptyList())
        return combine(entryDao.searchFlow(ftsQ), categoryDao.observeAll()) { es, cats ->
            es.toDomain(cats)
        }
    }

    override suspend fun searchOr(query: String): List<KbEntry> {
        val baseTokens = BigramTokenizer.tokenizeForQuery(query)
        if (baseTokens.isBlank()) return emptyList()
        // baseTokens is now space-separated quoted bigrams (implicit AND); convert to explicit OR.
        // (Stock Android SQLite FTS3/4 supports the OR keyword in standard query syntax.)
        val ftsQ = baseTokens.split(' ').filter { it.isNotBlank() }.joinToString(" OR ")
        val cats = categoryDao.observeAll().let { flow ->
            // first emission is sufficient
            kotlinx.coroutines.flow.first(flow)
        }
        return entryDao.searchOnce(ftsQ).toDomain(cats)
    }

    // -- write --

    override suspend fun saveEntry(draft: KbEntryDraft): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val categoryId = upsertCategory(draft.categorySuggestion)

        val (sourceType, chatMsgId, chatSessionId, scanPageId) = unpackSource(draft.source)

        // 1. Insert with placeholder image path; we'll patch after promote.
        val entryId = entryDao.insert(
            KbEntryEntity(
                title = draft.title,
                categoryId = categoryId,
                sourceType = sourceType,
                sourceChatMessageId = chatMsgId,
                sourceChatSessionId = chatSessionId,
                sourceScanPageId = scanPageId,
                originalImagePath = null,
                standardizedQuestion = draft.standardizedQuestion,
                summaryMarkdown = draft.summaryMarkdown,
                createdAt = now,
                updatedAt = now,
            ),
        )

        // 2. Promote staged image (if any) to <entryId>.jpg, then patch the entry row.
        if (!draft.originalImagePath.isNullOrBlank()) {
            val finalPath = imageStore.promote(draft.originalImagePath, entryId)
            entryDao.update(
                entryDao.get(entryId)!!.copy(originalImagePath = finalPath),
            )
        }

        // 3. Write FTS row (post-update so we read the final image-bearing row, though FTS
        //    only indexes text fields; reading is just a defensive fetch).
        ftsDao.upsert(
            KbEntryFtsEntity(
                rowid = entryId,
                titleBigrams = BigramTokenizer.tokenizeForIndex(draft.title),
                summaryBigrams = BigramTokenizer.tokenizeForIndex(draft.summaryMarkdown),
                standardizedQuestionBigrams = BigramTokenizer.tokenizeForIndex(draft.standardizedQuestion.orEmpty()),
            ),
        )

        // 4. Resolve relatedEntryTitles → existing entryIds → kb_relations rows.
        if (draft.relatedEntryTitles.isNotEmpty()) {
            val matched = entryDao.findByTitles(draft.relatedEntryTitles)
            if (matched.isNotEmpty()) {
                entryDao.insertRelations(
                    matched.map { KbRelationEntity(fromEntryId = entryId, toEntryId = it.id) },
                )
            }
        }

        entryId
    }

    override suspend fun updateEntry(entry: KbEntry) = db.withTransaction {
        val existing = entryDao.get(entry.id) ?: return@withTransaction
        val now = System.currentTimeMillis()
        entryDao.update(
            existing.copy(
                title = entry.title,
                categoryId = entry.categoryId,
                standardizedQuestion = entry.standardizedQuestion,
                summaryMarkdown = entry.summaryMarkdown,
                updatedAt = now,
            ),
        )
        ftsDao.upsert(
            KbEntryFtsEntity(
                rowid = entry.id,
                titleBigrams = BigramTokenizer.tokenizeForIndex(entry.title),
                summaryBigrams = BigramTokenizer.tokenizeForIndex(entry.summaryMarkdown),
                standardizedQuestionBigrams = BigramTokenizer.tokenizeForIndex(entry.standardizedQuestion.orEmpty()),
            ),
        )
    }

    override suspend fun deleteEntry(id: Long) = db.withTransaction {
        ftsDao.delete(id)
        entryDao.delete(id)
        imageStore.deleteForEntry(id)
    }

    override suspend fun upsertCategory(name: String): Long {
        val trimmed = name.trim().ifBlank { "其它" }
        categoryDao.findByName(trimmed)?.let { return it.id }
        val id = categoryDao.insert(
            KbCategoryEntity(name = trimmed, seeded = false, createdAt = System.currentTimeMillis()),
        )
        // findByName() above raced with a concurrent insert? IGNORE strategy returns -1; refetch.
        return if (id > 0) id else categoryDao.findByName(trimmed)!!.id
    }

    // -- LLM bridge (Phase 2 will replace this body) --

    override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft =
        throw NotImplementedError("draftFromSource implemented in Phase 2")

    // -- mappers --

    private fun List<KbEntryEntity>.toDomain(cats: List<KbCategoryEntity>): List<KbEntry> {
        val byId = cats.associateBy { it.id }
        return map { e ->
            KbEntry(
                id = e.id,
                title = e.title,
                categoryId = e.categoryId,
                categoryName = e.categoryId?.let { byId[it]?.name },
                source = e.sourceType.toDomain(),
                sourceChatMessageId = e.sourceChatMessageId,
                sourceChatSessionId = e.sourceChatSessionId,
                sourceScanPageId = e.sourceScanPageId,
                originalImagePath = e.originalImagePath,
                standardizedQuestion = e.standardizedQuestion,
                summaryMarkdown = e.summaryMarkdown,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
            )
        }
    }

    private fun KbCategoryEntity.toDomain() = KbCategory(id = id, name = name, seeded = seeded)
    private fun KbSourceType.toDomain() = when (this) {
        KbSourceType.CHAT_MESSAGE -> KbSource.CHAT_MESSAGE
        KbSourceType.CHAT_SESSION -> KbSource.CHAT_SESSION
        KbSourceType.SCAN -> KbSource.SCAN
    }

    private data class SourceParts(
        val sourceType: KbSourceType,
        val chatMsgId: Long?,
        val chatSessionId: Long?,
        val scanPageId: Long?,
    )

    private fun unpackSource(s: KbDraftSource): SourceParts = when (s) {
        is KbDraftSource.FromChatMessage -> SourceParts(KbSourceType.CHAT_MESSAGE, s.aiMessageId, s.sessionId, null)
        is KbDraftSource.FromChatSession -> SourceParts(KbSourceType.CHAT_SESSION, null, s.sessionId, null)
        is KbDraftSource.FromScanPage -> SourceParts(KbSourceType.SCAN, null, null, s.pageId)
    }
}
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepository.kt app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt
git commit -m "kb(data): KnowledgeRepository + Impl with CRUD/FTS/relations (no LLM yet)"
```

---

### Task 9: Hilt module — `KnowledgeModule`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/core/di/KnowledgeModule.kt`

- [ ] **Step 1: Write the module**

```kotlin
package com.example.personal_studio.core.di

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.data.repository.KnowledgeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {
    @Binds @Singleton
    abstract fun bindKnowledgeRepository(impl: KnowledgeRepositoryImpl): KnowledgeRepository
}
```

- [ ] **Step 2: Build to confirm Hilt graph is valid**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/core/di/KnowledgeModule.kt
git commit -m "kb(di): bind KnowledgeRepository → KnowledgeRepositoryImpl"
```

---

### Task 10: Phase 1 verification — install + smoke check seeded data

**Files:** none

- [ ] **Step 1: Build + install on device/emulator**

Run: `./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Launch app**

Run: `adb shell am start -n com.example.personal_studio/.MainActivity`
Expected: app starts. Tap the `kb` tab — placeholder still shows (KbHome lands in Phase 4).

- [ ] **Step 3: Inspect DB via Android Studio's App Inspection**

Open Android Studio → App Inspection → select `com.example.personal_studio` → Database Inspector → expand `personal-studio.db` → `kb_categories` table.
Expected: 7 rows with `seeded = 1`. Names: 数学/物理/化学/生物/英语/编程/其它.

- [ ] **Step 4: Run unit tests one more time as a sanity sweep**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit phase tag** (optional — useful for rollback)

```bash
git tag p3-phase1
```

---

## Phase 2 — LLM Contract

Goal: extend `LLMProvider.generateStructured` to accept `List<LlmMessage>` (so vision is supported), implement `KnowledgeRepository.draftFromSource` with JSON parse → 1× retry → fallback, expose it via `SaveToKnowledgeUseCase`. All paths covered by unit tests with a fake LLM.

### Task 11: Extend `LLMProvider.generateStructured`

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/remote/llm/LLMProvider.kt`

- [ ] **Step 1: Replace the file contents**

```kotlin
package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow

enum class LlmRole { SYSTEM, USER, ASSISTANT }

/** One turn of a conversation. [images] are raw JPEG/PNG bytes. */
data class LlmMessage(
    val role: LlmRole,
    val text: String,
    val images: List<ByteArray> = emptyList(),
)

/**
 * Provider-agnostic LLM contract. Two structured-output entry points:
 *  - the **multi-message** overload supports vision + system/user history
 *  - the legacy single-prompt overload remains for back-compat (defaults to wrapping
 *    the prompt in a single USER message and delegating to the new overload)
 */
interface LLMProvider {
    val name: String

    fun generate(messages: List<LlmMessage>, temperature: Float = 0.7f): Flow<LlmChunk>

    fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk> = generate(
        messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(LlmMessage(LlmRole.SYSTEM, systemPrompt))
            add(LlmMessage(LlmRole.USER, prompt))
        },
        temperature = temperature,
    )

    fun generateMultimodal(
        prompt: String,
        images: List<ByteArray>,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
    ): Flow<LlmChunk> = generate(
        messages = buildList {
            if (!systemPrompt.isNullOrBlank()) add(LlmMessage(LlmRole.SYSTEM, systemPrompt))
            add(LlmMessage(LlmRole.USER, prompt, images = images))
        },
        temperature = temperature,
    )

    /**
     * Multi-message structured output. Implementations should set
     * `response_format = json_object` (OpenAI-compatible) and assemble vision
     * content parts when an LlmMessage carries images.
     */
    suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
        temperature: Float = 0.3f,
    ): String

    /** Legacy single-prompt overload. Delegates to the multi-message form. */
    suspend fun generateStructured(prompt: String, jsonSchema: String): String =
        generateStructured(
            messages = listOf(LlmMessage(LlmRole.USER, prompt)),
            jsonSchema = jsonSchema,
        )
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAILURE — `OpenAiCompatibleProvider` no longer overrides the new abstract method correctly. We fix this in Task 12.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/remote/llm/LLMProvider.kt
git commit -m "kb(llm): add multi-message generateStructured(messages, schema, temp)"
```

---

### Task 12: Update `OpenAiCompatibleProvider` to implement the new overload

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/remote/llm/OpenAiCompatibleProvider.kt`

- [ ] **Step 1: Replace the body of `generateStructured(prompt, jsonSchema)` and add the new overload above it**

Find the existing `override suspend fun generateStructured(prompt: String, jsonSchema: String): String { ... }` block (around line 97) and **replace it entirely** with:

```kotlin
    override suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
        temperature: Float,
    ): String {
        val key = resolveApiKey() ?: error("No API key configured")
        val endpoint = completionsUrl(resolveBaseUrl())
        val model = resolveModel()

        // Wrap caller messages: prepend a system instruction reminding the model to emit JSON
        // matching the schema, then forward the original messages (which may carry images).
        val schemaInstruction = LlmMessage(
            role = LlmRole.SYSTEM,
            text = """
                You must respond with valid JSON conforming to this schema:
                $jsonSchema

                Return only the JSON, no Markdown fences, no prose.
            """.trimIndent(),
        )
        val finalMessages = listOf(schemaInstruction) + messages

        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature.toDouble())
            put("stream", false)
            putJsonArray("messages") {
                finalMessages.forEach { m -> add(serializeMessage(m)) }
            }
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }

        val request = buildRequest(endpoint, key, body)
        return httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: $responseBody")
            val root = json.parseToJsonElement(responseBody).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: error("upstream returned no content")
            content
        }
    }
```

The interface's default `generateStructured(prompt, schema)` will now route through this new method automatically — no separate override needed.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/remote/llm/OpenAiCompatibleProvider.kt
git commit -m "kb(llm): OpenAiCompatibleProvider implements multi-message structured output"
```

---

### Task 13: `FakeLLMProvider` test double

**Files:**
- Create: `app/src/test/java/com/example/personal_studio/data/remote/llm/FakeLLMProvider.kt`

- [ ] **Step 1: Write the fake**

```kotlin
package com.example.personal_studio.data.remote.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-process fake. Configure via [structuredResponses] (queue) and inspect
 * [recordedMessages] for what each call received.
 */
class FakeLLMProvider : LLMProvider {
    override val name: String = "fake"

    /** Each call to generateStructured pops the head; throws if empty. */
    val structuredResponses: ArrayDeque<Result<String>> = ArrayDeque()

    /** Captures the messages list passed to each generateStructured call. */
    val recordedMessages: MutableList<List<LlmMessage>> = mutableListOf()

    /** Captures the schema strings passed to each generateStructured call. */
    val recordedSchemas: MutableList<String> = mutableListOf()

    override fun generate(messages: List<LlmMessage>, temperature: Float): Flow<LlmChunk> =
        flowOf(LlmChunk.Text("<fake>"), LlmChunk.Done(totalTokens = 0))

    override suspend fun generateStructured(
        messages: List<LlmMessage>,
        jsonSchema: String,
        temperature: Float,
    ): String {
        recordedMessages += messages
        recordedSchemas += jsonSchema
        val next = structuredResponses.removeFirstOrNull()
            ?: error("FakeLLMProvider: no queued response")
        return next.getOrThrow()
    }
}
```

- [ ] **Step 2: Compile-check (test source set)**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/example/personal_studio/data/remote/llm/FakeLLMProvider.kt
git commit -m "kb(test): FakeLLMProvider with queued responses + recorded inputs"
```

---

### Task 14: Implement `KnowledgeRepository.draftFromSource` with TDD

The plan: a) test JSON parse success path; b) test parse fail → retry → success; c) test parse fail → retry → fallback; d) test network error rethrows.

**Files:**
- Create: `app/src/test/java/com/example/personal_studio/data/repository/KnowledgeRepositoryDraftTest.kt`
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt` (replace `draftFromSource` body + add helpers + take an `LLMProvider` + a `SourceContextLoader` constructor arg)
- Create: `app/src/main/java/com/example/personal_studio/data/repository/SourceContextLoader.kt` (extract chat-message / chat-session / scan-page → LlmMessage list + staged image)

- [ ] **Step 1: Write `SourceContextLoader`**

```kotlin
package com.example.personal_studio.data.repository

import com.example.personal_studio.data.kb.KbImageStore
import com.example.personal_studio.data.local.db.dao.ChatMessageDao
import com.example.personal_studio.data.local.db.dao.ChatSessionDao
import com.example.personal_studio.data.local.db.dao.ScanDocumentDao
import com.example.personal_studio.data.local.db.dao.ScanPageDao
import com.example.personal_studio.data.local.db.entity.MessageRole
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
import com.example.personal_studio.domain.model.KbDraftSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads source content (chat / scan) and shapes it into the LlmMessage list
 * the LLM will see, plus a staged copy of the original image (if any) under
 * filesDir/kb/tmp_*.jpg that becomes the entry's originalImagePath on commit.
 */
@Singleton
class SourceContextLoader @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val scanDocumentDao: ScanDocumentDao,
    private val scanPageDao: ScanPageDao,
    private val imageStore: KbImageStore,
) {

    data class LoadedContext(
        val messages: List<LlmMessage>,
        /** filesDir/kb/tmp_<uuid>.jpg path; null when source has no image (CHAT_SESSION etc.). */
        val stagedImagePath: String?,
        /** Used when AI parse fails entirely so the fallback Draft has a sensible title. */
        val fallbackTitle: String,
    )

    suspend fun load(source: KbDraftSource): LoadedContext = when (source) {
        is KbDraftSource.FromChatMessage -> loadChatMessage(source)
        is KbDraftSource.FromChatSession -> loadChatSession(source)
        is KbDraftSource.FromScanPage -> loadScanPage(source)
    }

    private suspend fun loadChatMessage(s: KbDraftSource.FromChatMessage): LoadedContext {
        val all = chatMessageDao.getAllForSession(s.sessionId)
        val ai = all.firstOrNull { it.id == s.aiMessageId }
            ?: error("AI message ${s.aiMessageId} not found in session ${s.sessionId}")
        val precedingUser = all
            .filter { it.id < ai.id && it.role == MessageRole.USER }
            .maxByOrNull { it.id }
        val text = buildString {
            append("### 用户问题\n")
            append(precedingUser?.contentMarkdown.orEmpty())
            append("\n\n### AI 回答\n")
            append(ai.contentMarkdown)
        }
        val imageBytes = precedingUser?.attachedImagePath?.let { File(it).takeIf(File::exists)?.readBytes() }
        val staged = precedingUser?.attachedImagePath?.let { imageStore.stageCopy(it) }

        val messages = listOf(
            LlmMessage(LlmRole.USER, text, images = listOfNotNull(imageBytes)),
        )
        val sessionTitle = chatSessionDao.findById(s.sessionId)?.title ?: "会话"
        return LoadedContext(messages, staged, fallbackTitle = sessionTitle)
    }

    private suspend fun loadChatSession(s: KbDraftSource.FromChatSession): LoadedContext {
        val all = chatMessageDao.getAllForSession(s.sessionId)
        // Drop earliest messages until under a soft codepoint budget; preserve last N.
        val budget = TOTAL_CHARS_BUDGET
        val kept = mutableListOf<LlmMessage>()
        var charCount = 0
        for (m in all.asReversed()) {
            val role = when (m.role) {
                MessageRole.USER -> LlmRole.USER
                MessageRole.AI -> LlmRole.ASSISTANT
                MessageRole.SYSTEM -> LlmRole.SYSTEM
            }
            val msg = LlmMessage(role, m.contentMarkdown)
            charCount += m.contentMarkdown.length
            if (charCount > budget) break
            kept += msg
        }
        kept.reverse()
        kept += LlmMessage(LlmRole.USER, "请把以上对话归档为一张知识卡片。")
        val sessionTitle = chatSessionDao.findById(s.sessionId)?.title ?: "会话"
        return LoadedContext(kept, stagedImagePath = null, fallbackTitle = sessionTitle)
    }

    private suspend fun loadScanPage(s: KbDraftSource.FromScanPage): LoadedContext {
        val page = scanPageDao.getById(s.pageId) ?: error("scan page ${s.pageId} not found")
        val doc = scanDocumentDao.getById(s.docId)
        val docTitle = doc?.title ?: "扫描文档"
        val imageBytes = File(page.enhancedImagePath).readBytes()
        val staged = imageStore.stageCopy(page.enhancedImagePath)
        val text = "请识别图中内容并归档为一张知识卡片。如果是题目，按题目处理（isQuestion=true）。文档标题：$docTitle"
        val messages = listOf(LlmMessage(LlmRole.USER, text, images = listOf(imageBytes)))
        return LoadedContext(messages, staged, fallbackTitle = docTitle)
    }

    companion object {
        /** Soft cap on conversation chars sent to the LLM in CHAT_SESSION mode. ~6 k chars ≈ 4 k tokens for CJK. */
        private const val TOTAL_CHARS_BUDGET = 6_000
    }
}
```

> Note: this assumes `ChatMessageDao.getAllForSession(sessionId): List<ChatMessageEntity>`, `ChatSessionDao.findById(id): ChatSessionEntity?`, `ScanPageDao.getById(id): ScanPageEntity?`, `ScanDocumentDao.getById(id): ScanDocumentEntity?` exist. They are added in Task 15 below if missing.

- [ ] **Step 2: Add the missing DAO suspend accessors**

Run: `git grep -n "getAllForSession" app/src/main/java/com/example/personal_studio/data/local/db/dao/ChatMessageDao.kt || true`
If not found, append to `ChatMessageDao` interface:

```kotlin
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getAllForSession(sessionId: Long): List<com.example.personal_studio.data.local.db.entity.ChatMessageEntity>
```

Run: `git grep -n "findById" app/src/main/java/com/example/personal_studio/data/local/db/dao/ChatSessionDao.kt || true`
If not found, append to `ChatSessionDao` interface:

```kotlin
    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): com.example.personal_studio.data.local.db.entity.ChatSessionEntity?
```

Run: `git grep -n "fun getById" app/src/main/java/com/example/personal_studio/data/local/db/dao/ScanPageDao.kt || true`
If not found, append to `ScanPageDao` interface:

```kotlin
    @Query("SELECT * FROM scan_pages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): com.example.personal_studio.data.local.db.entity.ScanPageEntity?
```

Run: `git grep -n "fun getById" app/src/main/java/com/example/personal_studio/data/local/db/dao/ScanDocumentDao.kt || true`
If not found, append to `ScanDocumentDao` interface:

```kotlin
    @Query("SELECT * FROM scan_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): com.example.personal_studio.data.local.db.entity.ScanDocumentEntity?
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/repository/SourceContextLoader.kt app/src/main/java/com/example/personal_studio/data/local/db/dao/
git commit -m "kb(data): SourceContextLoader + DAO suspend accessors used by it"
```

---

### Task 15: Wire LLM into `KnowledgeRepositoryImpl.draftFromSource` (TDD)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt`
- Create: `app/src/test/java/com/example/personal_studio/data/repository/KnowledgeRepositoryDraftTest.kt`

- [ ] **Step 1: Write the failing tests first**

```kotlin
package com.example.personal_studio.data.repository

import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.remote.llm.FakeLLMProvider
import com.example.personal_studio.data.remote.llm.LlmMessage
import com.example.personal_studio.data.remote.llm.LlmRole
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbDraftSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KnowledgeRepositoryDraftTest {

    private lateinit var fakeLlm: FakeLLMProvider
    private lateinit var loader: SourceContextLoader
    private lateinit var repo: KnowledgeRepositoryImpl

    @Before fun setup() {
        fakeLlm = FakeLLMProvider()
        loader = mockk()
        // Repo + DAO collaborators are mocked; we only test draftFromSource here.
        repo = KnowledgeRepositoryImpl(
            db = mockk(relaxed = true),
            entryDao = mockk(relaxed = true).also {
                coEvery { it.recentTitles(any()) } returns listOf("二次函数", "韦达定理")
            },
            categoryDao = mockk(relaxed = true).also {
                coEvery { it.observeAll() } returns kotlinx.coroutines.flow.flowOf(emptyList())
            },
            ftsDao = mockk(relaxed = true),
            imageStore = mockk(relaxed = true),
            llm = fakeLlm,
            contextLoader = loader,
        )
    }

    private fun mockChatLoaderReturns(text: String) {
        coEvery { loader.load(any()) } returns SourceContextLoader.LoadedContext(
            messages = listOf(LlmMessage(LlmRole.USER, text)),
            stagedImagePath = "/tmp/staged.jpg",
            fallbackTitle = "session-X",
        )
    }

    @Test fun parsesValidJsonOnFirstTry() = runTest {
        mockChatLoaderReturns("Q+A text")
        fakeLlm.structuredResponses += Result.success(
            """{
                "title":"二次函数判别式",
                "categorySuggestion":"数学",
                "isQuestion":true,
                "standardizedQuestion":"求 Δ 的值",
                "summaryMarkdown":"## 核心概念\n…",
                "relatedEntryTitles":["二次函数"]
            }""",
        )
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertEquals("二次函数判别式", draft.title)
        assertEquals("数学", draft.categorySuggestion)
        assertEquals("求 Δ 的值", draft.standardizedQuestion)
        assertTrue(draft.summaryMarkdown.startsWith("## 核心概念"))
        assertEquals(listOf("二次函数"), draft.relatedEntryTitles)
        assertFalse(draft.isFallback)
        assertEquals("/tmp/staged.jpg", draft.originalImagePath)
    }

    @Test fun retriesOnceOnGarbage_thenSucceeds() = runTest {
        mockChatLoaderReturns("Q+A")
        fakeLlm.structuredResponses += Result.success("not json at all")
        fakeLlm.structuredResponses += Result.success(
            """{
                "title":"X","categorySuggestion":"其它","isQuestion":false,
                "standardizedQuestion":null,"summaryMarkdown":"## 核心概念\n.","relatedEntryTitles":[]
            }""",
        )
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertEquals("X", draft.title)
        assertFalse(draft.isFallback)
        assertEquals(2, fakeLlm.recordedMessages.size)
    }

    @Test fun fallsBackWhenBothCallsReturnGarbage() = runTest {
        mockChatLoaderReturns("Q+A")
        fakeLlm.structuredResponses += Result.success("garbage 1")
        fakeLlm.structuredResponses += Result.success("garbage 2")
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertTrue(draft.isFallback)
        assertEquals(KbDraftFallbackReason.JSON_PARSE_FAILED, draft.fallbackReason)
        assertEquals("session-X", draft.title)
        assertEquals("其它", draft.categorySuggestion)
        assertNotNull(draft.summaryMarkdown)
        assertTrue(draft.summaryMarkdown.contains("原始内容"))
    }

    @Test fun fallbackOnMissingRequiredFields() = runTest {
        mockChatLoaderReturns("Q+A")
        // valid JSON but missing summaryMarkdown
        fakeLlm.structuredResponses += Result.success("""{"title":"X"}""")
        fakeLlm.structuredResponses += Result.success("""{"title":"Y"}""")
        val draft = repo.draftFromSource(KbDraftSource.FromChatMessage(1, 2))
        assertTrue(draft.isFallback)
        assertEquals(KbDraftFallbackReason.MISSING_REQUIRED_FIELDS, draft.fallbackReason)
    }
}
```

- [ ] **Step 2: Update `KnowledgeRepositoryImpl` constructor + body**

Modify the class declaration to inject `LLMProvider` + `SourceContextLoader`:

```kotlin
@Singleton
class KnowledgeRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val entryDao: KbEntryDao,
    private val categoryDao: KbCategoryDao,
    private val ftsDao: KbFtsDao,
    private val imageStore: KbImageStore,
    private val llm: com.example.personal_studio.data.remote.llm.LLMProvider,
    private val contextLoader: SourceContextLoader,
) : KnowledgeRepository {
```

Replace the `draftFromSource` body and add the helpers (place near the bottom of the class):

```kotlin
    override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft {
        val context = contextLoader.load(source)
        val schema = JSON_SCHEMA
        val systemMsg = com.example.personal_studio.data.remote.llm.LlmMessage(
            role = com.example.personal_studio.data.remote.llm.LlmRole.SYSTEM,
            text = SYSTEM_PROMPT,
        )
        val recentTitles = entryDao.recentTitles(50)
        val categories = kotlinx.coroutines.flow.first(categoryDao.observeAll()).map { it.name }
        val contextHeader = com.example.personal_studio.data.remote.llm.LlmMessage(
            role = com.example.personal_studio.data.remote.llm.LlmRole.USER,
            text = buildContextHeader(categories, recentTitles),
        )
        val finalMessages = listOf(systemMsg, contextHeader) + context.messages

        // First attempt
        val first = runCatching { llm.generateStructured(finalMessages, schema, temperature = 0.3f) }
            .getOrElse { throw it }                           // network error → propagate
        parseStrict(first, source, context)?.let { return it }

        // Retry with lower temp
        val second = runCatching { llm.generateStructured(finalMessages, schema, temperature = 0.1f) }
            .getOrElse { throw it }
        parseStrict(second, source, context)?.let { return it }

        // Fallback skeleton
        return fallbackDraft(
            source = source,
            context = context,
            rawText = second.ifBlank { first },
            reason = if (canParseJson(second) || canParseJson(first))
                KbDraftFallbackReason.MISSING_REQUIRED_FIELDS
            else KbDraftFallbackReason.JSON_PARSE_FAILED,
        )
    }

    private fun buildContextHeader(categories: List<String>, recentTitles: List<String>): String =
        buildString {
            append("[已有分类]\n")
            append(if (categories.isEmpty()) "(空)" else categories.joinToString(", "))
            append("\n\n[已有条目标题（最多 50 条，按 createdAt DESC）]\n")
            if (recentTitles.isEmpty()) append("(空)") else recentTitles.forEach { append("- ").append(it).append('\n') }
            append("\n\n[输出 schema]\n").append(JSON_SCHEMA)
        }

    private fun canParseJson(s: String): Boolean = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(s)
    }.isSuccess

    private fun parseStrict(raw: String, source: KbDraftSource, ctx: SourceContextLoader.LoadedContext): KbEntryDraft? {
        val obj = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
        }.getOrNull() ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val cat = obj["categorySuggestion"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val summary = obj["summaryMarkdown"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val isQuestion = obj["isQuestion"]?.jsonPrimitive?.booleanOrNull ?: false
        val stdQ = obj["standardizedQuestion"]?.jsonPrimitive?.contentOrNull?.takeIf { isQuestion && it.isNotBlank() }
        val related = obj["relatedEntryTitles"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return KbEntryDraft(
            source = source,
            title = title,
            categorySuggestion = cat,
            standardizedQuestion = stdQ,
            summaryMarkdown = summary,
            relatedEntryTitles = related.take(3),
            originalImagePath = ctx.stagedImagePath,
        )
    }

    private fun fallbackDraft(
        source: KbDraftSource,
        context: SourceContextLoader.LoadedContext,
        rawText: String,
        reason: KbDraftFallbackReason,
    ): KbEntryDraft = KbEntryDraft(
        source = source,
        title = context.fallbackTitle.ifBlank { "未命名条目" },
        categorySuggestion = "其它",
        standardizedQuestion = null,
        summaryMarkdown = "## 原始内容\n\n$rawText",
        relatedEntryTitles = emptyList(),
        originalImagePath = context.stagedImagePath,
        isFallback = true,
        fallbackReason = reason,
    )

    companion object {
        private const val SYSTEM_PROMPT = """你是学习知识库的归档助手。任务：把给定的对话或扫描页浓缩为一张知识卡片。
只输出严格的 JSON（不带 markdown 代码围栏、不带任何解释文字）。
所有字段必填；不知道就给空字符串、null 或空数组（按 schema 类型）。
公式用 ${'$'}...${'$'}（行内）或 ${'$'}${'$'}...${'$'}${'$'}（块级）。
中文为主。"""

        private const val JSON_SCHEMA = """{
  "title": "string，≤15 字，能 1 秒看懂这条 KB 关于什么",
  "categorySuggestion": "string，下面分类列表里的某一个，或全新分类名",
  "isQuestion": "boolean，是否为可独立成立的题目",
  "standardizedQuestion": "string|null，isQuestion=true 时必填；规范化清晰可独立阅读的题面，LaTeX 包公式；isQuestion=false 填 null",
  "summaryMarkdown": "string，5 节 Markdown：## 核心概念 / ## 推导过程 / ## 关键公式 / ## 易错点 / ## 应用场景",
  "relatedEntryTitles": "array<string>，从给定的已有标题列表精确挑出最多 3 个；若无则空数组"
}"""
    }
```

Add the necessary imports at the top of `KnowledgeRepositoryImpl.kt`:

```kotlin
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
```

- [ ] **Step 3: Run the new tests — they should pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.data.repository.KnowledgeRepositoryDraftTest"`
Expected: 4 tests pass.

- [ ] **Step 4: Run all unit tests as a regression sweep**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/data/repository/KnowledgeRepositoryImpl.kt app/src/test/java/com/example/personal_studio/data/repository/KnowledgeRepositoryDraftTest.kt
git commit -m "kb(data): draftFromSource — JSON parse + retry + fallback (TDD)"
```

---

### Task 16: `SaveToKnowledgeUseCase`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/knowledge/SaveToKnowledgeUseCase.kt`
- Create: `app/src/test/java/com/example/personal_studio/domain/knowledge/SaveToKnowledgeUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveToKnowledgeUseCaseTest {

    @Test fun draft_delegatesToRepository() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            draftToReturn = KbEntryDraft(
                source = KbDraftSource.FromChatMessage(1, 2),
                title = "T", categorySuggestion = "数学",
                standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
                relatedEntryTitles = emptyList(), originalImagePath = null,
            )
        }
        val useCase = SaveToKnowledgeUseCase(repo)
        val draft = useCase.draft(KbDraftSource.FromChatMessage(1, 2))
        assertEquals("T", draft.title)
        assertEquals(KbDraftSource.FromChatMessage(1, 2), repo.lastDraftSource)
    }

    @Test fun commit_delegatesToRepositoryAndReturnsId() = runTest {
        val repo = FakeKnowledgeRepository().apply { savedEntryIdToReturn = 42L }
        val draft = KbEntryDraft(
            source = KbDraftSource.FromChatMessage(1, 2),
            title = "T", categorySuggestion = "数学",
            standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
            relatedEntryTitles = emptyList(), originalImagePath = null,
        )
        val useCase = SaveToKnowledgeUseCase(repo)
        val id = useCase.commit(draft)
        assertEquals(42L, id)
        assertEquals(draft, repo.lastSavedDraft)
    }
}
```

- [ ] **Step 2: Write `FakeKnowledgeRepository`**

```kotlin
package com.example.personal_studio.data.repository

import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeKnowledgeRepository : KnowledgeRepository {
    var allEntries = MutableStateFlow<List<KbEntry>>(emptyList())
    var mistakes = MutableStateFlow<List<KbEntry>>(emptyList())
    var entryFlow = MutableStateFlow<KbEntry?>(null)
    var related = MutableStateFlow<List<KbEntry>>(emptyList())
    var categories = MutableStateFlow<List<KbCategory>>(emptyList())
    var notesCount = MutableStateFlow(0)
    var mistakesCount = MutableStateFlow(0)
    var categoryCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    var searchResults = MutableStateFlow<List<KbEntry>>(emptyList())
    var orSearchResults: List<KbEntry> = emptyList()

    var draftToReturn: KbEntryDraft? = null
    var lastDraftSource: KbDraftSource? = null
    var savedEntryIdToReturn: Long = 1L
    var lastSavedDraft: KbEntryDraft? = null
    var lastUpdatedEntry: KbEntry? = null
    var lastDeletedId: Long? = null
    var upsertedCategories: MutableList<String> = mutableListOf()

    override fun observeAllEntries(categoryId: Long?, notesOnly: Boolean): Flow<List<KbEntry>> = allEntries
    override fun observeMistakes(): Flow<List<KbEntry>> = mistakes
    override fun observeEntry(id: Long): Flow<KbEntry?> = entryFlow
    override fun observeRelated(id: Long): Flow<List<KbEntry>> = related
    override fun observeCategories(): Flow<List<KbCategory>> = categories
    override fun observeNotesCount(): Flow<Int> = notesCount
    override fun observeMistakesCount(): Flow<Int> = mistakesCount
    override fun observeCategoryCounts(): Flow<Map<Long, Int>> = categoryCounts
    override fun search(query: String): Flow<List<KbEntry>> = searchResults
    override suspend fun searchOr(query: String): List<KbEntry> = orSearchResults

    override suspend fun saveEntry(draft: KbEntryDraft): Long {
        lastSavedDraft = draft
        return savedEntryIdToReturn
    }
    override suspend fun updateEntry(entry: KbEntry) { lastUpdatedEntry = entry }
    override suspend fun deleteEntry(id: Long) { lastDeletedId = id }
    override suspend fun upsertCategory(name: String): Long {
        upsertedCategories += name
        return name.hashCode().toLong()
    }
    override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft {
        lastDraftSource = source
        return draftToReturn ?: error("draftToReturn not configured")
    }
}
```

- [ ] **Step 3: Verify tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.knowledge.SaveToKnowledgeUseCaseTest"`
Expected: FAILED — `SaveToKnowledgeUseCase` unresolved.

- [ ] **Step 4: Write the use case**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import javax.inject.Inject

/**
 * Two-phase: [draft] resolves the draft (LLM call), [commit] persists user-edited result.
 */
class SaveToKnowledgeUseCase @Inject constructor(
    private val repo: KnowledgeRepository,
) {
    suspend fun draft(source: KbDraftSource): KbEntryDraft = repo.draftFromSource(source)
    suspend fun commit(draft: KbEntryDraft): Long = repo.saveEntry(draft)
}
```

- [ ] **Step 5: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.knowledge.SaveToKnowledgeUseCaseTest"`
Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/knowledge/SaveToKnowledgeUseCase.kt app/src/test/java/com/example/personal_studio/data/repository/FakeKnowledgeRepository.kt app/src/test/java/com/example/personal_studio/domain/knowledge/SaveToKnowledgeUseCaseTest.kt
git commit -m "kb(domain): SaveToKnowledgeUseCase + FakeKnowledgeRepository"
```

---

### Task 17: Phase 2 verification — manual LLM ping

**Files:** none

- [ ] **Step 1: Build green**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 2: Add a temporary debug call in `MainActivity.onCreate` to ping the LLM**

In `MainActivity.kt`, inside the `Hilt` injection scope (after the `setContent` block), append (and revert before commit at end of phase):

```kotlin
// TEMP — Phase 2 verification only; revert before merge.
@Inject lateinit var debugRepo: com.example.personal_studio.data.repository.KnowledgeRepository
// In onCreate, after setContent:
lifecycleScope.launch {
    val draft = debugRepo.draftFromSource(
        com.example.personal_studio.domain.model.KbDraftSource.FromChatSession(/* sessionId = */ 1L),
    )
    android.util.Log.i("KbPing", "draft=$draft")
}
```

> Skip this step if there's no real chat session to draft from. Otherwise: `./gradlew :app:installDebug` + `adb logcat -c && adb logcat | grep KbPing`. Expected: a log line with the parsed draft.

- [ ] **Step 3: Revert the temp ping**

Manually delete the lines added in Step 2.

- [ ] **Step 4: Tag**

```bash
git tag p3-phase2
```

---

## Phase 3 — Add-to-KB · Chat Per-Message

Goal: ChatDetailScreen's AI bubble exposes `[+ archive]` after streaming completes. Tap → `SavePreviewModal` opens with Loading → Preview → save. After save, navigates to `KbEntryDetailScreen` (placeholder until Phase 4).

### Task 18: `SaveToKnowledgeViewModel` + states (TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/SaveToKnowledgeViewModel.kt`
- Create: `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/SaveToKnowledgeViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.knowledge.SaveToKnowledgeUseCase
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveToKnowledgeViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startDraft_emitsLoadingThenPreview() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            draftToReturn = sampleDraft()
        }
        val vm = SaveToKnowledgeViewModel(SaveToKnowledgeUseCase(repo))

        vm.uiState.test {
            assertEquals(SaveToKnowledgeUiState.Idle, awaitItem())
            vm.startDraft(KbDraftSource.FromChatMessage(1, 2))
            assertTrue(awaitItem() is SaveToKnowledgeUiState.Loading)
            val preview = awaitItem() as SaveToKnowledgeUiState.Preview
            assertEquals("T", preview.draft.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun startDraft_networkError_emitsErrorState() = runTest {
        val repo = object : FakeKnowledgeRepository() {
            override suspend fun draftFromSource(source: KbDraftSource): KbEntryDraft {
                throw java.io.IOException("net down")
            }
        }
        val vm = SaveToKnowledgeViewModel(SaveToKnowledgeUseCase(repo))
        vm.uiState.test {
            assertEquals(SaveToKnowledgeUiState.Idle, awaitItem())
            vm.startDraft(KbDraftSource.FromChatMessage(1, 2))
            assertTrue(awaitItem() is SaveToKnowledgeUiState.Loading)
            val err = awaitItem() as SaveToKnowledgeUiState.Error
            assertTrue(err.message.contains("net down"))
        }
    }

    @Test fun commit_emitsSavingThenSavedWithEntryId() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            draftToReturn = sampleDraft()
            savedEntryIdToReturn = 99L
        }
        val vm = SaveToKnowledgeViewModel(SaveToKnowledgeUseCase(repo))
        vm.startDraft(KbDraftSource.FromChatMessage(1, 2))
        vm.uiState.test {
            // Drain the latest Preview
            val preview = awaitItem() as SaveToKnowledgeUiState.Preview
            vm.commit(preview.draft)
            assertTrue(awaitItem() is SaveToKnowledgeUiState.Saving)
            val saved = awaitItem() as SaveToKnowledgeUiState.Saved
            assertEquals(99L, saved.entryId)
        }
    }

    private fun sampleDraft() = KbEntryDraft(
        source = KbDraftSource.FromChatMessage(1, 2),
        title = "T", categorySuggestion = "数学",
        standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
        relatedEntryTitles = emptyList(), originalImagePath = null,
    )
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeViewModelTest"`
Expected: FAILED — `SaveToKnowledgeViewModel` unresolved.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.domain.knowledge.SaveToKnowledgeUseCase
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SaveToKnowledgeUiState {
    data object Idle : SaveToKnowledgeUiState
    data object Loading : SaveToKnowledgeUiState
    data class Preview(val draft: KbEntryDraft) : SaveToKnowledgeUiState
    data object Saving : SaveToKnowledgeUiState
    data class Saved(val entryId: Long) : SaveToKnowledgeUiState
    data class Error(val message: String, val canRetry: Boolean = true) : SaveToKnowledgeUiState
}

class SaveToKnowledgeViewModel @AssistedInject constructor(
    private val useCase: SaveToKnowledgeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SaveToKnowledgeUiState>(SaveToKnowledgeUiState.Idle)
    val uiState: StateFlow<SaveToKnowledgeUiState> = _uiState.asStateFlow()

    fun startDraft(source: KbDraftSource) {
        _uiState.value = SaveToKnowledgeUiState.Loading
        viewModelScope.launch {
            try {
                val draft = useCase.draft(source)
                _uiState.value = SaveToKnowledgeUiState.Preview(draft)
            } catch (t: Throwable) {
                _uiState.value = SaveToKnowledgeUiState.Error(t.message ?: "Unknown error")
            }
        }
    }

    fun commit(draft: KbEntryDraft) {
        _uiState.value = SaveToKnowledgeUiState.Saving
        viewModelScope.launch {
            try {
                val id = useCase.commit(draft)
                _uiState.value = SaveToKnowledgeUiState.Saved(id)
            } catch (t: Throwable) {
                _uiState.value = SaveToKnowledgeUiState.Error(t.message ?: "Save failed")
            }
        }
    }

    fun retry(source: KbDraftSource) = startDraft(source)

    /** Reset to Idle so the modal can be safely reopened. */
    fun reset() { _uiState.value = SaveToKnowledgeUiState.Idle }

    @AssistedFactory
    interface Factory {
        fun create(): SaveToKnowledgeViewModel
    }

    companion object {
        // Keeping AssistedFactory shape consistent with existing ChatDetailViewModel pattern,
        // even though no per-instance arg is needed today (future-proof for source-specific scoping).
        fun unused() = Unit
    }
}
```

- [ ] **Step 4: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeViewModelTest"`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/vm/SaveToKnowledgeViewModel.kt app/src/test/java/com/example/personal_studio/feature/knowledge/vm/SaveToKnowledgeViewModelTest.kt
git commit -m "kb(vm): SaveToKnowledgeViewModel with Idle/Loading/Preview/Saving/Saved/Error"
```

---

### Task 19: `SavePreviewModal` Composable

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/SavePreviewModal.kt`

- [ ] **Step 1: Write the modal**

```kotlin
package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

/**
 * Full-screen modal for previewing + editing the AI-generated KB draft before commit.
 * Caller hosts the SaveToKnowledgeViewModel and provides:
 *  - [state] from vm.uiState
 *  - [onCancel] dismiss without saving
 *  - [onConfirm] commits the (possibly edited) draft
 *  - [onRetry] re-runs the LLM call (only meaningful in Error state)
 */
@Composable
fun SavePreviewModal(
    state: SaveToKnowledgeUiState,
    onCancel: () -> Unit,
    onConfirm: (KbEntryDraft) -> Unit,
    onRetry: () -> Unit,
) {
    if (state is SaveToKnowledgeUiState.Idle || state is SaveToKnowledgeUiState.Saved) return

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().background(Void)) {
            when (state) {
                is SaveToKnowledgeUiState.Loading -> Loading()
                is SaveToKnowledgeUiState.Saving -> Saving()
                is SaveToKnowledgeUiState.Error -> ErrorBlock(state.message, onRetry, onCancel)
                is SaveToKnowledgeUiState.Preview -> PreviewBody(state.draft, onCancel, onConfirm)
                else -> {}
            }
        }
    }
}

@Composable private fun Loading() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Phosphor)
        Spacer(Modifier.height(16.dp))
        Text("$ thinking...", color = FoamDim)
    }
}

@Composable private fun Saving() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Phosphor)
        Spacer(Modifier.height(16.dp))
        Text("$ writing entry...", color = FoamDim)
    }
}

@Composable private fun ErrorBlock(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("! llm error: $message", color = Carmine)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("[retry]", color = Phosphor, modifier = Modifier.padding(8.dp).clickableText { onRetry() })
            Text("[cancel]", color = FoamDim, modifier = Modifier.padding(8.dp).clickableText { onCancel() })
        }
    }
}

@Composable
private fun PreviewBody(initial: KbEntryDraft, onCancel: () -> Unit, onConfirm: (KbEntryDraft) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.categorySuggestion) }
    var standardizedQuestion by remember { mutableStateOf(initial.standardizedQuestion.orEmpty()) }
    var summary by remember { mutableStateOf(initial.summaryMarkdown) }
    var editingSummaryRaw by remember { mutableStateOf(false) }
    var related by remember { mutableStateOf(initial.relatedEntryTitles) }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(Void).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "cancel", tint = FoamDim)
            }
            Spacer(Modifier.width(8.dp))
            Text("archive draft", style = MaterialTheme.typography.titleMedium, color = Foam, modifier = Modifier.weight(1f))
            Text(
                "[save]",
                color = Phosphor,
                modifier = Modifier.padding(8.dp).clickableText {
                    onConfirm(
                        initial.copy(
                            title = title.trim().ifBlank { initial.title },
                            categorySuggestion = category.trim().ifBlank { "其它" },
                            standardizedQuestion = standardizedQuestion.takeIf { it.isNotBlank() },
                            summaryMarkdown = summary,
                            relatedEntryTitles = related,
                        ),
                    )
                },
            )
        }

        // Fallback banner
        if (initial.isFallback) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Amber)) { append("⚠ ") }
                    withStyle(SpanStyle(color = Foam)) {
                        append(when (initial.fallbackReason) {
                            KbDraftFallbackReason.JSON_PARSE_FAILED -> "AI 解析失败，已用原文兜底，请检查"
                            KbDraftFallbackReason.MISSING_REQUIRED_FIELDS -> "AI 输出字段缺失，已用原文兜底，请检查"
                            null -> "已用 fallback，请检查"
                        })
                    }
                },
                modifier = Modifier.fillMaxWidth().background(Void).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { FieldLabel("title"); LineField(title) { title = it } }
            item { FieldLabel("category"); LineField(category) { category = it } }
            if (!standardizedQuestion.isBlank() || initial.standardizedQuestion != null) {
                item {
                    FieldLabel("standardized question")
                    MultilineField(standardizedQuestion) { standardizedQuestion = it }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel("summary", Modifier.weight(1f))
                    Text(
                        if (editingSummaryRaw) "[preview]" else "[edit raw markdown]",
                        color = FoamDim,
                        modifier = Modifier.padding(8.dp).clickableText { editingSummaryRaw = !editingSummaryRaw },
                    )
                }
                if (editingSummaryRaw) MultilineField(summary) { summary = it }
                else MathMarkdownView(markdown = summary, modifier = Modifier.fillMaxWidth())
            }
            if (related.isNotEmpty()) {
                item { FieldLabel("related (AI-suggested)") }
                items(related) { title ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("▎ $title", color = Foam, modifier = Modifier.weight(1f))
                        Text("✕", color = Carmine, modifier = Modifier.padding(8.dp).clickableText { related = related - title })
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        "$ $text",
        color = FoamDim,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable private fun LineField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
        cursorBrush = SolidColor(Phosphor),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
}

@Composable private fun MultilineField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
        cursorBrush = SolidColor(Phosphor),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(Modifier.padding(0.dp)).then(Modifier.then(Modifier).then(Modifier.then(Modifier))).also { /* noop */ }
        .let { it } // kept simple — caller passes directly to Text.modifier
        .let { it } // placeholder; real impl follows:
        .let { it.then(Modifier) }

// Real clickable helper (replace the noop above with this — kept separate for clarity in plan):
// fun Modifier.clickableText(onClick: () -> Unit) = this.then(
//     androidx.compose.foundation.clickable(
//         interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
//         indication = null,
//         onClick = onClick,
//     )
// )
```

> Note: replace the `clickableText` stub at the bottom of the file with the real Compose clickable helper before finishing the task — see the comment block. The stub exists only to keep the listing self-contained; if you copy the file verbatim, run a `grep -n clickableText` after and rewrite the implementation as:
> ```kotlin
> @Composable
> private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
>     this.then(androidx.compose.foundation.clickable(onClick = onClick))
> ```

Replace the placeholder lines accordingly, then re-run compile.

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/SavePreviewModal.kt
git commit -m "kb(ui): SavePreviewModal — Loading/Saving/Error/Preview states with editable fields"
```

---

### Task 20: NavRoutes + AppNavHost stub for `KB_DETAIL`

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt`
- Modify: `app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`

- [ ] **Step 1: Add KB routes**

Append to `NavRoutes` object (after the existing `SCANNER_ENHANCE` definition):

```kotlin
    // Knowledge Base routes (P3)
    const val KB_MISTAKES = "knowledge/mistakes"
    const val KB_DETAIL = "knowledge/detail/{entryId}"
    fun kbDetail(entryId: Long) = "knowledge/detail/$entryId"
```

- [ ] **Step 2: Register destinations in `AppNavHost`**

Run: `git grep -n "KNOWLEDGE" app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt`
Find the existing `composable(NavRoutes.KNOWLEDGE) { KnowledgePlaceholder() }` line and replace it with a stub that we'll flesh out in Phase 4:

```kotlin
        composable(NavRoutes.KNOWLEDGE) {
            // Phase 4 replaces this with KbHomeScreen.
            KnowledgePlaceholder()
        }
        composable(NavRoutes.KB_MISTAKES) {
            // Phase 5 replaces this.
            KnowledgePlaceholder()
        }
        composable(
            route = NavRoutes.KB_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("entryId") ?: 0L
            // Phase 4 replaces this with KbEntryDetailScreen(id).
            androidx.compose.material3.Text("kb entry $id (placeholder)")
        }
```

If the imports `navArgument` / `NavType` aren't already present in the file, add at the top:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/ui/navigation/NavRoutes.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(nav): KB_MISTAKES + KB_DETAIL routes (placeholders)"
```

---

### Task 21: Wire `[+ archive]` into `ChatDetailScreen` AI bubbles

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt`

- [ ] **Step 1: Locate the AI bubble Composable**

Run: `git grep -n "fun AiMessageBubble\|MessageRole.AI\|role == MessageRole.AI" app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt`

Identify where AI messages are rendered. Typical pattern: a `MessageBubble(message)` Composable inside the `LazyColumn`, with branching on `message.role`.

- [ ] **Step 2: Add navigation + ViewModel hook to `ChatDetailScreen`**

In the `ChatDetailScreen(sessionId: Long, onBack: () -> Unit)` signature, append a new param:

```kotlin
fun ChatDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onNavigateToKbEntry: (Long) -> Unit,    // NEW
)
```

Inside `ChatDetailScreen`, hoist a `SaveToKnowledgeViewModel`:

```kotlin
val saveVm: com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeViewModel = hiltViewModel()
val saveState by saveVm.uiState.collectAsStateWithLifecycle()
val activeSourceState = remember { mutableStateOf<com.example.personal_studio.domain.model.KbDraftSource?>(null) }
```

After the main `Scaffold` body, render the modal:

```kotlin
com.example.personal_studio.feature.knowledge.ui.SavePreviewModal(
    state = saveState,
    onCancel = { saveVm.reset() },
    onConfirm = { draft -> saveVm.commit(draft) },
    onRetry = { activeSourceState.value?.let { saveVm.retry(it) } },
)
LaunchedEffect(saveState) {
    val s = saveState
    if (s is com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState.Saved) {
        onNavigateToKbEntry(s.entryId)
        saveVm.reset()
    }
}
```

- [ ] **Step 3: Add `[+ archive]` row under each AI bubble**

Inside the AI-message branch in the `LazyColumn`, append the archive button below the bubble (only when streaming completed — i.e. message id is real and `state.streamingMessageId != message.id`):

```kotlin
if (message.role == MessageRole.AI && state.streamingMessageId != message.id) {
    Row(
        Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "[+ archive]",
            color = Phosphor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .clickable {
                    val src = com.example.personal_studio.domain.model.KbDraftSource
                        .FromChatMessage(sessionId = sessionId, aiMessageId = message.id)
                    activeSourceState.value = src
                    saveVm.startDraft(src)
                }
                .padding(8.dp),
        )
    }
}
```

> If the existing code does not expose `streamingMessageId`, use whatever flag the ViewModel surfaces for "this message is the in-flight stream". Look for `state.streamingMessage` / `isStreaming` and adapt the predicate.

- [ ] **Step 4: Update the call site in `AppNavHost`**

In `AppNavHost`, where `ChatDetailScreen(sessionId = …, onBack = …)` is wired (under `composable(NavRoutes.CHAT_DETAIL)`), pass:

```kotlin
ChatDetailScreen(
    sessionId = id,
    onBack = { navController.popBackStack() },
    onNavigateToKbEntry = { entryId -> navController.navigate(NavRoutes.kbDetail(entryId)) },
)
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(chat): [+ archive] under each AI bubble — opens SavePreviewModal"
```

---

### Task 22: Phase 3 verification — manual smoke

**Files:** none

- [ ] **Step 1: Install + open chat session**

Run: `./gradlew :app:installDebug && adb shell am start -n com.example.personal_studio/.MainActivity`
On device: open the `chat` tab → tap an existing session → ask the AI a question if no AI replies exist yet → wait for streaming to complete.

- [ ] **Step 2: Tap `[+ archive]` under the AI bubble**

Expected: full-screen `SavePreviewModal` opens with a phosphor spinner + `$ thinking...`. Within ~5–15 s the LLM returns and the modal renders the editable preview with title/category/summary/related fields.

- [ ] **Step 3: Edit the title, hit `[save]`**

Expected: modal shows `$ writing entry...` for ~1 s then closes; navigation pushes to `knowledge/detail/<id>` placeholder which renders `kb entry <id> (placeholder)`.

- [ ] **Step 4: Verify a row was inserted in `kb_entries`**

Use Android Studio Database Inspector → `kb_entries` table.
Expected: 1 row with `sourceType = CHAT_MESSAGE`, your edited title, `summaryMarkdown` non-empty.

- [ ] **Step 5: Tag**

```bash
git tag p3-phase3
```

---

## Phase 4 — KbHomeScreen + KbEntryDetailScreen Basic

Goal: Tap kb tab → see notes count + mistakes count + category chip row + recent entries list. Tap entry → see detail with metadata + 5-section KaTeX-rendered summary + back-link to source. No edit/regenerate yet (Phase 6).

### Task 23: `KbHomeViewModel` + state (TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModel.kt`
- Create: `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import app.cash.turbine.test
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KbHomeViewModelTest {

    private val now = System.currentTimeMillis()

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun mkEntry(id: Long, catId: Long? = 1, mistake: Boolean = false) = KbEntry(
        id = id, title = "T$id", categoryId = catId, categoryName = "数学",
        source = KbSource.CHAT_MESSAGE, sourceChatMessageId = id, sourceChatSessionId = 1,
        sourceScanPageId = null, originalImagePath = null,
        standardizedQuestion = if (mistake) "Q" else null,
        summaryMarkdown = "## 核心概念\n", createdAt = now, updatedAt = now,
    )

    @Test fun observesEntries_categories_and_counts() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.allEntries.value = listOf(mkEntry(1), mkEntry(2, mistake = true))
        repo.categories.value = listOf(KbCategory(1, "数学", true), KbCategory(2, "物理", true))
        repo.notesCount.value = 1
        repo.mistakesCount.value = 1
        repo.categoryCounts.value = mapOf(1L to 1, 2L to 0)

        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            // initial Idle / Loading not used — start with the combined first emission
            val s = awaitItem()
            assertEquals(1, s.notesCount)
            assertEquals(1, s.mistakesCount)
            assertEquals(2, s.categories.size)
            assertEquals(2, s.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun toggleNotesOnly_filtersList() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.categories.value = emptyList()
        repo.allEntries.value = listOf(mkEntry(1), mkEntry(2, mistake = true))

        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            awaitItem()
            vm.onToggleNotes()
            // The fake re-emits whenever `allEntries` changes; here we just verify the flag flipped.
            val s = awaitItem()
            assertTrue(s.showNotes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun selectCategory_setsFilter() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.categories.value = listOf(KbCategory(1, "数学", true))
        val vm = KbHomeViewModel(repo)
        vm.uiState.test {
            awaitItem()
            vm.onSelectCategory(1L)
            val s = awaitItem()
            assertEquals(1L, s.selectedCategoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.KbHomeViewModelTest"`
Expected: FAILED — `KbHomeViewModel` unresolved.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryWithCount(val category: KbCategory, val count: Int)

data class KbHomeUiState(
    val searchQuery: String = "",
    val selectedCategoryId: Long? = null,
    val showNotes: Boolean = true,
    val notesCount: Int = 0,
    val mistakesCount: Int = 0,
    val categories: List<CategoryWithCount> = emptyList(),
    val entries: List<KbEntry> = emptyList(),
    val isSearching: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class KbHomeViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val showNotes = MutableStateFlow(true)

    /** When searchQuery is blank we observe browse-mode list; otherwise FTS results. */
    private val entriesFlow = combine(
        searchQuery.debounce(150).distinctUntilChanged(),
        selectedCategoryId,
        showNotes,
    ) { q, catId, notes -> Triple(q, catId, notes) }
        .flatMapLatest { (q, catId, notes) ->
            if (q.isBlank()) repo.observeAllEntries(catId, notesOnly = notes)
            else repo.search(q)
        }

    val uiState: StateFlow<KbHomeUiState> = combine(
        searchQuery,
        selectedCategoryId,
        showNotes,
        repo.observeNotesCount(),
        repo.observeMistakesCount(),
        repo.observeCategories(),
        repo.observeCategoryCounts(),
        entriesFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val q = values[0] as String
        val catId = values[1] as Long?
        val notes = values[2] as Boolean
        val notesCount = values[3] as Int
        val mistakesCount = values[4] as Int
        val cats = values[5] as List<KbCategory>
        val counts = values[6] as Map<Long, Int>
        val entries = values[7] as List<KbEntry>
        KbHomeUiState(
            searchQuery = q,
            selectedCategoryId = catId,
            showNotes = notes,
            notesCount = notesCount,
            mistakesCount = mistakesCount,
            categories = cats.map { CategoryWithCount(it, counts[it.id] ?: 0) },
            entries = entries,
            isSearching = q.isNotBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbHomeUiState())

    fun onSearchChange(q: String) { searchQuery.value = q }
    fun onSelectCategory(id: Long?) { selectedCategoryId.value = id }
    fun onToggleNotes() { showNotes.value = !showNotes.value }

    /** When AND-search returns < 5, fall back to OR for one-shot rescue. */
    fun rescueSearchIfSparse() {
        val q = searchQuery.value
        if (q.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.entries
            if (current.size < 5) {
                val or = repo.searchOr(q)
                if (or.size > current.size) {
                    // Surface OR results; we don't change underlying searchFlow but expose via a
                    // separate setter. For MVP, we just emit a "rescued" entries override.
                    _rescuedEntries.value = or
                }
            }
        }
    }
    private val _rescuedEntries = MutableStateFlow<List<KbEntry>?>(null)
    val rescuedEntries: StateFlow<List<KbEntry>?> = _rescuedEntries.asStateFlow()
}
```

> Implementation note: pass `notesOnly = showNotes` directly (no inversion). The DAO predicate is `(:notesOnly = 0 OR standardizedQuestion IS NULL)`, so `notesOnly=true` filters to rows with NULL standardizedQuestion (mistakes hidden) and `notesOnly=false` shows everything — exactly what `showNotes` means at the UI layer.

- [ ] **Step 4: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.KbHomeViewModelTest"`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModel.kt app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModelTest.kt
git commit -m "kb(vm): KbHomeViewModel — categories + counts + search + filter"
```

---

### Task 24: KbHome shared components

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbSearchBar.kt`
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryChipRow.kt`
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbEntryRow.kt`

- [ ] **Step 1: Write `KbSearchBar`**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun KbSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = FoamDim)) { append("$ grep -r ") }
                withStyle(SpanStyle(color = Phosphor)) { append("\"") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
            cursorBrush = SolidColor(Phosphor),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("\"") }
                withStyle(SpanStyle(color = FoamDim)) { append(" kb/") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
```

- [ ] **Step 2: Write `CategoryChipRow`**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.knowledge.vm.CategoryWithCount
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun CategoryChipRow(
    items: List<CategoryWithCount>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Chip(label = "全部", selected = selectedId == null) { onSelect(null) }
        items.forEach { (cat, count) ->
            Chip(label = "${cat.name} $count", selected = selectedId == cat.id) { onSelect(cat.id) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Phosphor else FoamDim
    val text = if (selected) Phosphor else Foam
    Text(
        "[$label]",
        color = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .background(Void)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 3: Write `KbEntryRow`**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KbEntryRow(entry: KbEntry, onClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick(entry.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▎", color = Phosphor)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                buildAnnotatedString {
                    if (entry.isMistake) {
                        withStyle(SpanStyle(color = Cyan)) { append("⊕ ") }
                    }
                    withStyle(SpanStyle(color = Foam)) { append(entry.title) }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = FoamDim)) {
                        append(entry.categoryName ?: "其它")
                        append(" · ")
                        append(formatRelative(entry.createdAt))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatRelative(ts: Long): String {
    val diff = (System.currentTimeMillis() - ts) / 1000
    return when {
        diff < 60 -> "${diff}s前"
        diff < 3600 -> "${diff / 60}m前"
        diff < 86400 -> "${diff / 3600}h前"
        diff < 604800 -> "${diff / 86400}d前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    }
}
```

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbSearchBar.kt app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryChipRow.kt app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbEntryRow.kt
git commit -m "kb(ui): components — KbSearchBar, CategoryChipRow, KbEntryRow"
```

---

### Task 25: `KbHomeScreen`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbHomeScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.knowledge.ui.components.CategoryChipRow
import com.example.personal_studio.feature.knowledge.ui.components.KbEntryRow
import com.example.personal_studio.feature.knowledge.ui.components.KbSearchBar
import com.example.personal_studio.feature.knowledge.vm.KbHomeViewModel
import com.example.personal_studio.ui.placeholder.KnowledgePlaceholder
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun KbHomeScreen(
    onOpenEntry: (Long) -> Unit,
    onOpenMistakes: () -> Unit,
) {
    val vm: KbHomeViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            KbSearchBar(query = state.searchQuery, onQueryChange = vm::onSearchChange)

            // Top stats row: [notes N] [mistakes N]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip("notes", state.notesCount, selected = state.showNotes) { vm.onToggleNotes() }
                Spacer(Modifier.width(12.dp))
                StatChip("mistakes", state.mistakesCount, selected = false) { onOpenMistakes() }
            }

            CategoryChipRow(
                items = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = vm::onSelectCategory,
            )

            Text(
                "─────────── ${if (state.isSearching) "matches" else "recent"} ───────────",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
            )

            if (state.entries.isEmpty()) {
                KnowledgePlaceholder()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.id }) { e ->
                        KbEntryRow(entry = e, onClick = onOpenEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Phosphor else Foam
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = color)) { append("[$label] ") }
            withStyle(SpanStyle(color = if (selected) Phosphor else FoamDim)) { append(count.toString()) }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    )
}
```

- [ ] **Step 2: Wire into `AppNavHost`**

In `AppNavHost.kt`, replace the placeholder for `composable(NavRoutes.KNOWLEDGE)` with:

```kotlin
        composable(NavRoutes.KNOWLEDGE) {
            com.example.personal_studio.feature.knowledge.ui.KbHomeScreen(
                onOpenEntry = { id -> navController.navigate(NavRoutes.kbDetail(id)) },
                onOpenMistakes = { navController.navigate(NavRoutes.KB_MISTAKES) },
            )
        }
```

- [ ] **Step 3: Build + install + smoke**

Run: `./gradlew :app:installDebug`
On device: open `kb` tab. Expected: search bar + `[notes 1] [mistakes 0]` + chip row + recent list with the entry archived in Phase 3.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbHomeScreen.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(ui): KbHomeScreen — search bar + stats + category chips + entry list"
```

---

### Task 26: `KbEntryDetailViewModel` + state (TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModel.kt`
- Create: `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KbEntryDetailViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun emitsEntry_whenRepoEmits() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            entryFlow.value = KbEntry(
                id = 7, title = "T", categoryId = 1, categoryName = "数学",
                source = KbSource.CHAT_MESSAGE, sourceChatMessageId = 1, sourceChatSessionId = 1,
                sourceScanPageId = null, originalImagePath = null,
                standardizedQuestion = null, summaryMarkdown = "## 核心概念\n",
                createdAt = 0, updatedAt = 0,
            )
        }
        val vm = KbEntryDetailViewModel(repo, SavedStateHandle(mapOf("entryId" to 7L)))
        kotlinx.coroutines.delay(50)
        val s = vm.uiState.value
        assertTrue(s is KbEntryDetailUiState.Loaded)
        assertEquals("T", (s as KbEntryDetailUiState.Loaded).entry.title)
    }
}
```

- [ ] **Step 2: Write the ViewModel**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface KbEntryDetailUiState {
    data object Loading : KbEntryDetailUiState
    data class Loaded(val entry: KbEntry, val related: List<KbEntry>) : KbEntryDetailUiState
    data object NotFound : KbEntryDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KbEntryDetailViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: 0L

    val uiState: StateFlow<KbEntryDetailUiState> = combine(
        repo.observeEntry(entryId),
        repo.observeRelated(entryId),
    ) { entry, related ->
        if (entry == null) KbEntryDetailUiState.NotFound
        else KbEntryDetailUiState.Loaded(entry, related)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbEntryDetailUiState.Loading)
}
```

- [ ] **Step 3: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.KbEntryDetailViewModelTest"`
Expected: 1 test passes.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModel.kt app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModelTest.kt
git commit -m "kb(vm): KbEntryDetailViewModel — observes entry + related"
```

---

### Task 27: `RelatedEntriesSection` component

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/RelatedEntriesSection.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

@Composable
fun RelatedEntriesSection(
    related: List<KbEntry>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (related.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("─────── 相关条目 ───────", color = FoamDim, style = MaterialTheme.typography.bodySmall)
        related.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▎", color = Phosphor)
                Text(entry.title, color = Foam, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Text("→", color = Phosphor)
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/RelatedEntriesSection.kt
git commit -m "kb(ui): RelatedEntriesSection — clickable list of linked entries"
```

---

### Task 28: `KbEntryDetailScreen` (basic — normal variant only; mistakes variant is Task 33)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import com.example.personal_studio.feature.knowledge.ui.components.RelatedEntriesSection
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailUiState
import com.example.personal_studio.feature.knowledge.vm.KbEntryDetailViewModel
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun KbEntryDetailScreen(
    onBack: () -> Unit,
    onOpenSource: (KbEntry) -> Unit,
    onOpenRelated: (Long) -> Unit,
) {
    val vm: KbEntryDetailViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            TerminalTopBar(
                route = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty().ifBlank { "kb/" },
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                    }
                },
            )
            when (val s = state) {
                is KbEntryDetailUiState.Loading -> Centered { CircularProgressIndicator(color = Phosphor) }
                is KbEntryDetailUiState.NotFound -> Centered { Text("! entry not found", color = Carmine) }
                is KbEntryDetailUiState.Loaded -> Loaded(s, onOpenSource, onOpenRelated)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

@Composable
private fun Loaded(state: KbEntryDetailUiState.Loaded, onOpenSource: (KbEntry) -> Unit, onOpenRelated: (Long) -> Unit) {
    val e = state.entry
    LazyColumn(Modifier.fillMaxSize()) {
        item { MetadataRow(e, onOpenSource) }
        item {
            // Phase 5 will overlay an image+question header here when e.isMistake.
            MathMarkdownView(markdown = e.summaryMarkdown, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
        item { RelatedEntriesSection(related = state.related, onOpen = onOpenRelated) }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun MetadataRow(e: KbEntry, onOpenSource: (KbEntry) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("▎") }
                withStyle(SpanStyle(color = Foam)) { append(" ${e.categoryName ?: "其它"} · ") }
                withStyle(SpanStyle(color = if (e.isMistake) Cyan else FoamDim)) {
                    append(if (e.isMistake) "错题" else e.source.name)
                }
                withStyle(SpanStyle(color = FoamDim)) { append(" · 来自: ") }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            "[↗]",
            color = Phosphor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp).then(Modifier.then(Modifier))
                .let { it } // see clickable note below
                .let { it.then(Modifier) },
        )
    }
}
```

> Replace the chained `.let { it }` placeholder on the `[↗]` Text with a real `Modifier.clickable { onOpenSource(e) }`. Keep the listing self-contained but final code:
> ```kotlin
> Text(
>     "[↗]",
>     color = Phosphor,
>     style = MaterialTheme.typography.bodySmall,
>     modifier = Modifier
>         .clickable { onOpenSource(e) }
>         .padding(8.dp),
> )
> ```

- [ ] **Step 2: Wire into `AppNavHost`**

Replace the placeholder `composable(NavRoutes.KB_DETAIL)` block with:

```kotlin
        composable(
            route = NavRoutes.KB_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
        ) {
            com.example.personal_studio.feature.knowledge.ui.KbEntryDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenSource = { entry ->
                    when (entry.source) {
                        com.example.personal_studio.domain.model.KbSource.CHAT_MESSAGE,
                        com.example.personal_studio.domain.model.KbSource.CHAT_SESSION ->
                            entry.sourceChatSessionId?.let { sid ->
                                navController.navigate(NavRoutes.chatDetail(sid))
                            }
                        com.example.personal_studio.domain.model.KbSource.SCAN ->
                            entry.sourceScanPageId?.let { pid ->
                                // resolve to docId in Phase 5; for now jump to scanner library.
                                navController.navigate(NavRoutes.SCANNER)
                            }
                    }
                },
                onOpenRelated = { id -> navController.navigate(NavRoutes.kbDetail(id)) },
            )
        }
```

- [ ] **Step 3: Build + smoke**

Run: `./gradlew :app:installDebug`
Tap entry on KbHome → detail loads with category, source label, KaTeX-rendered summary.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(ui): KbEntryDetailScreen basic — meta + 5-section summary + related"
```

---

### Task 29: Phase 4 verification

**Files:** none

- [ ] **Step 1: Build green + tests green**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 2: Manual smoke on device**

1. Open `kb` tab — see search bar + stats + chip row + recent entries list.
2. Tap a category chip — list filters; tap `[全部]` — list resets.
3. Tap an entry — detail screen opens with KaTeX-rendered 5-section summary.
4. Tap `[↗]` — navigates back to source chat session (if CHAT_MESSAGE).
5. Tap `[< back]` — returns to KbHome.

Capture screenshots into `docs/superpowers/checkpoints/P3/phase4/`.

- [ ] **Step 3: Tag**

```bash
git tag p3-phase4
```

---

## Phase 5 — Mistakes + Per-Session + Scan-Page Sources

Goal: 错题集 has its own screen with image-prominent rows. KbEntryDetail's mistakes variant shows original image + standardized question above the 5-section summary. ChatDetailScreen exposes `[+ archive session]` in the topbar trailing slot. ScanDocumentDetailScreen + PageEditScreen expose `[archive page to KB]`.

### Task 30: `KbMistakesViewModel` (TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbMistakesViewModel.kt`
- Create: `app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbMistakesViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KbMistakesViewModelTest {

    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun forwardsRepoMistakes() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            mistakes.value = listOf(
                KbEntry(1, "Q1", 1, "数学", KbSource.CHAT_MESSAGE, 1, 1, null, "/img1.jpg", "求 x", "## …", 0, 0),
                KbEntry(2, "Q2", 1, "数学", KbSource.SCAN, null, null, 9, "/img2.jpg", "证明", "## …", 0, 0),
            )
        }
        val vm = KbMistakesViewModel(repo)
        val s = vm.uiState.first { it.entries.isNotEmpty() }
        assertEquals(2, s.entries.size)
        assertEquals(setOf(1L, 2L), s.entries.map { it.id }.toSet())
    }
}
```

- [ ] **Step 2: Write the ViewModel**

```kotlin
package com.example.personal_studio.feature.knowledge.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class KbMistakesUiState(val entries: List<KbEntry> = emptyList())

@HiltViewModel
class KbMistakesViewModel @Inject constructor(
    repo: KnowledgeRepository,
) : ViewModel() {
    val uiState: StateFlow<KbMistakesUiState> = repo.observeMistakes()
        .map { KbMistakesUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbMistakesUiState())
}
```

- [ ] **Step 3: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.KbMistakesViewModelTest"`
Expected: 1 test passes.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbMistakesViewModel.kt app/src/test/java/com/example/personal_studio/feature/knowledge/vm/KbMistakesViewModelTest.kt
git commit -m "kb(vm): KbMistakesViewModel — observes mistakes-only flow"
```

---

### Task 31: `KbMistakeRow` component + image loading via Coil

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbMistakeRow.kt`
- Modify: `gradle/libs.versions.toml` (if Coil isn't already present — check first)
- Modify: `app/build.gradle.kts` (add Coil dep if missing)

- [ ] **Step 1: Verify Coil presence**

Run: `git grep -n "coil-compose" gradle/libs.versions.toml app/build.gradle.kts`
If a result is shown, skip Step 2. Otherwise, add Coil:

In `gradle/libs.versions.toml` under `[versions]`:
```toml
coil = "2.7.0"
```
Under `[libraries]`:
```toml
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
```
In `app/build.gradle.kts` under `dependencies`:
```kotlin
implementation(libs.coil.compose)
```

- [ ] **Step 2: Write `KbMistakeRow`**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void
import java.io.File

@Composable
fun KbMistakeRow(entry: KbEntry, onClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick(entry.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(80.dp).clip(RoundedCornerShape(2.dp)).background(Void),
            contentAlignment = Alignment.Center,
        ) {
            entry.originalImagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp),
                )
            } ?: Text("IMG", color = FoamDim, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(entry.title, color = Foam, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.standardizedQuestion.orEmpty(),
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.categoryName ?: "其它"} · entry #${entry.id}",
                color = Phosphor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/KbMistakeRow.kt
git commit -m "kb(ui): KbMistakeRow with thumbnail (Coil) + question first line"
```

---

### Task 32: `KbMistakesScreen`

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbMistakesScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.knowledge.ui.components.KbMistakeRow
import com.example.personal_studio.feature.knowledge.vm.KbMistakesViewModel
import com.example.personal_studio.ui.components.TerminalTopBar
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Void

@Composable
fun KbMistakesScreen(onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val vm: KbMistakesViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Void)) {
        Column(Modifier.fillMaxSize()) {
            TerminalTopBar(
                route = "kb/mistakes/",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
                    }
                },
            )
            Text(
                "$ ls mistakes/    ${state.entries.size} entries",
                color = FoamDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.entries, key = { it.id }) { e -> KbMistakeRow(e, onOpenEntry) }
            }
        }
    }
}
```

- [ ] **Step 2: Wire into `AppNavHost`**

Replace the placeholder `composable(NavRoutes.KB_MISTAKES)` with:

```kotlin
        composable(NavRoutes.KB_MISTAKES) {
            com.example.personal_studio.feature.knowledge.ui.KbMistakesScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { id -> navController.navigate(NavRoutes.kbDetail(id)) },
            )
        }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbMistakesScreen.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(ui): KbMistakesScreen — image-prominent list of standardized-question entries"
```

---

### Task 33: `KbEntryDetailScreen` mistakes variant (image + standardized question header)

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt`

- [ ] **Step 1: Add the mistake header above the 5-section summary**

In the `Loaded` Composable, replace the body item with a sequence that conditionally inserts a `MistakeHeader` block:

```kotlin
    LazyColumn(Modifier.fillMaxSize()) {
        item { MetadataRow(e, onOpenSource) }
        if (e.isMistake) {
            item { MistakeHeader(e) }
        }
        item {
            MathMarkdownView(markdown = e.summaryMarkdown, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }
        item { RelatedEntriesSection(related = state.related, onOpen = onOpenRelated) }
        item { Spacer(Modifier.height(48.dp)) }
    }
```

Add the `MistakeHeader` Composable to the same file:

```kotlin
@Composable
private fun MistakeHeader(e: KbEntry) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        e.originalImagePath?.let { path ->
            coil.compose.AsyncImage(
                model = java.io.File(path),
                contentDescription = "原图",
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text("## 题目", color = Phosphor, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        com.example.personal_studio.ui.components.MathMarkdownView(
            markdown = e.standardizedQuestion.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}
```

- [ ] **Step 2: Build + smoke**

Run: `./gradlew :app:installDebug`
Open detail of any mistake entry (you'll need one — if you don't yet, save a photo-question chat reply first).
Expected: original image at top, KaTeX-rendered standardized question, then 5-section summary.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt
git commit -m "kb(ui): KbEntryDetailScreen mistake variant — image + standardized question header"
```

---

### Task 34: `[+ archive session]` in ChatDetailScreen topbar

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt`

- [ ] **Step 1: Add the trailing button**

Locate the `TerminalTopBar(...)` call in `ChatDetailScreen`. The existing call has `leading = { IconButton(onClick = onBack) { ... } }`. Replace the `trailing` slot (or add one if absent):

```kotlin
TerminalTopBar(
    route = "chat/${state.session?.title ?: sessionId}",
    leading = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
        }
    },
    trailing = {
        Text(
            "[+ archive session]",
            color = Phosphor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .clickable {
                    val src = com.example.personal_studio.domain.model.KbDraftSource.FromChatSession(sessionId)
                    activeSourceState.value = src
                    saveVm.startDraft(src)
                }
                .padding(8.dp),
        )
    },
)
```

(Reuses `saveVm` and `activeSourceState` already added in Task 21.)

- [ ] **Step 2: Build + smoke**

Run: `./gradlew :app:installDebug`
Open a chat session → tap `[+ archive session]` → modal opens → save → KbHome shows new entry with `sourceType=CHAT_SESSION`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/chat/ui/ChatDetailScreen.kt
git commit -m "kb(chat): [+ archive session] in TerminalTopBar trailing"
```

---

### Task 35: `[archive page to KB]` in ScanDocumentDetailScreen long-press menu

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt`

- [ ] **Step 1: Locate the existing long-press menu**

Run: `git grep -n "DropdownMenu\|onLongClick\|combinedClickable" app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt`

The file uses one of: `Modifier.combinedClickable(onLongClick = …)` + a `DropdownMenu`, or a custom long-press → modal pattern. Find the `DropdownMenuItem` rows for "delete page" / "retake" and append a new item.

- [ ] **Step 2: Hoist a `SaveToKnowledgeViewModel` + modal in this screen**

Same pattern as Task 21 (chat). Add to the screen body:

```kotlin
val saveVm: com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeViewModel = hiltViewModel()
val saveState by saveVm.uiState.collectAsStateWithLifecycle()
val activeSource = remember { mutableStateOf<com.example.personal_studio.domain.model.KbDraftSource?>(null) }

com.example.personal_studio.feature.knowledge.ui.SavePreviewModal(
    state = saveState,
    onCancel = { saveVm.reset() },
    onConfirm = { d -> saveVm.commit(d) },
    onRetry = { activeSource.value?.let { saveVm.retry(it) } },
)
LaunchedEffect(saveState) {
    val s = saveState
    if (s is com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState.Saved) {
        // Need a callback up — for MVP just toast or stay; navigate via existing nav controller.
        onNavigateToKbEntry?.invoke(s.entryId)
        saveVm.reset()
    }
}
```

If `ScanDocumentDetailScreen` doesn't already have a navigation callback for KB entries, add a new param:

```kotlin
fun ScanDocumentDetailScreen(
    docId: Long,
    onBack: () -> Unit,
    onNavigateToKbEntry: ((Long) -> Unit)? = null,    // NEW (nullable for back-compat with existing call sites)
)
```

Update the call site in `AppNavHost`:

```kotlin
ScanDocumentDetailScreen(
    docId = id,
    onBack = { navController.popBackStack() },
    onNavigateToKbEntry = { entryId -> navController.navigate(NavRoutes.kbDetail(entryId)) },
)
```

- [ ] **Step 3: Add the menu item**

Inside the existing `DropdownMenu` for a page:

```kotlin
DropdownMenuItem(
    text = { Text("[archive page to KB]") },
    onClick = {
        val src = com.example.personal_studio.domain.model.KbDraftSource.FromScanPage(docId = docId, pageId = page.id)
        activeSource.value = src
        saveVm.startDraft(src)
        menuExpanded = false
    },
)
```

Adjust `menuExpanded` to whatever the local state name is.

- [ ] **Step 4: Build + smoke**

Run: `./gradlew :app:installDebug`
Open scanner → existing doc → long-press a page → tap `[archive page to KB]` → modal → save.
Expected: KB row with `sourceType=SCAN`, originalImagePath set.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/library/ScanDocumentDetailScreen.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(scanner): [archive page to KB] in ScanDocumentDetailScreen long-press menu"
```

---

### Task 36: `[+ archive]` in PageEditScreen

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/scanner/doc/PageEditScreen.kt` (or wherever the file lives — verify with `git grep -n "PageEditScreen"`).

- [ ] **Step 1: Add the same SaveToKnowledgeViewModel + modal hoist as Task 35**

Same `saveVm` / `saveState` / `activeSource` / `SavePreviewModal` block.
Add `onNavigateToKbEntry: ((Long) -> Unit)? = null` param if missing; thread through the AppNavHost call site.

- [ ] **Step 2: Add the button to the existing top action row**

Where the screen renders `[retake]` / `[delete]` (typically a `Row` near the top), append:

```kotlin
Text(
    "[+ archive]",
    color = Phosphor,
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier
        .clickable {
            val src = com.example.personal_studio.domain.model.KbDraftSource.FromScanPage(docId = docId, pageId = pageId)
            activeSource.value = src
            saveVm.startDraft(src)
        }
        .padding(8.dp),
)
```

- [ ] **Step 3: Build + smoke**

Run: `./gradlew :app:installDebug`
Open scanner → doc → page edit → tap `[+ archive]` → modal → save.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/scanner/doc/PageEditScreen.kt app/src/main/java/com/example/personal_studio/ui/AppNavHost.kt
git commit -m "kb(scanner): [+ archive] in PageEditScreen top action row"
```

---

### Task 37: Phase 5 verification

**Files:** none

- [ ] **Step 1: Build green**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 2: Three-source smoke on device**

1. From `chat/<session>` AI bubble → `[+ archive]` → save → KbHome shows new entry.
2. From `chat/<session>` topbar → `[+ archive session]` → save → KbHome shows new entry; sourceType in DB inspector = CHAT_SESSION.
3. From scanner doc detail → long-press a page → `[archive page to KB]` → save → KbMistakes (if AI flagged isQuestion=true) or KbHome (otherwise) shows new entry; sourceType = SCAN.

Capture screenshots of all three flows + KbHome/Mistakes lists into `docs/superpowers/checkpoints/P3/phase5/`.

- [ ] **Step 3: Tag**

```bash
git tag p3-phase5
```

---

## Phase 6 — Edit / Regenerate / Search / Related

Goal: KbEntryDetail's overflow menu exposes rename / change category / delete / regenerate. Summary section has inline edit toggle. KbHome's search bar runs FTS with < 5 fallback to OR. Related entries appear on detail (already wired UI in Phase 4; Phase 6 verifies the data round-trip end-to-end).

### Task 38: `UpdateEntryUseCase` + `DeleteEntryUseCase` + tests

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/knowledge/UpdateEntryUseCase.kt`
- Create: `app/src/main/java/com/example/personal_studio/domain/knowledge/DeleteEntryUseCase.kt`
- Create: `app/src/test/java/com/example/personal_studio/domain/knowledge/UpdateEntryUseCaseTest.kt`
- Create: `app/src/test/java/com/example/personal_studio/domain/knowledge/DeleteEntryUseCaseTest.kt`

- [ ] **Step 1: Write `UpdateEntryUseCase`**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import javax.inject.Inject

class UpdateEntryUseCase @Inject constructor(private val repo: KnowledgeRepository) {
    suspend operator fun invoke(entry: KbEntry) = repo.updateEntry(entry)
}
```

- [ ] **Step 2: Write `DeleteEntryUseCase`**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import javax.inject.Inject

class DeleteEntryUseCase @Inject constructor(private val repo: KnowledgeRepository) {
    suspend operator fun invoke(entryId: Long) = repo.deleteEntry(entryId)
}
```

- [ ] **Step 3: Write tests**

`UpdateEntryUseCaseTest.kt`:
```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateEntryUseCaseTest {
    @Test fun forwardsToRepository() = runTest {
        val repo = FakeKnowledgeRepository()
        val e = KbEntry(1, "T", null, null, KbSource.CHAT_MESSAGE, 1, 1, null, null, null, "## …", 0, 0)
        UpdateEntryUseCase(repo)(e)
        assertEquals(e, repo.lastUpdatedEntry)
    }
}
```

`DeleteEntryUseCaseTest.kt`:
```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteEntryUseCaseTest {
    @Test fun forwardsToRepository() = runTest {
        val repo = FakeKnowledgeRepository()
        DeleteEntryUseCase(repo)(42L)
        assertEquals(42L, repo.lastDeletedId)
    }
}
```

- [ ] **Step 4: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.knowledge.UpdateEntryUseCaseTest" --tests "com.example.personal_studio.domain.knowledge.DeleteEntryUseCaseTest"`
Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/knowledge/UpdateEntryUseCase.kt app/src/main/java/com/example/personal_studio/domain/knowledge/DeleteEntryUseCase.kt app/src/test/java/com/example/personal_studio/domain/knowledge/UpdateEntryUseCaseTest.kt app/src/test/java/com/example/personal_studio/domain/knowledge/DeleteEntryUseCaseTest.kt
git commit -m "kb(domain): UpdateEntryUseCase + DeleteEntryUseCase + tests"
```

---

### Task 39: `RegenerateEntryUseCase` (TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/knowledge/RegenerateEntryUseCase.kt`
- Create: `app/src/test/java/com/example/personal_studio/domain/knowledge/RegenerateEntryUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegenerateEntryUseCaseTest {

    @Test fun overwritesSummaryAndStdQuestion_keepsTitleAndCategory() = runTest {
        val repo = FakeKnowledgeRepository().apply {
            // Repo emits new draft from LLM:
            draftToReturn = KbEntryDraft(
                source = KbDraftSource.FromChatMessage(1, 2),
                title = "AI 新标题",
                categorySuggestion = "AI 新分类",
                standardizedQuestion = "新规范化题目",
                summaryMarkdown = "## 核心概念\n新内容",
                relatedEntryTitles = listOf("ignored"),
                originalImagePath = null,
            )
        }
        val original = KbEntry(
            id = 7, title = "用户已 rename", categoryId = 1, categoryName = "用户已 recategorize",
            source = KbSource.CHAT_MESSAGE, sourceChatMessageId = 2, sourceChatSessionId = 1,
            sourceScanPageId = null, originalImagePath = null,
            standardizedQuestion = "旧题目", summaryMarkdown = "旧摘要",
            createdAt = 0, updatedAt = 0,
        )
        RegenerateEntryUseCase(repo).invoke(original)
        val saved = repo.lastUpdatedEntry!!
        assertEquals("用户已 rename", saved.title)              // preserved
        assertEquals(1L, saved.categoryId)                       // preserved
        assertEquals("新规范化题目", saved.standardizedQuestion)  // overwritten
        assertEquals("## 核心概念\n新内容", saved.summaryMarkdown) // overwritten
    }
}
```

- [ ] **Step 2: Write the use case**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import javax.inject.Inject

/**
 * Re-runs the LLM against the entry's original source and overwrites only the
 * AI-derived fields ([standardizedQuestion] + [summaryMarkdown]). Title, category,
 * and relations are preserved (so user edits aren't blown away).
 */
class RegenerateEntryUseCase @Inject constructor(
    private val repo: KnowledgeRepository,
) {
    suspend operator fun invoke(entry: KbEntry) {
        val source = rebuildSource(entry) ?: error("entry ${entry.id} has incomplete source pointers")
        val freshDraft = repo.draftFromSource(source)
        repo.updateEntry(
            entry.copy(
                standardizedQuestion = freshDraft.standardizedQuestion,
                summaryMarkdown = freshDraft.summaryMarkdown,
            ),
        )
    }

    private fun rebuildSource(e: KbEntry): KbDraftSource? = when (e.source) {
        KbSource.CHAT_MESSAGE -> {
            val sid = e.sourceChatSessionId ?: return null
            val mid = e.sourceChatMessageId ?: return null
            KbDraftSource.FromChatMessage(sessionId = sid, aiMessageId = mid)
        }
        KbSource.CHAT_SESSION -> {
            val sid = e.sourceChatSessionId ?: return null
            KbDraftSource.FromChatSession(sessionId = sid)
        }
        KbSource.SCAN -> {
            val pid = e.sourceScanPageId ?: return null
            // docId isn't stored on the entry; the loader resolves it from pageId, so pass any
            // placeholder — SourceContextLoader.loadScanPage uses pageId as the source of truth.
            KbDraftSource.FromScanPage(docId = -1L, pageId = pid)
        }
    }
}
```

> Note: `SourceContextLoader.loadScanPage` currently uses `s.docId` only to fetch the doc title. Patch it (one-line change) to derive docId from `scanPageDao.getById(pageId)?.docId` when the input is `-1`. Add this small fix as Step 3.

- [ ] **Step 3: Patch `SourceContextLoader.loadScanPage` for the regenerate case**

In `SourceContextLoader.kt`, replace:
```kotlin
        val doc = scanDocumentDao.getById(s.docId)
```
with:
```kotlin
        val effectiveDocId = if (s.docId > 0) s.docId else page.docId
        val doc = scanDocumentDao.getById(effectiveDocId)
```

- [ ] **Step 4: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.knowledge.RegenerateEntryUseCaseTest"`
Expected: 1 test passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/knowledge/RegenerateEntryUseCase.kt app/src/main/java/com/example/personal_studio/data/repository/SourceContextLoader.kt app/src/test/java/com/example/personal_studio/domain/knowledge/RegenerateEntryUseCaseTest.kt
git commit -m "kb(domain): RegenerateEntryUseCase preserves user title/category"
```

---

### Task 40: Extend `KbEntryDetailViewModel` with edit ops

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModel.kt`

- [ ] **Step 1: Add the operations + state flags**

Replace the body of `KbEntryDetailViewModel` (everything inside the class):

```kotlin
    private val entryId: Long = savedStateHandle.get<Long>("entryId") ?: 0L

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isBusy: kotlinx.coroutines.flow.StateFlow<Boolean> = _busy

    val uiState: kotlinx.coroutines.flow.StateFlow<KbEntryDetailUiState> = kotlinx.coroutines.flow.combine(
        repo.observeEntry(entryId),
        repo.observeRelated(entryId),
    ) { entry, related ->
        if (entry == null) KbEntryDetailUiState.NotFound
        else KbEntryDetailUiState.Loaded(entry, related)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KbEntryDetailUiState.Loading)

    fun rename(newTitle: String) = mutate { entry -> entry.copy(title = newTitle.trim().ifBlank { entry.title }) }

    fun changeCategory(newCategoryId: Long?) = mutate { entry -> entry.copy(categoryId = newCategoryId) }

    fun saveSummary(newMarkdown: String) = mutate { entry -> entry.copy(summaryMarkdown = newMarkdown) }

    fun saveStandardizedQuestion(newQuestion: String?) = mutate { entry -> entry.copy(standardizedQuestion = newQuestion?.takeIf { it.isNotBlank() }) }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try { deleteUseCase(entryId) } finally { _busy.value = false }
            onDone()
        }
    }

    fun regenerate() {
        viewModelScope.launch {
            val current = (uiState.value as? KbEntryDetailUiState.Loaded)?.entry ?: return@launch
            _busy.value = true
            try { regenerateUseCase(current) } finally { _busy.value = false }
        }
    }

    private fun mutate(block: (com.example.personal_studio.domain.model.KbEntry) -> com.example.personal_studio.domain.model.KbEntry) {
        val current = (uiState.value as? KbEntryDetailUiState.Loaded)?.entry ?: return
        viewModelScope.launch {
            _busy.value = true
            try { updateUseCase(block(current)) } finally { _busy.value = false }
        }
    }
```

Update the constructor to inject the new use cases:

```kotlin
@HiltViewModel
class KbEntryDetailViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
    private val updateUseCase: com.example.personal_studio.domain.knowledge.UpdateEntryUseCase,
    private val deleteUseCase: com.example.personal_studio.domain.knowledge.DeleteEntryUseCase,
    private val regenerateUseCase: com.example.personal_studio.domain.knowledge.RegenerateEntryUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
```

Add the necessary imports if not already present:
```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Tests still green**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.feature.knowledge.vm.*"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModel.kt
git commit -m "kb(vm): KbEntryDetailViewModel — rename/recategorize/edit/delete/regenerate"
```

---

### Task 41: `CategoryPickerSheet` component

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryPickerSheet.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.model.KbCategory
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<KbCategory>,
    selectedId: Long?,
    onPick: (KbCategory) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Void) {
        Column(Modifier.padding(16.dp)) {
            Text("$ select category", color = FoamDim, style = MaterialTheme.typography.bodySmall)
            LazyColumn {
                items(categories, key = { it.id }) { c ->
                    val sel = c.id == selectedId
                    Text(
                        if (sel) "▎ ${c.name}  ✓" else "▎ ${c.name}",
                        color = if (sel) Phosphor else Foam,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(c); onDismiss() }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            Text("$ + 新建分类", color = FoamDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                    cursorBrush = SolidColor(Phosphor),
                    modifier = Modifier.weight(1f).padding(8.dp),
                )
                Text(
                    "[create]",
                    color = if (newName.isBlank()) FoamDim else Phosphor,
                    modifier = Modifier
                        .clickable(enabled = newName.isNotBlank()) { onCreate(newName.trim()); newName = ""; onDismiss() }
                        .padding(8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/CategoryPickerSheet.kt
git commit -m "kb(ui): CategoryPickerSheet — select existing or create new"
```

---

### Task 42: `SummaryMarkdownEditor` component

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/SummaryMarkdownEditor.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.personal_studio.ui.components.MathMarkdownView
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor

/**
 * Toggleable preview/edit pane for a markdown blob. Caller commits via [onSave]
 * once the user taps `[save]` — discards via the `[cancel]` button.
 */
@Composable
fun SummaryMarkdownEditor(
    initial: String,
    label: String = "summary",
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(initial) { mutableStateOf(initial) }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("## $label", color = Phosphor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!editing) {
                Text(
                    "[edit]",
                    color = FoamDim,
                    modifier = Modifier.clickable { editing = true; draft = initial }.padding(8.dp),
                )
            } else {
                Text("[cancel]", color = FoamDim, modifier = Modifier.clickable { editing = false }.padding(8.dp))
                Text(
                    "[save]",
                    color = Phosphor,
                    modifier = Modifier.clickable { onSave(draft); editing = false }.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MathMarkdownView(markdown = initial, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/components/SummaryMarkdownEditor.kt
git commit -m "kb(ui): SummaryMarkdownEditor — preview/edit toggle"
```

---

### Task 43: Wire overflow menu + editors into `KbEntryDetailScreen`

**Files:**
- Modify: `app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt`

- [ ] **Step 1: Add the overflow menu state**

Inside the screen body, just under the existing `val state by vm.uiState…` line, add:

```kotlin
val categories by vm.observeCategoriesForUi.collectAsStateWithLifecycle(initialValue = emptyList())
var menuExpanded by remember { mutableStateOf(false) }
var showRename by remember { mutableStateOf(false) }
var showCategorySheet by remember { mutableStateOf(false) }
var showDeleteConfirm by remember { mutableStateOf(false) }
var showRegenerateConfirm by remember { mutableStateOf(false) }
var renameDraft by remember { mutableStateOf("") }
```

> The VM exposes a `observeCategoriesForUi` derived flow: open the VM and add a passthrough:
> ```kotlin
> val observeCategoriesForUi = repo.observeCategories()
> ```

- [ ] **Step 2: Replace the `TerminalTopBar` trailing slot with overflow menu**

```kotlin
TerminalTopBar(
    route = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty().ifBlank { "kb/" },
    leading = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back", tint = Foam)
        }
    },
    trailing = {
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "menu", tint = Foam)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("rename") }, onClick = {
                    renameDraft = (state as? KbEntryDetailUiState.Loaded)?.entry?.title.orEmpty()
                    showRename = true; menuExpanded = false
                })
                DropdownMenuItem(text = { Text("change category") }, onClick = {
                    showCategorySheet = true; menuExpanded = false
                })
                DropdownMenuItem(text = { Text("regenerate") }, onClick = {
                    showRegenerateConfirm = true; menuExpanded = false
                })
                DropdownMenuItem(text = { Text("delete", color = Carmine) }, onClick = {
                    showDeleteConfirm = true; menuExpanded = false
                })
            }
        }
    },
)
```

Add imports as needed:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

- [ ] **Step 3: Render the dialogs + sheet**

After the main `Column` + `Box`, append:

```kotlin
if (showRename) {
    AlertDialog(
        onDismissRequest = { showRename = false },
        title = { Text("rename entry") },
        text = {
            BasicTextField(
                value = renameDraft,
                onValueChange = { renameDraft = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Foam),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Phosphor),
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.rename(renameDraft); showRename = false }) { Text("save") }
        },
        dismissButton = { TextButton(onClick = { showRename = false }) { Text("cancel") } },
    )
}
if (showCategorySheet) {
    com.example.personal_studio.feature.knowledge.ui.components.CategoryPickerSheet(
        categories = categories,
        selectedId = (state as? KbEntryDetailUiState.Loaded)?.entry?.categoryId,
        onPick = { vm.changeCategory(it.id) },
        onCreate = { name ->
            // upsert via repo, then re-pick
            kotlinx.coroutines.MainScope().launch {
                val newId = vm.upsertCategoryAndUse(name)
                vm.changeCategory(newId)
            }
        },
        onDismiss = { showCategorySheet = false },
    )
}
if (showDeleteConfirm) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text("delete this entry?") },
        text = { Text("kb_entries 行 + 本地 image + 关联关系会一并删除，无法撤销。") },
        confirmButton = { TextButton(onClick = { vm.delete(onBack); showDeleteConfirm = false }) { Text("delete", color = Carmine) } },
        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("cancel") } },
    )
}
if (showRegenerateConfirm) {
    AlertDialog(
        onDismissRequest = { showRegenerateConfirm = false },
        title = { Text("regenerate summary?") },
        text = { Text("将重新调 LLM 覆盖 summaryMarkdown + standardizedQuestion；标题 / 分类保留。") },
        confirmButton = { TextButton(onClick = { vm.regenerate(); showRegenerateConfirm = false }) { Text("regenerate") } },
        dismissButton = { TextButton(onClick = { showRegenerateConfirm = false }) { Text("cancel") } },
    )
}
```

Add imports:
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
```

Add the helper to `KbEntryDetailViewModel`:
```kotlin
    suspend fun upsertCategoryAndUse(name: String): Long = repo.upsertCategory(name)
```

- [ ] **Step 4: Replace the inline `MathMarkdownView` with `SummaryMarkdownEditor`**

Find the `LazyColumn` item that renders `MathMarkdownView(markdown = e.summaryMarkdown, …)` and replace with:

```kotlin
        item {
            com.example.personal_studio.feature.knowledge.ui.components.SummaryMarkdownEditor(
                initial = e.summaryMarkdown,
                label = "summary",
                onSave = { md -> vm.saveSummary(md) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
```

For mistake variant, the standardized question section should use the same editor — modify `MistakeHeader` to use `SummaryMarkdownEditor(initial = e.standardizedQuestion.orEmpty(), label = "题目", onSave = { vm.saveStandardizedQuestion(it) })` instead of the inline `MathMarkdownView`.

- [ ] **Step 5: Build + smoke**

Run: `./gradlew :app:installDebug`
Expected: kb entry detail shows overflow menu with 4 items; rename / category / regenerate / delete all work end-to-end.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/feature/knowledge/ui/KbEntryDetailScreen.kt app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbEntryDetailViewModel.kt
git commit -m "kb(ui): KbEntryDetailScreen edit suite — rename/category/delete/regenerate + summary editor"
```

---

### Task 44: `SearchKbUseCase` + AND→OR fallback in repo (TDD)

**Files:**
- Create: `app/src/main/java/com/example/personal_studio/domain/knowledge/SearchKbUseCase.kt`
- Create: `app/src/test/java/com/example/personal_studio/domain/knowledge/SearchKbUseCaseTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.FakeKnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.domain.model.KbSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKbUseCaseTest {

    private fun mk(id: Long) = KbEntry(
        id, "T$id", null, null, KbSource.CHAT_MESSAGE, 1, 1, null, null, null, "## …", 0, 0,
    )

    @Test fun returnsAndResults_whenEnoughHits() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.searchResults.value = (1L..6L).map { mk(it) }
        repo.orSearchResults = (1L..20L).map { mk(it) } // not used because AND has >=5
        val out = SearchKbUseCase(repo).invoke("any").first()
        assertEquals(6, out.size)
    }

    @Test fun fallsBackToOr_whenAndHitsLessThan5() = runTest {
        val repo = FakeKnowledgeRepository()
        repo.searchResults.value = listOf(mk(1L), mk(2L))
        repo.orSearchResults = (1L..10L).map { mk(it) }
        val out = SearchKbUseCase(repo).invoke("any").first()
        assertEquals(10, out.size)
    }

    @Test fun emptyQueryReturnsEmpty() = runTest {
        val repo = FakeKnowledgeRepository()
        val out = SearchKbUseCase(repo).invoke("").first()
        assertEquals(0, out.size)
    }
}
```

- [ ] **Step 2: Write the use case**

```kotlin
package com.example.personal_studio.domain.knowledge

import com.example.personal_studio.data.repository.KnowledgeRepository
import com.example.personal_studio.domain.model.KbEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * AND-mode FTS search; if the AND result set is below [MIN_RESCUE_HITS] the
 * use case returns the OR-mode result instead (one-shot, not reactive — re-emits
 * happen only when the upstream Flow emits).
 */
class SearchKbUseCase @Inject constructor(private val repo: KnowledgeRepository) {

    operator fun invoke(query: String): Flow<List<KbEntry>> {
        if (query.isBlank()) return flowOf(emptyList())
        return repo.search(query).map { andResults ->
            if (andResults.size >= MIN_RESCUE_HITS) andResults
            else {
                val or = repo.searchOr(query)
                if (or.size > andResults.size) or else andResults
            }
        }
    }

    companion object { private const val MIN_RESCUE_HITS = 5 }
}
```

- [ ] **Step 3: Tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.personal_studio.domain.knowledge.SearchKbUseCaseTest"`
Expected: 3 tests pass.

- [ ] **Step 4: Wire into KbHomeViewModel**

In `KbHomeViewModel`, replace the `entriesFlow` definition's else branch from `repo.search(q)` to `searchUseCase(q)`. Inject:

```kotlin
@HiltViewModel
class KbHomeViewModel @Inject constructor(
    private val repo: KnowledgeRepository,
    private val searchUseCase: com.example.personal_studio.domain.knowledge.SearchKbUseCase,
) : ViewModel() {
```

And:
```kotlin
            if (q.isBlank()) repo.observeAllEntries(catId, notesOnly = !notes)
            else searchUseCase(q)
```

- [ ] **Step 5: Run unit tests sweep**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/personal_studio/domain/knowledge/SearchKbUseCase.kt app/src/test/java/com/example/personal_studio/domain/knowledge/SearchKbUseCaseTest.kt app/src/main/java/com/example/personal_studio/feature/knowledge/vm/KbHomeViewModel.kt
git commit -m "kb(domain): SearchKbUseCase with AND→OR fallback; KbHomeViewModel uses it"
```

---

### Task 45: Phase 6 verification

**Files:** none

- [ ] **Step 1: Build + tests green**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL on both.

- [ ] **Step 2: Manual smoke**

1. Detail page → overflow → rename → check title updates on KbHome.
2. Detail page → overflow → change category → category badge updates.
3. Detail page → summary `[edit]` → modify text → `[save]` → KaTeX re-renders.
4. Detail page → overflow → regenerate → wait for spinner → summary changes; title preserved.
5. Detail page → overflow → delete → confirm → returns to KbHome with row gone; image gone from filesDir.
6. KbHome → search box: type `数学` → list filters to entries with bigram match; type `微积分` (no spaces) — verify behavior; type `微积分 极限` (with space) — verify multi-chunk AND match.
7. Detail page → tap related entry → navigates to that entry's detail.

Capture screenshots into `docs/superpowers/checkpoints/P3/phase6/`.

- [ ] **Step 3: Tag**

```bash
git tag p3-phase6
```

---

## Phase 7 — DoD + PR + tag

Goal: lock the feature with instrumented tests, screenshot collateral, PR, merge to main, tag `p3-knowledge-mvp`.

### Task 46: Instrumented test — KbHomeScreen empty + populated

**Files:**
- Create: `app/src/androidTest/java/com/example/personal_studio/feature/knowledge/KbHomeScreenTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.personal_studio.feature.knowledge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class KbHomeScreenTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun showsKbTabAndPlaceholderOrList() {
        // Tap the kb bottom-nav entry by its visible label `kb`.
        composeRule.onNodeWithText("kb").performClick()
        // Either the empty placeholder hint OR the search bar prefix should be visible.
        composeRule.onNodeWithText("$ grep -r ", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Build the androidTest variant**

Run: `./gradlew :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run on device**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.personal_studio.feature.knowledge.KbHomeScreenTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/example/personal_studio/feature/knowledge/KbHomeScreenTest.kt
git commit -m "kb(test): instrumented KbHomeScreen — search bar visible after tab click"
```

---

### Task 47: Instrumented test — SavePreviewModal renders fallback banner

**Files:**
- Create: `app/src/androidTest/java/com/example/personal_studio/feature/knowledge/SavePreviewModalTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.personal_studio.feature.knowledge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.personal_studio.domain.model.KbDraftFallbackReason
import com.example.personal_studio.domain.model.KbDraftSource
import com.example.personal_studio.domain.model.KbEntryDraft
import com.example.personal_studio.feature.knowledge.ui.SavePreviewModal
import com.example.personal_studio.feature.knowledge.vm.SaveToKnowledgeUiState
import org.junit.Rule
import org.junit.Test

class SavePreviewModalTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun fallbackBannerIsShown() {
        val draft = KbEntryDraft(
            source = KbDraftSource.FromChatMessage(1, 2),
            title = "T", categorySuggestion = "其它",
            standardizedQuestion = null, summaryMarkdown = "## 原始内容\n\nraw",
            relatedEntryTitles = emptyList(), originalImagePath = null,
            isFallback = true, fallbackReason = KbDraftFallbackReason.JSON_PARSE_FAILED,
        )
        composeRule.setContent {
            SavePreviewModal(
                state = SaveToKnowledgeUiState.Preview(draft),
                onCancel = {}, onConfirm = {}, onRetry = {},
            )
        }
        composeRule.onNodeWithText("AI 解析失败", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.personal_studio.feature.knowledge.SavePreviewModalTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/example/personal_studio/feature/knowledge/SavePreviewModalTest.kt
git commit -m "kb(test): instrumented SavePreviewModal — fallback banner visible"
```

---

### Task 48: Instrumented test — KbEntryDetailScreen mistake variant renders image header

**Files:**
- Create: `app/src/androidTest/java/com/example/personal_studio/feature/knowledge/KbEntryDetailScreenTest.kt`

- [ ] **Step 1: Write the test**

This test is end-to-end against a real DB. It seeds one mistake entry (with a tiny test JPEG copied from `androidTest/assets`), renders `MainActivity`, navigates to `kbDetail(id)`, and asserts the `## 题目` header is shown.

```kotlin
package com.example.personal_studio.feature.knowledge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.personal_studio.MainActivity
import com.example.personal_studio.data.local.db.AppDatabase
import com.example.personal_studio.data.local.db.entity.KbEntryEntity
import com.example.personal_studio.data.local.db.entity.KbSourceType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class KbEntryDetailScreenTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: AppDatabase

    @Before fun seed() {
        hiltRule.inject()
        runBlocking {
            val now = System.currentTimeMillis()
            val mathId = db.kbCategoryDao().findByName("数学")!!.id
            db.kbEntryDao().insert(
                KbEntryEntity(
                    id = 0, title = "T-MISTAKE", categoryId = mathId,
                    sourceType = KbSourceType.SCAN,
                    sourceChatMessageId = null, sourceChatSessionId = null, sourceScanPageId = 1,
                    originalImagePath = null,
                    standardizedQuestion = "求 x 的值",
                    summaryMarkdown = "## 核心概念\n…",
                    createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    @Test fun mistakeEntry_showsQuestionHeader() {
        composeRule.onNodeWithText("kb").performClick()
        composeRule.onNodeWithText("T-MISTAKE").performClick()
        composeRule.onNodeWithText("题目", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.example.personal_studio.feature.knowledge.KbEntryDetailScreenTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/example/personal_studio/feature/knowledge/KbEntryDetailScreenTest.kt
git commit -m "kb(test): instrumented KbEntryDetailScreen — mistake variant header"
```

---

### Task 49: Manual end-to-end DoD

**Files:** none

- [ ] **Step 1: Wipe + reinstall**

Run: `adb uninstall com.example.personal_studio || true && ./gradlew :app:installDebug`
Expected: clean install. App launches on `chat` tab.

- [ ] **Step 2: Verify seed**

Open Database Inspector → `kb_categories`: 7 rows, all `seeded = 1`.

- [ ] **Step 3: Three sources happy path**

For each of CHAT_MESSAGE / CHAT_SESSION / SCAN, archive → save → land on detail. Capture screenshots into `docs/superpowers/checkpoints/P3/phase7/dod-archive-<source>.png`.

- [ ] **Step 4: Edit suite happy path**

On any entry: rename, change category, edit summary, regenerate (twice), delete. Capture before/after screenshots for each.

- [ ] **Step 5: Search happy path**

Type a query in Chinese (e.g. `判别式`). Verify hits show. Try a mixed query (`compose 状态`). Verify hits.

- [ ] **Step 6: Mistakes screen**

Open `kb` → tap `[mistakes]`. Verify only standardized-question entries show with image thumbnails.

- [ ] **Step 7: Stash screenshots**

```bash
git add docs/superpowers/checkpoints/P3/
git commit -m "docs(p3): DoD smoke screenshots"
```

---

### Task 50: PR + tag

**Files:** none

- [ ] **Step 1: Push branch**

Run: `git push -u origin feature/p3-knowledge`
Expected: branch pushed.

- [ ] **Step 2: Open PR**

Run:
```bash
gh pr create --title "P3 · Knowledge Base — archive chat & scans into structured KB entries" --body "$(cat <<'EOF'
## Summary
- 4 new Room tables (kb_categories, kb_entries, kb_relations, kb_entries_fts) + bigram-tokenized FTS4 search
- 3 source types: per AI message, per chat session, per scan page
- Single LLM call extracts standardizedQuestion (mistakes) + 5-section summary; retry once + fallback to original text
- KbHome (search + chips + counts) → KbEntryDetail (with mistake variant: image + question header) → KbMistakes second-level
- Full edit suite: rename / change category / edit markdown / delete / regenerate (preserves user title+category)
- Default seed: 数学 / 物理 / 化学 / 生物 / 英语 / 编程 / 其它 (其它 non-deletable)

## Spec / Plan
- Spec: \`docs/superpowers/specs/2026-04-25-p3-knowledge-base-design.md\`
- Plan: \`docs/superpowers/plans/2026-04-25-p3-knowledge-base.md\`

## Test plan
- [x] All unit tests green (\`./gradlew :app:testDebugUnitTest\`)
- [x] Instrumented tests green (\`./gradlew :app:connectedDebugAndroidTest\`)
- [x] Manual DoD walkthrough — screenshots in docs/superpowers/checkpoints/P3/

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: After review + merge to main, tag**

```bash
git checkout main
git pull
git tag p3-knowledge-mvp
git push origin p3-knowledge-mvp
```

- [ ] **Step 4: Update memory file**

Update `C:\Users\NiKo\.claude\projects\C--Users-NiKo-Desktop-Personal-Studio\memory\project_context.md` (or create `project_p3_knowledge.md` if you prefer per-phase memory) with:
- P3 shipped (tag p3-knowledge-mvp)
- New tables + FTS bigram strategy
- Add-to-KB entry points (per-msg / per-session / per-scan-page)
- Mistakes second-level surface
- Edit suite available

---

## Self-Review Notes (post-write)

Coverage check vs spec:
- §1 Data model — Tasks 1-9 ✓
- §2 LLM contract — Tasks 11-15 ✓
- §3 Workflows — Tasks 18-22 (add-to-KB), 38-43 (edit/delete/regen) ✓
- §4 UI screens — Tasks 23-28 (home + detail), 30-33 (mistakes), 19+43 (modal+detail edit) ✓
- §5 Search — Tasks 4 (tokenizer), 8 (search() in repo), 44 (use case + AND→OR fallback) ✓
- §6 File structure — front-loaded ✓
- §7 Test strategy — unit tests inline with each TDD task; instrumented in Tasks 6, 46-48 ✓
- §8 Phase split — Phases 1-7 align with spec ✓
- §9 Risk register — fallback handling (Task 15), bigram noise (Task 44), image decoupling (Task 7) all addressed ✓

No "TBD" / "implement later" remaining. Type signatures consistent across tasks (KbDraftSource subclasses, KbEntry fields, KbSource enum).

