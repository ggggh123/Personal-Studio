package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.data.network.bit.BitApiClient
import com.example.personal_studio.data.network.bit.dto.CasLoginDto
import com.example.personal_studio.data.network.bit.dto.EclassCourseDto
import com.example.personal_studio.data.network.bit.dto.EclassCoursesDto
import com.example.personal_studio.data.network.bit.dto.EclassHomeworkDto
import com.example.personal_studio.data.network.bit.dto.EclassHomeworkPageDto
import com.example.personal_studio.data.network.bit.dto.EclassSemestersDto
import com.example.personal_studio.domain.bitddl.model.BackgroundDdlResult
import com.example.personal_studio.domain.bitddl.model.DdlEvent
import com.example.personal_studio.domain.bitddl.model.DdlSource
import com.example.personal_studio.domain.bitddl.model.DdlSyncError
import com.example.personal_studio.domain.bitddl.model.DdlSyncRequest
import com.example.personal_studio.domain.bitimport.SsoLoginUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * 新乐学(畅课)作业:当前学期 → 本学期课程 → 每门课的作业(分页),只读。
 * 先直接拉(学校网页 Cookie 可能还有效);拿到的不是 JSON 说明没登录,走一次统一认证再拉。
 * 假设 apiClient 已 open(由调用方管 open/close)。
 */
class FetchEclassAssignmentsUseCase @Inject constructor(
    private val apiClient: BitApiClient,
    private val ssoLogin: SsoLoginUseCase,
) {
    suspend operator fun invoke(req: DdlSyncRequest): BackgroundDdlResult = try {
        fetch()?.let { BackgroundDdlResult.Ok(it) } ?: run {
            when (val login = ssoLogin.loginEclass(apiClient, req.username, req.password)) {
                CasLoginDto.Success -> fetch()?.let { BackgroundDdlResult.Ok(it) }
                    ?: BackgroundDdlResult.Stop(DdlSyncError.ParseFail("新乐学登录后仍未进入"))
                CasLoginDto.WrongCredentials -> BackgroundDdlResult.Stop(DdlSyncError.WrongCredentials)
                CasLoginDto.AccountLocked -> BackgroundDdlResult.Stop(DdlSyncError.AccountLocked)
                CasLoginDto.CaptchaRequired -> BackgroundDdlResult.Stop(DdlSyncError.CaptchaRequired)
                is CasLoginDto.UnknownFailure -> BackgroundDdlResult.Stop(DdlSyncError.ParseFail(login.body))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        BackgroundDdlResult.Transient
    } catch (e: SerializationException) {
        BackgroundDdlResult.Stop(DdlSyncError.ParseFail("新乐学返回结构变化"))
    } catch (e: IllegalArgumentException) {
        // An untrusted redirect host, or a malformed field.
        BackgroundDdlResult.Stop(DdlSyncError.ParseFail(e.message ?: "新乐学返回异常"))
    }

    /** All homework of this semester, or null when 新乐学 does not see us signed in. */
    private suspend fun fetch(): List<DdlEvent>? {
        val service = apiClient.eclass
        val semesters = service.semesters().json<EclassSemestersDto>() ?: return null
        val current = EclassAssignments.currentSemesters(semesters)
        if (current.isEmpty()) return emptyList()

        val courses = mutableListOf<EclassCourseDto>()
        var page = 1
        do {
            val body = EclassAssignments.coursesQuery(current, page).toRequestBody(JSON_TYPE)
            val dto = service.courses(body).json<EclassCoursesDto>() ?: return null
            courses += dto.courses
            page++
        } while (page <= (dto.pages ?: 1) && page <= MAX_PAGES)

        val perCourse = coroutineScope {
            courses.distinctBy { it.id }.map { course ->
                async {
                    val items = mutableListOf<EclassHomeworkDto>()
                    var p = 1
                    do {
                        val dto = service.homework(course.id, p, PAGE_SIZE).json<EclassHomeworkPageDto>()
                            ?: return@async null
                        items += dto.items
                        p++
                    } while (p <= (dto.pages ?: 1) && p <= MAX_PAGES)
                    course to items
                }
            }.awaitAll()
        }
        if (perCourse.any { it == null }) return null
        return perCourse.filterNotNull().flatMap { (course, items) -> EclassAssignments.toEvents(course, items) }
            .distinctBy { it.uid }
    }

    /** null: not JSON (a sign-in page) or 401/403. Other HTTP errors are network-level. */
    private inline fun <reified T> Response<ResponseBody>.json(): T? {
        val text = (body() ?: errorBody())?.string().orEmpty()
        if (code() == 401 || code() == 403) return null
        if (!isSuccessful) throw IOException("新乐学 HTTP ${code()}")
        if (!EclassAssignments.looksLikeJson(text)) return null
        return JSON.decodeFromString<T>(text)
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 10
        private val JSON_TYPE = "application/json".toMediaType()
        private val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    }
}

/** Pure mapping, kept apart from the network so it can be tested on its own. */
object EclassAssignments {
    fun looksLikeJson(text: String): Boolean = text.trimStart().let { it.startsWith("{") || it.startsWith("[") }

    /** The semester(s) 新乐学 marks active; the latest one when none is. */
    fun currentSemesters(dto: EclassSemestersDto): List<Long> {
        val active = dto.semesters.filter { it.isActive == true }.map { it.id }
        return active.ifEmpty { listOfNotNull(dto.semesters.maxByOrNull { it.sort ?: 0 }?.id) }
    }

    fun coursesQuery(semesterIds: List<Long>, page: Int): String = buildJsonObject {
        put("page", page)
        put("page_size", 100)
        put("fields", "id,name")
        putJsonObject("conditions") {
            put("semester_id", buildJsonArray { semesterIds.forEach { add(JsonPrimitive(it)) } })
        }
    }.toString()

    /** Published homework with a deadline; times are UTC instants. */
    fun toEvents(course: EclassCourseDto, items: List<EclassHomeworkDto>): List<DdlEvent> = items.mapNotNull { h ->
        if (h.published == false) return@mapNotNull null
        val due = parseInstant(h.endTime ?: h.deadline) ?: return@mapNotNull null
        DdlEvent(
            uid = DdlSource.ECLASS_UID_PREFIX + h.id,
            title = h.title?.trim().orEmpty().ifEmpty { "作业" },
            description = htmlToText(((h.data as? JsonObject)?.get("description") as? JsonPrimitive)
                ?.takeIf { it.isString }?.content.orEmpty()),
            course = course.name?.trim()?.takeIf { it.isNotEmpty() },
            dueAt = due,
            source = DdlSource.ECLASS,
            submitted = h.submitted == true,
        )
    }

    fun parseInstant(text: String?): Long? = try {
        text?.takeIf { it.isNotBlank() }?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: DateTimeParseException) {
        null
    }

    /** The teacher's rich text as plain lines: breaks and paragraphs become newlines, tags go. */
    fun htmlToText(html: String): String = html
        .replace(Regex("""(?i)<br\s*/?>|</(p|div|li|h[1-6])>"""), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .lines().map { it.trim() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
