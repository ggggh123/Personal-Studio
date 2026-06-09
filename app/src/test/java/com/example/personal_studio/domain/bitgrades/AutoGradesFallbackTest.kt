package com.example.personal_studio.domain.bitgrades

import com.example.personal_studio.data.network.bit.NetworkMode
import com.example.personal_studio.data.network.bit.autoNetworkFallback
import com.example.personal_studio.domain.bitgrades.model.GradesSyncError
import com.example.personal_studio.domain.bitgrades.model.SyncGradesStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AutoGradesFallbackTest {

    private fun netFail() = SyncGradesStep.Failed(GradesSyncError.NetworkFail(IOException("unreachable")))

    /** 复刻 SyncGradesUseCase.syncAuto 的判据,锁定成绩在共享 autoNetworkFallback 上的取舍。 */
    private fun autoGradesFallback(
        first: NetworkMode,
        onModeSucceeded: (NetworkMode) -> Unit,
        attempt: (NetworkMode) -> Flow<SyncGradesStep>,
    ): Flow<SyncGradesStep> = autoNetworkFallback(
        first = first,
        isConnFail = { it is SyncGradesStep.Failed && it.err is GradesSyncError.NetworkFail },
        isDone = { it is SyncGradesStep.Done },
        switchingStep = { SyncGradesStep.SwitchingMode(it) },
        onModeSucceeded = onModeSucceeded,
        attempt = attempt,
    )

    @Test fun `falls back to the other mode on NetworkFail and reports the winning mode`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val steps = autoGradesFallback(NetworkMode.LOCAL, { won += it }) { mode ->
            when (mode) {
                NetworkMode.LOCAL -> flowOf(SyncGradesStep.LoggingIn, netFail())
                NetworkMode.WEBVPN -> flowOf(SyncGradesStep.LoggingIn, SyncGradesStep.Done(1, 2))
            }
        }.toList()

        // 首选 LOCAL 的网络失败被抑制(不 emit Failed),插入 SwitchingMode(WEBVPN),WEBVPN 成功。
        assertTrue("no Failed should surface", steps.none { it is SyncGradesStep.Failed })
        assertTrue(steps.any { it is SyncGradesStep.SwitchingMode && it.to == NetworkMode.WEBVPN })
        assertTrue(steps.last() is SyncGradesStep.Done)
        assertEquals(listOf(NetworkMode.WEBVPN), won)
    }

    @Test fun `succeeds on first mode without any fallback`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val steps = autoGradesFallback(NetworkMode.WEBVPN, { won += it }) { _ ->
            flowOf(SyncGradesStep.LoggingIn, SyncGradesStep.Done(1, 1))
        }.toList()

        assertEquals(listOf(NetworkMode.WEBVPN), won)
        assertTrue(steps.none { it is SyncGradesStep.SwitchingMode })
        assertTrue(steps.last() is SyncGradesStep.Done)
    }

    @Test fun `does NOT retry on a non-network failure (wrong password)`() = runTest {
        val won = mutableListOf<NetworkMode>()
        var attempts = 0
        val steps = autoGradesFallback(NetworkMode.LOCAL, { won += it }) { _ ->
            attempts++
            flowOf(SyncGradesStep.LoggingIn, SyncGradesStep.Failed(GradesSyncError.WrongCredentials))
        }.toList()

        assertEquals("must not switch modes for credential errors", 1, attempts)
        assertTrue(steps.none { it is SyncGradesStep.SwitchingMode })
        assertTrue(steps.last() is SyncGradesStep.Failed)
        assertTrue(won.isEmpty())
    }

    @Test fun `does NOT retry on EmptyGrades or ParseFail (fallback is connection-level only by design)`() = runTest {
        // 有意取舍:这两类可能是真实状态(无成绩/评教未完成/页面变动),换网络会掩盖真因。
        for (err in listOf(GradesSyncError.EmptyGrades, GradesSyncError.ParseFail("garbage html"))) {
            val won = mutableListOf<NetworkMode>()
            var attempts = 0
            val steps = autoGradesFallback(NetworkMode.LOCAL, { won += it }) { _ ->
                attempts++
                flowOf<SyncGradesStep>(SyncGradesStep.Failed(err))
            }.toList()

            assertEquals("must not switch modes for $err", 1, attempts)
            assertTrue(steps.none { it is SyncGradesStep.SwitchingMode })
            assertTrue(steps.last() is SyncGradesStep.Failed)
            assertTrue(won.isEmpty())
        }
    }

    @Test fun `when both modes are unreachable, surfaces the final NetworkFail and reports no winner`() = runTest {
        val won = mutableListOf<NetworkMode>()
        var attempts = 0
        val steps = autoGradesFallback(NetworkMode.LOCAL, { won += it }) { _ ->
            attempts++
            flowOf<SyncGradesStep>(netFail())
        }.toList()

        assertEquals(2, attempts)
        assertTrue(steps.any { it is SyncGradesStep.SwitchingMode }) // first fail suppressed + switch
        assertTrue(steps.last() is SyncGradesStep.Failed)            // second fail surfaces
        assertTrue(won.isEmpty())
    }
}
