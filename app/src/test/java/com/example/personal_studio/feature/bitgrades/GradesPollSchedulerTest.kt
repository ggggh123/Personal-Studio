package com.example.personal_studio.feature.bitgrades

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class GradesPollSchedulerTest {
    @Test fun `buildPeriodicRequest sets interval network constraint and exponential backoff`() {
        val req = GradesPollScheduler.buildPeriodicRequest(intervalHours = 6)
        assertEquals(TimeUnit.HOURS.toMillis(6), req.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, req.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, req.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(30), req.workSpec.backoffDelayDuration)
    }

    @Test fun `different intervals produce different durations`() {
        listOf(3, 6, 12).forEach { h ->
            assertEquals(TimeUnit.HOURS.toMillis(h.toLong()), GradesPollScheduler.buildPeriodicRequest(h).workSpec.intervalDuration)
        }
    }
}
