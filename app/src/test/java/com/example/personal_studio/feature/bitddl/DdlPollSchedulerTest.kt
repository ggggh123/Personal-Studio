package com.example.personal_studio.feature.bitddl

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DdlPollSchedulerTest {
    @Test fun `buildPeriodicRequest sets interval network and backoff`() {
        val req = DdlPollScheduler.buildPeriodicRequest(12)
        assertEquals(TimeUnit.HOURS.toMillis(12), req.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, req.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, req.workSpec.backoffPolicy)
        assertEquals(TimeUnit.MINUTES.toMillis(30), req.workSpec.backoffDelayDuration)
    }

    @Test fun `different intervals differ`() {
        listOf(6, 12, 24).forEach { h ->
            assertEquals(TimeUnit.HOURS.toMillis(h.toLong()), DdlPollScheduler.buildPeriodicRequest(h).workSpec.intervalDuration)
        }
    }
}
