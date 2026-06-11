package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.network.bit.CampusReachabilityProbe
import com.example.personal_studio.data.network.bit.NetworkMode
import javax.inject.Inject

/**
 * 决定本次同步的**首选**网络模式(全自动,校内优先;已无手动选择)。
 *
 * - `lastMode = LOCAL/null` → 直接 LOCAL(校外不可达由 [autoNetworkFallback] 等回退自动转 WEBVPN)。
 * - `lastMode = WEBVPN` → 用 [CampusReachabilityProbe] 快速探测校内可达性:可达(回到校园网)→ LOCAL
 *   (自动脱离「粘在校外」);不可达 → WEBVPN(仅多花一次 ~2s)。
 *
 * 解决「webvpn 在校内也能绕一圈成功 → lastMode 一直停在 WEBVPN、永远走慢路」:只在已记住 WEBVPN
 * 时才探一次校内。其余情况零探测开销。
 */
class ResolveNetworkModeUseCase @Inject constructor(
    private val probe: CampusReachabilityProbe,
) {
    suspend operator fun invoke(lastMode: NetworkMode?): NetworkMode {
        val remembered = lastMode ?: NetworkMode.LOCAL
        if (remembered == NetworkMode.LOCAL) return NetworkMode.LOCAL
        return if (probe.localReachable()) NetworkMode.LOCAL else NetworkMode.WEBVPN
    }
}
