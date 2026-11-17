package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.core.util.DefaultTimetable
import com.example.personal_studio.domain.emptyroom.model.Building
import com.example.personal_studio.domain.emptyroom.model.Campus
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import com.example.personal_studio.data.local.datastore.EmptyRoomPrefs
import com.example.personal_studio.feature.emptyroom.ui.periodsText
import com.example.personal_studio.feature.emptyroom.ui.placeText
import com.example.personal_studio.feature.emptyroom.ui.rangeText
import com.example.personal_studio.feature.emptyroom.ui.shortCampus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyRoomQueryTest {
    private val clock = PeriodClock(DefaultTimetable.PERIODS)
    private fun at(h: Int, m: Int = 0) = h * 60 + m

    private fun room(name: String, building: String = "J1", busy: Set<Int> = emptySet(), freeNow: Boolean = true) = RoomFreeSlots(
        roomName = name, buildingName = if (building == "J1") "理教" else "综教", buildingCode = building,
        busyPeriods = busy, freeRanges = listOf(1..13),
        status = RoomStatus(freeNow = freeNow, freeUntilMinuteOfDay = at(15, 15), nextFreeMinuteOfDay = null),
    )

    @Test fun `now means the period under way, or the next one in a break`() {
        assertEquals(RequiredPeriods(setOf(1)), EmptyRoomQuery.required(TimeFilter.NOW, emptySet(), true, at(8, 10), clock))
        // 08:45–08:50 is the break between periods 1 and 2.
        assertEquals(RequiredPeriods(setOf(2)), EmptyRoomQuery.required(TimeFilter.NOW, emptySet(), true, at(8, 47), clock))
        assertEquals(RequiredPeriods(emptySet(), passed = true), EmptyRoomQuery.required(TimeFilter.NOW, emptySet(), true, at(23), clock))
        assertEquals(RequiredPeriods(emptySet()), EmptyRoomQuery.required(TimeFilter.NOW, emptySet(), false, at(8, 10), clock))
    }

    @Test fun `a span drops periods already over today, and is whole on another day`() {
        // 10:00 is inside period 3 (09:55–10:40): periods 1 and 2 are over.
        assertEquals(RequiredPeriods(setOf(3, 4, 5)), EmptyRoomQuery.required(TimeFilter.MORNING, emptySet(), true, at(10), clock))
        assertEquals(RequiredPeriods(setOf(1, 2, 3, 4, 5)), EmptyRoomQuery.required(TimeFilter.MORNING, emptySet(), false, at(10), clock))
        assertEquals(RequiredPeriods(emptySet(), passed = true), EmptyRoomQuery.required(TimeFilter.MORNING, emptySet(), true, at(13), clock))
        assertEquals(RequiredPeriods(setOf(6, 7)), EmptyRoomQuery.required(TimeFilter.CUSTOM, setOf(6, 7), false, 0, clock))
        assertEquals(RequiredPeriods(emptySet()), EmptyRoomQuery.required(TimeFilter.ANY, setOf(6, 7), true, at(10), clock))
    }

    @Test fun `custom periods can be several blocks, minus those over today`() {
        val chosen = setOf(1, 2, 3, 7, 8, 9)
        assertEquals(RequiredPeriods(chosen), EmptyRoomQuery.required(TimeFilter.CUSTOM, chosen, false, 0, clock))
        // 10:00: periods 1 and 2 are over, 3 is under way.
        assertEquals(RequiredPeriods(setOf(3, 7, 8, 9)), EmptyRoomQuery.required(TimeFilter.CUSTOM, chosen, true, at(10), clock))
        assertEquals(RequiredPeriods(emptySet()), EmptyRoomQuery.required(TimeFilter.CUSTOM, emptySet(), true, at(10), clock))
        assertEquals(RequiredPeriods(emptySet(), passed = true), EmptyRoomQuery.required(TimeFilter.CUSTOM, setOf(1, 2), true, at(10), clock))
    }

    @Test fun `blocks merge consecutive periods`() {
        assertEquals(listOf(1..3, 7..9, 12..12), EmptyRoomQuery.blocks(setOf(9, 1, 2, 3, 7, 8, 12)))
        assertEquals(emptyList<IntRange>(), EmptyRoomQuery.blocks(emptySet()))
    }

    @Test fun `a tap selects a period or clears it`() {
        assertEquals(setOf(1, 2, 7), EmptyRoomQuery.toggle(setOf(1, 2), 7))
        assertEquals(setOf(1), EmptyRoomQuery.toggle(setOf(1, 2), 2))
    }

    @Test fun `dragging paints a stretch and keeps the other blocks`() {
        // Starting on an empty period selects the stretch, in either direction.
        assertEquals(setOf(1, 2, 3, 7, 8, 9), EmptyRoomQuery.paint(setOf(1, 2, 3), 7, 9))
        assertEquals(setOf(1, 2, 3, 7, 8, 9), EmptyRoomQuery.paint(setOf(1, 2, 3), 9, 7))
        // Starting on a selected period clears the stretch.
        assertEquals(setOf(1, 9), EmptyRoomQuery.paint(setOf(1, 2, 3, 7, 8, 9), 2, 8))
        // Moving back shrinks what the drag added: painting is always relative to the start.
        assertEquals(setOf(1, 2, 3, 7), EmptyRoomQuery.paint(setOf(1, 2, 3), 7, 7))
    }

    @Test fun `rooms must have every required period free`() {
        val rooms = listOf(room("101", busy = setOf(3)), room("102", busy = setOf(6)), room("103"))
        assertEquals(listOf("102", "103"), EmptyRoomQuery.filter(rooms, setOf(3, 4)).map { it.roomName })
        assertEquals(3, EmptyRoomQuery.filter(rooms, emptySet()).size)
    }

    @Test fun `rooms group by building in catalog order, free now first, then natural numbers`() {
        val rooms = listOf(
            room("1001"), room("305", busy = setOf(1)), room("305A", building = "J2"),
            room("207", freeNow = false), room("31"),
        )
        val groups = EmptyRoomQuery.group(rooms, listOf("J2", "J1"), today = true)
        assertEquals(listOf("综教", "理教"), groups.map { it.first })
        assertEquals(listOf("31", "1001", "305", "207"), groups[1].second.map { it.roomName })
    }

    @Test fun `today the longest-free rooms lead, busy rooms by when they free up`() {
        fun r(name: String, free: Boolean, until: Int?, next: Int?) = room(name).copy(status = RoomStatus(free, until, next))
        val rooms = listOf(r("a", true, at(11, 35), null), r("b", true, at(19, 20), null), r("c", false, null, at(14)), r("d", false, null, at(13, 20)))
        assertEquals(listOf("b", "a", "d", "c"), EmptyRoomQuery.group(rooms, listOf("J1"), today = true)[0].second.map { it.roomName })
    }

    @Test fun `natural order compares digit runs by value`() {
        assertTrue(EmptyRoomQuery.naturalCompare("B2", "B10") < 0)
        assertTrue(EmptyRoomQuery.naturalCompare("305", "1001") < 0)
        assertTrue(EmptyRoomQuery.naturalCompare("理教305", "理教305") == 0)
    }

    @Test fun `status reads how long a room stays free today, or its free periods another day`() {
        assertEquals("空到 15:15", EmptyRoomQuery.statusText(room("1"), today = true))
        val busy = room("2").copy(status = RoomStatus(false, null, at(13, 20)))
        assertEquals("13:20 后空", EmptyRoomQuery.statusText(busy, today = true))
        assertEquals("今天不再空", EmptyRoomQuery.statusText(busy.copy(status = RoomStatus(false, null, null)), today = true))
        assertEquals("空闲 第 1–2、6 节", EmptyRoomQuery.statusText(room("3").copy(freeRanges = listOf(1..2, 6..6)), today = false))
        assertEquals("全天有课", EmptyRoomQuery.freeRangesText(emptyList()))
    }

    @Test fun `place summary names a few buildings and counts the rest`() {
        val campus = Campus("01", "良乡校区")
        val bs = listOf("理教", "综教", "文萃", "研楼").mapIndexed { i, n -> Building("B$i", n, "01") }
        assertEquals("良乡 · 全部 4 栋", placeText(campus, bs, emptySet()))
        assertEquals("良乡 · 理教、综教", placeText(campus, bs, setOf("B0", "B1")))
        assertEquals("良乡 · 理教、综教等 4 栋", placeText(campus, bs, setOf("B0", "B1", "B2", "B3")))
        assertEquals(null, placeText(null, bs, emptySet()))
        assertEquals("中关村", shortCampus("中关村校区"))
    }

    @Test fun `several blocks read as periods and clock times`() {
        assertEquals("第 1–3、7–9 节 · 08:00–10:40、14:10–16:50", periodsText(setOf(1, 2, 3, 7, 8, 9), clock))
        assertEquals("第 5 节 · 11:35–12:20", periodsText(setOf(5), clock))
        assertEquals(null, periodsText(emptySet(), clock))
    }

    @Test fun `stored custom periods read the new and the first format`() {
        assertEquals(setOf(1, 2, 3, 7, 8), EmptyRoomPrefs.parseCustomPeriods("1,2,3,7,8"))
        assertEquals(setOf(3, 4, 5), EmptyRoomPrefs.parseCustomPeriods("3-5"))
        assertEquals(emptySet<Int>(), EmptyRoomPrefs.parseCustomPeriods(""))
        assertEquals(setOf(13), EmptyRoomPrefs.parseCustomPeriods("13,14,0"))
    }

    @Test fun `range text gives periods and clock times`() {
        assertEquals("第 3–5 节 · 09:55–12:20", rangeText(3..5, clock))
        assertEquals("第 1 节 · 08:00–08:45", rangeText(1..1, clock))
    }
}
