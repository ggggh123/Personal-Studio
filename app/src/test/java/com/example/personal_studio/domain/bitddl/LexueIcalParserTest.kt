package com.example.personal_studio.domain.bitddl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class LexueIcalParserTest {
    private val parser = LexueIcalParser()

    private fun cal(vararg vevents: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + vevents.joinToString("") + "END:VCALENDAR\r\n"

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun plus8Millis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    @Test fun `parses a single utc vevent`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nUID:abc123\r\nSUMMARY:作业1 is due\r\n" +
            "DESCRIPTION:第一次作业\r\nCATEGORIES:高等数学\r\nDTSTART:20260530T155900Z\r\nEND:VEVENT\r\n"
        )
        val r = parser.parse(ics)
        assertEquals(1, r.size)
        val e = r.single()
        assertEquals("abc123", e.uid)
        assertEquals("作业1 is due", e.title)
        assertEquals("第一次作业", e.description)
        assertEquals("高等数学", e.course)
        assertEquals(utcMillis(2026, 5, 30, 15, 59, 0), e.dueAt)
    }

    @Test fun `unfolds 75-octet folded lines`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nUID:u1\r\nSUMMARY:t\r\nDESCRIPTION:line one\r\n  continues here\r\n" +
            "DTSTART:20260101T000000Z\r\nEND:VEVENT\r\n"
        )
        val e = parser.parse(ics).single()
        assertEquals("line onecontinues here", e.description)
    }

    @Test fun `unescapes ical text`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nUID:u2\r\nSUMMARY:a\\,b\\; c\\nd\\\\e\r\n" +
            "DTSTART:20260101T000000Z\r\nEND:VEVENT\r\n"
        )
        val e = parser.parse(ics).single()
        assertEquals("a,b; c\nd\\e", e.title)
    }

    @Test fun `parses tzid datetime as plus8`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nUID:u3\r\nSUMMARY:t\r\n" +
            "DTSTART;TZID=Asia/Shanghai:20260530T235900\r\nEND:VEVENT\r\n"
        )
        val e = parser.parse(ics).single()
        assertEquals(plus8Millis(2026, 5, 30, 23, 59, 0), e.dueAt)
    }

    @Test fun `parses all-day value-date at plus8 midnight`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nUID:u4\r\nSUMMARY:t\r\nDTSTART;VALUE=DATE:20260530\r\nEND:VEVENT\r\n"
        )
        val e = parser.parse(ics).single()
        assertEquals(plus8Millis(2026, 5, 30, 0, 0, 0), e.dueAt)
    }

    @Test fun `parses multiple vevents`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nUID:a\r\nSUMMARY:1\r\nDTSTART:20260101T000000Z\r\nEND:VEVENT\r\n",
            "BEGIN:VEVENT\r\nUID:b\r\nSUMMARY:2\r\nDTSTART:20260102T000000Z\r\nEND:VEVENT\r\n",
        )
        assertEquals(listOf("a", "b"), parser.parse(ics).map { it.uid })
    }

    @Test fun `skips vevent missing uid or dtstart`() {
        val ics = cal(
            "BEGIN:VEVENT\r\nSUMMARY:no uid\r\nDTSTART:20260101T000000Z\r\nEND:VEVENT\r\n",
            "BEGIN:VEVENT\r\nUID:nodt\r\nSUMMARY:no dtstart\r\nEND:VEVENT\r\n",
            "BEGIN:VEVENT\r\nUID:ok\r\nSUMMARY:fine\r\nDTSTART:20260101T000000Z\r\nEND:VEVENT\r\n",
        )
        assertEquals(listOf("ok"), parser.parse(ics).map { it.uid })
    }

    @Test fun `empty or non-calendar input yields empty list`() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("<html>not a calendar</html>").isEmpty())
    }

    @Test fun `null course when categories absent`() {
        val ics = cal("BEGIN:VEVENT\r\nUID:u\r\nSUMMARY:t\r\nDTSTART:20260101T000000Z\r\nEND:VEVENT\r\n")
        assertNull(parser.parse(ics).single().course)
    }
}
