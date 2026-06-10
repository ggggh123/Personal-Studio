package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.network.bit.CampusReachabilityProbe
import com.example.personal_studio.data.network.bit.NetworkMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveNetworkModeUseCaseTest {

    private fun noOverride() = mockk<ImportCredentialPrefs> { every { modeOverride() } returns null }

    // —— 自动模式(无手动覆盖) —— //

    @Test fun `auto - LOCAL or null stays LOCAL without probing`() = runTest {
        val probe = mockk<CampusReachabilityProbe>()   // 未 stub:若被调用会抛,验证不探测
        val resolve = ResolveNetworkModeUseCase(probe, noOverride())
        assertEquals(NetworkMode.LOCAL, resolve(NetworkMode.LOCAL))
        assertEquals(NetworkMode.LOCAL, resolve(null))
        coVerify(exactly = 0) { probe.localReachable() }
    }

    @Test fun `auto - WEBVPN reverts to LOCAL when campus is reachable`() = runTest {
        val probe = mockk<CampusReachabilityProbe> { coEvery { localReachable() } returns true }
        assertEquals(NetworkMode.LOCAL, ResolveNetworkModeUseCase(probe, noOverride())(NetworkMode.WEBVPN))
    }

    @Test fun `auto - WEBVPN stays WEBVPN when campus is unreachable`() = runTest {
        val probe = mockk<CampusReachabilityProbe> { coEvery { localReachable() } returns false }
        assertEquals(NetworkMode.WEBVPN, ResolveNetworkModeUseCase(probe, noOverride())(NetworkMode.WEBVPN))
    }

    // —— 手动覆盖(强制,跳过探测) —— //

    @Test fun `manual override WEBVPN is forced and skips the probe`() = runTest {
        val probe = mockk<CampusReachabilityProbe>()   // 必须不被调用
        val credPrefs = mockk<ImportCredentialPrefs> { every { modeOverride() } returns NetworkMode.WEBVPN }
        // 即便 lastMode=LOCAL、即便在校内,手动校外也强制生效、不探测。
        assertEquals(NetworkMode.WEBVPN, ResolveNetworkModeUseCase(probe, credPrefs)(NetworkMode.LOCAL))
        coVerify(exactly = 0) { probe.localReachable() }
    }

    @Test fun `manual override LOCAL is forced and skips the probe`() = runTest {
        val probe = mockk<CampusReachabilityProbe>()
        val credPrefs = mockk<ImportCredentialPrefs> { every { modeOverride() } returns NetworkMode.LOCAL }
        assertEquals(NetworkMode.LOCAL, ResolveNetworkModeUseCase(probe, credPrefs)(NetworkMode.WEBVPN))
        coVerify(exactly = 0) { probe.localReachable() }
    }
}
