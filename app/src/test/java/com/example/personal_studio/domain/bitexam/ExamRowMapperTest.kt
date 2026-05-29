package com.example.personal_studio.domain.bitexam

import com.example.personal_studio.data.network.bit.dto.ExamRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ExamRowMapperTest {
    private val mapper = ExamRowMapper()
    private fun plus8(y:Int,mo:Int,d:Int,h:Int,mi:Int)=LocalDateTime.of(y,mo,d,h,mi,0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()

    @Test fun `maps a full exam row`() {
        val row = ExamRowDto(kcm="100074340[人工智能概论]", kch="100074340",
            kssjms="2026-07-01 10:10-12:10(星期三)", ksrq="2026-07-01 00:00:00",
            jasmc="理教楼203", zwh="78", zjjsxm="刘峡壁", xnxqdm="2025-2026-2")
        val e = mapper.invoke(row, "2025-2026-2")!!
        assertEquals("人工智能概论", e.course)
        assertEquals(plus8(2026,7,1,10,10), e.startAt)
        assertEquals(plus8(2026,7,1,12,10), e.endAt)
        assertEquals("理教楼203", e.location)
        assertEquals("78", e.seat)
        assertEquals("刘峡壁", e.invigilator)
        assertEquals("2025-2026-2|100074340|${plus8(2026,7,1,10,10)}", e.uid)
    }

    @Test fun `course without bracket falls back to raw`() {
        val e = mapper.invoke(ExamRowDto(kcm="高等数学", kch="X", kssjms="2026-07-01 10:10-12:10(星期三)", ksrq="2026-07-01 00:00:00"), "t")!!
        assertEquals("高等数学", e.course)
    }

    @Test fun `unparseable time returns null`() {
        assertNull(mapper.invoke(ExamRowDto(kcm="x[y]", kch="X", kssjms=null, ksrq=null), "t"))
    }

    @Test fun `date-only fallback when kssjms null uses start of day and null end`() {
        val e = mapper.invoke(ExamRowDto(kcm="x[y]", kch="X", kssjms=null, ksrq="2026-07-01 00:00:00"), "t")!!
        assertEquals(plus8(2026,7,1,0,0), e.startAt)
        assertNull(e.endAt)
    }

    @Test fun `single-digit hour is padded`() {
        val e = mapper.invoke(ExamRowDto(kcm="x[y]", kch="X", kssjms="2026-07-01 9:00-11:00(星期三)", ksrq="2026-07-01 00:00:00"), "t")!!
        assertEquals(plus8(2026,7,1,9,0), e.startAt)
        assertEquals(plus8(2026,7,1,11,0), e.endAt)
    }

    @Test fun `full-width tilde dash variant parses same as hyphen`() {
        val e = mapper.invoke(ExamRowDto(kcm="x[y]", kch="X", kssjms="2026-07-01 10:10～12:10(星期三)", ksrq="2026-07-01 00:00:00"), "t")!!
        assertEquals(plus8(2026,7,1,10,10), e.startAt)
        assertEquals(plus8(2026,7,1,12,10), e.endAt)
    }

    @Test fun `blank optional fields become null`() {
        val e = mapper.invoke(ExamRowDto(kcm="x[y]", kch="X", kssjms="2026-07-01 10:10-12:10(星期三)", ksrq="2026-07-01 00:00:00",
            jasmc="", zwh="   ", zjjsxm=""), "t")!!
        assertNull(e.location)
        assertNull(e.seat)
        assertNull(e.invigilator)
    }

    @Test fun `same course same day different time yields distinct uids`() {
        val morning = mapper.invoke(ExamRowDto(kcm="x[y]", kch="100074340", kssjms="2026-07-01 09:00-11:00(星期三)", ksrq="2026-07-01 00:00:00"), "t")!!
        val afternoon = mapper.invoke(ExamRowDto(kcm="x[y]", kch="100074340", kssjms="2026-07-01 14:00-16:00(星期三)", ksrq="2026-07-01 00:00:00"), "t")!!
        assertNotEquals(morning.uid, afternoon.uid)
    }
}
