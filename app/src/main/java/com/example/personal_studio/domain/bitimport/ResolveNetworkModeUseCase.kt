package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.CampusReachabilityProbe
import com.example.personal_studio.data.network.bit.NetworkMode
import javax.inject.Inject

/**
 * 决定本次同步的**首选**网络模式(全自动,校内优先;已无手动选择)。
 *
 * 每次先短时探测 jwms 是否可直连；历史模式不能代表当前网络。
 * 这样从校园 Wi-Fi 切到移动数据后，在登录之前就能选择 WebVPN。
 */
class ResolveNetworkModeUseCase @Inject constructor(
    private val probe: CampusReachabilityProbe,
) {
    suspend operator fun invoke(@Suppress("UNUSED_PARAMETER") lastMode: NetworkMode?): NetworkMode {
        // Keep the caller API stable; the previous success is only persisted as history.
        return if (probe.localReachable()) NetworkMode.LOCAL else NetworkMode.WEBVPN
    }
}
