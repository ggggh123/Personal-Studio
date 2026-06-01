package com.example.personal_studio.domain.emptyroom

import com.example.personal_studio.data.network.bit.dto.RoomOccupancyDto
import com.example.personal_studio.domain.emptyroom.model.RoomFreeSlots
import com.example.personal_studio.domain.emptyroom.model.RoomStatus
import javax.inject.Inject

/** 把一条 cxkxjasqk 行(JASMC + ZYJC 忙节次串)换算成空闲段 + 相对「nowMinute」的状态。纯函数,无依赖,易测。 */
class ComputeFreeSlotsUseCase @Inject constructor() {

    fun invoke(
        dto: RoomOccupancyDto,
        buildingName: String,
        clock: PeriodClock,
        nowMinute: Int,
    ): RoomFreeSlots {
        val busy = parseBusy(dto.busyPeriods)
        val free = (1..13).filter { it !in busy }
        val ranges = mergeConsecutive(free)
        val status = computeStatus(busy, clock, nowMinute)
        return RoomFreeSlots(
            roomName = dto.roomName.orEmpty(),
            buildingName = buildingName,
            busyPeriods = busy,
            freeRanges = ranges,
            status = status,
        )
    }

    private fun parseBusy(zyjc: String?): Set<Int> =
        zyjc?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet().orEmpty()

    private fun mergeConsecutive(sorted: List<Int>): List<IntRange> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var start = sorted.first(); var prev = start
        for (p in sorted.drop(1)) {
            if (p == prev + 1) { prev = p } else { out += start..prev; start = p; prev = p }
        }
        out += start..prev
        return out
    }

    private fun computeStatus(busy: Set<Int>, clock: PeriodClock, nowMinute: Int): RoomStatus {
        val curPeriod = clock.periodAt(nowMinute)
        val freeNow = curPeriod == null || curPeriod !in busy
        if (freeNow) {
            val from = (curPeriod ?: nextPeriodStartingAfter(clock, nowMinute) ?: 14)
            val nextBusy = (from..13).firstOrNull { it in busy }
            val until = if (nextBusy != null) clock.startMinute(nextBusy) else clock.endMinute(13)
            return RoomStatus(freeNow = true, freeUntilMinuteOfDay = until, nextFreeMinuteOfDay = null)
        } else {
            val nextFree = ((curPeriod!! + 1)..13).firstOrNull { it !in busy }
            return RoomStatus(
                freeNow = false,
                freeUntilMinuteOfDay = null,
                nextFreeMinuteOfDay = nextFree?.let { clock.startMinute(it) },
            )
        }
    }

    private fun nextPeriodStartingAfter(clock: PeriodClock, nowMinute: Int): Int? =
        (1..13).firstOrNull { clock.startMinute(it) > nowMinute }
}
