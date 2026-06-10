package com.example.personal_studio.domain.bitimport

import com.example.personal_studio.data.local.datastore.ImportCredentialPrefs
import com.example.personal_studio.data.network.bit.CampusReachabilityProbe
import com.example.personal_studio.data.network.bit.NetworkMode
import javax.inject.Inject

/**
 * 决定本次同步的**首选**网络模式。
 *
 * 1. **手动覆盖优先**:用户在登录页选了「校内/校外」([ImportCredentialPrefs.modeOverride] 非 null)→
 *    **强制**用该模式、**跳过探测**(尊重用户;否则手动选择形同无效)。
 * 2. 「自动」(override = null)时按**校内优先**:
 *    - `lastMode = LOCAL/null` → 直接 LOCAL(校外不可达由 [autoNetworkFallback] 等回退自动转 WEBVPN)。
 *    - `lastMode = WEBVPN` → 用 [CampusReachabilityProbe] 快速探测校内可达性:可达(回到校园网)→ LOCAL
 *      (自动脱离「粘在校外」);不可达 → WEBVPN(仅多花一次 ~2s)。
 *
 * 解决两件事:① webvpn 在校内也能绕一圈成功 → 一直粘校外(自动模式靠探测脱离);② 手动指定的模式
 * 之前只作用于登录、之后被探测覆盖(现在持久化为 override,全程强制)。
 */
class ResolveNetworkModeUseCase @Inject constructor(
    private val probe: CampusReachabilityProbe,
    private val credPrefs: ImportCredentialPrefs,
) {
    suspend operator fun invoke(lastMode: NetworkMode?): NetworkMode {
        credPrefs.modeOverride()?.let { return it }   // 手动覆盖:强制,跳过探测
        val remembered = lastMode ?: NetworkMode.LOCAL
        if (remembered == NetworkMode.LOCAL) return NetworkMode.LOCAL
        return if (probe.localReachable()) NetworkMode.LOCAL else NetworkMode.WEBVPN
    }
}
