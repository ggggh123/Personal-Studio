package com.example.personal_studio.data.network.bit

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private sealed class S {
    object Work : S(); object Done : S(); object ConnFail : S(); object OtherFail : S()
    data class Switch(val to: NetworkMode) : S()
}

class NetworkFallbackTest {
    private val isConn: (S) -> Boolean = { it is S.ConnFail }
    private val isDone: (S) -> Boolean = { it is S.Done }
    private val sw: (NetworkMode) -> S = { S.Switch(it) }

    @Test fun `flow - first conn-fail switches, surfaces no fail, reports winner`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val out = autoNetworkFallback(NetworkMode.LOCAL, isConn, isDone, sw, { won += it }) { m ->
            if (m == NetworkMode.LOCAL) flowOf(S.Work, S.ConnFail) else flowOf(S.Work, S.Done)
        }.toList()
        assertTrue(out.none { it is S.ConnFail })
        assertTrue(out.any { it is S.Switch && it.to == NetworkMode.WEBVPN })
        assertTrue(out.last() is S.Done)
        assertEquals(listOf(NetworkMode.WEBVPN), won)
    }

    @Test fun `flow - first success no fallback`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val out = autoNetworkFallback(NetworkMode.WEBVPN, isConn, isDone, sw, { won += it }) { _ -> flowOf(S.Done) }.toList()
        assertEquals(listOf(NetworkMode.WEBVPN), won)
        assertTrue(out.none { it is S.Switch })
    }

    @Test fun `flow - non-conn failure does not retry`() = runTest {
        var attempts = 0
        val out = autoNetworkFallback(NetworkMode.LOCAL, isConn, isDone, sw, {}) { _ -> attempts++; flowOf(S.OtherFail) }.toList()
        assertEquals(1, attempts)
        assertTrue(out.none { it is S.Switch })
        assertTrue(out.last() is S.OtherFail)
    }

    @Test fun `flow - both modes conn-fail surfaces final fail, no winner`() = runTest {
        val won = mutableListOf<NetworkMode>()
        var attempts = 0
        val out = autoNetworkFallback(NetworkMode.LOCAL, isConn, isDone, sw, { won += it }) { _ -> attempts++; flowOf(S.ConnFail) }.toList()
        assertEquals(2, attempts)
        assertTrue(out.any { it is S.Switch })
        assertTrue(out.last() is S.ConnFail)
        assertTrue(won.isEmpty())
    }

    @Test fun `suspend - IOException switches then succeeds, reports winner`() = runTest {
        val won = mutableListOf<NetworkMode>(); val switched = mutableListOf<NetworkMode>()
        val r = withSessionAutoFallback(NetworkMode.LOCAL, onModeSucceeded = { won += it }, onSwitching = { switched += it }) { m ->
            if (m == NetworkMode.LOCAL) throw IOException("x") else "ok"
        }
        assertEquals("ok", r); assertEquals(listOf(NetworkMode.WEBVPN), won); assertEquals(listOf(NetworkMode.WEBVPN), switched)
    }

    @Test fun `suspend - non-IO rethrows without retry`() = runTest {
        var attempts = 0
        try {
            withSessionAutoFallback(NetworkMode.LOCAL, onModeSucceeded = {}) { _ -> attempts++; throw IllegalStateException("no") }
            assertTrue("should have thrown", false)
        } catch (e: IllegalStateException) { /* ok */ }
        assertEquals(1, attempts)
    }

    @Test fun `suspend - first mode succeeds, no switch`() = runTest {
        val won = mutableListOf<NetworkMode>()
        val r = withSessionAutoFallback(NetworkMode.LOCAL, onModeSucceeded = { won += it }) { _ -> 42 }
        assertEquals(42, r); assertEquals(listOf(NetworkMode.LOCAL), won)
    }
}
