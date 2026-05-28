package com.example.personal_studio.domain.bitddl

import com.example.personal_studio.domain.bitddl.model.DdlEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 手写 iCal(RFC 5545)解析器,只取需要的字段。处理:折行展开、VEVENT 切分、
 * TEXT 转义还原(\\ \, \; \n)、DTSTART 三态(UTC Z / TZID 按+08:00 / VALUE=DATE 全天+08:00 零点)。
 * 缺 UID 或 DTSTART 的条目跳过;非日历输入返回空。
 */
class LexueIcalParser @Inject constructor() {

    private val dtFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(ics: String): List<DdlEvent> {
        if (!ics.contains("BEGIN:VCALENDAR")) return emptyList()
        val unfolded = ics.replace(Regex("\\r?\\n[ \\t]+"), "")
        val lines = unfolded.split(Regex("\\r?\\n"))
        val events = mutableListOf<DdlEvent>()
        var current: MutableList<String>? = null
        for (line in lines) {
            when {
                line.trim() == "BEGIN:VEVENT" -> current = mutableListOf()
                line.trim() == "END:VEVENT" -> {
                    current?.let { parseEvent(it)?.let(events::add) }
                    current = null
                }
                else -> current?.add(line)
            }
        }
        return events
    }

    private fun parseEvent(lines: List<String>): DdlEvent? {
        var uid: String? = null
        var summary = ""
        var description = ""
        var categories: String? = null
        var dueAt: Long? = null
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val left = line.substring(0, colon)
            val value = line.substring(colon + 1)
            when (left.substringBefore(';').uppercase()) {
                "UID" -> uid = value.trim()
                "SUMMARY" -> summary = unescape(value)
                "DESCRIPTION" -> description = unescape(value)
                "CATEGORIES" -> categories = unescape(value).ifBlank { null }
                "DTSTART" -> dueAt = parseDateTime(left, value.trim())
            }
        }
        val u = uid?.takeIf { it.isNotBlank() } ?: return null
        val due = dueAt ?: return null
        return DdlEvent(u, summary, description, categories, due)
    }

    /**
     * 先把 `\\` 占位为不可能出现在正文里的控制符 sentinel,再还原其余转义,
     * 最后把 sentinel 还原成单个反斜杠,避免二次替换 / 误伤正文里的真实空格。
     */
    private fun unescape(s: String): String {
        val sentinel = "\u0000"
        return s.replace("\\\\", sentinel)
            .replace("\\n", "\n").replace("\\N", "\n")
            .replace("\\,", ",").replace("\\;", ";")
            .replace(sentinel, "\\")
    }

    private fun parseDateTime(left: String, value: String): Long? = runCatching {
        when {
            left.contains("VALUE=DATE", ignoreCase = true) && value.length == 8 ->
                LocalDate.parse(value, dateFmt).atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli()
            value.endsWith("Z") ->
                LocalDateTime.parse(value.removeSuffix("Z"), dtFmt).toInstant(ZoneOffset.UTC).toEpochMilli()
            else ->
                LocalDateTime.parse(value, dtFmt).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        }
    }.getOrNull()
}
