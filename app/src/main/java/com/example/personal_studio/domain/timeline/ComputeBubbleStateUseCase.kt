package com.example.personal_studio.domain.timeline

import com.example.personal_studio.domain.model.BubbleState
import com.example.personal_studio.domain.model.TimelineItem
import com.example.personal_studio.domain.model.TimelineType
import javax.inject.Inject

/**
 * Pure mapping (item, now) → BubbleState. No IO. Constants:
 * - COURSE imminent: 15 min
 * - TASK imminent: 2 h
 * - CUSTOM imminent: 30 min
 */
class ComputeBubbleStateUseCase @Inject constructor() {

    private val courseImminent = 15 * MIN_MS
    private val taskImminent = 2 * 60 * MIN_MS
    private val customImminent = 30 * MIN_MS

    operator fun invoke(item: TimelineItem, now: Long): BubbleState {
        return when (item.type) {
            TimelineType.COURSE -> {
                val end = item.endAt ?: item.startAt
                when {
                    end <= now -> BubbleState.CoursePast
                    item.startAt <= now -> BubbleState.CourseInProgress
                    item.startAt - now <= courseImminent -> BubbleState.CourseImminent
                    else -> BubbleState.CourseUpcoming
                }
            }
            TimelineType.TASK -> when {
                item.isDone -> BubbleState.TaskDone
                item.startAt <= now -> BubbleState.TaskOverdue
                item.startAt - now <= taskImminent -> BubbleState.TaskImminent
                else -> BubbleState.TaskUpcoming
            }
            TimelineType.CUSTOM -> {
                val end = item.endAt ?: item.startAt
                when {
                    item.isDone -> BubbleState.CustomDone
                    end <= now -> BubbleState.CustomOverdue
                    item.startAt <= now -> BubbleState.CustomInProgress
                    item.startAt - now <= customImminent -> BubbleState.CustomImminent
                    else -> BubbleState.CustomUpcoming
                }
            }
        }
    }

    companion object {
        private const val MIN_MS: Long = 60_000L
    }
}
