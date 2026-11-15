package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.CampusReachabilityProbe
import com.example.personal_studio.data.network.bit.NetworkMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveNetworkModeUseCaseTest {

    @Test fun `old campus mode and first login use VPN after leaving campus`() = runTest {
        val probe = mockk<CampusReachabilityProbe> { coEvery { localReachable() } returns false }
        val resolve = ResolveNetworkModeUseCase(probe)
        assertEquals(NetworkMode.WEBVPN, resolve(NetworkMode.LOCAL))
        assertEquals(NetworkMode.WEBVPN, resolve(null))
        coVerify(exactly = 2) { probe.localReachable() }
    }

    @Test fun `WEBVPN reverts to LOCAL when campus is reachable`() = runTest {
        val probe = mockk<CampusReachabilityProbe> { coEvery { localReachable() } returns true }
        assertEquals(NetworkMode.LOCAL, ResolveNetworkModeUseCase(probe)(NetworkMode.WEBVPN))
    }

    @Test fun `WEBVPN stays WEBVPN when campus is unreachable`() = runTest {
        val probe = mockk<CampusReachabilityProbe> { coEvery { localReachable() } returns false }
        assertEquals(NetworkMode.WEBVPN, ResolveNetworkModeUseCase(probe)(NetworkMode.WEBVPN))
    }
}
