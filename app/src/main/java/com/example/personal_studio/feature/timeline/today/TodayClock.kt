package com.example.personal_studio.feature.timeline.today

import javax.inject.Inject
import javax.inject.Singleton

/** Wall clock of the 今日 page; replaced in tests to pin "now". */
@Singleton
open class TodayClock @Inject constructor() {
    open fun now(): Long = System.currentTimeMillis()
}
