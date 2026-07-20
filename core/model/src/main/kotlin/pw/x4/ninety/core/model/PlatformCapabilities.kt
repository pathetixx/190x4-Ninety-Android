package pw.x4.ninety.core.model

/**
 * Возможности платформы, которыми должны руководствоваться config builder и UI.
 * Это заменяет разбросанные проверки "desktop/android" и не даёт показать
 * пользователю функцию, которую текущий runtime физически не умеет выполнить.
 */
data class PlatformCapabilities(
    val tun: Boolean,
    val systemProxy: Boolean,
    val perAppRouting: Boolean,
    val alwaysOnVpn: Boolean,
    val nativeKillSwitch: Boolean,
    val warp: Boolean,
    val naiveProxy: Boolean,
    val trustTunnel: Boolean,
    val dpiSidecar: Boolean,
) {
    companion object {
        val Android = PlatformCapabilities(
            tun = true,
            systemProxy = false,
            perAppRouting = true,
            alwaysOnVpn = true,
            nativeKillSwitch = false,
            warp = true,
            naiveProxy = false,
            trustTunnel = false,
            dpiSidecar = false,
        )

        val WindowsDesktop = PlatformCapabilities(
            tun = true,
            systemProxy = true,
            perAppRouting = true,
            alwaysOnVpn = false,
            nativeKillSwitch = true,
            warp = true,
            naiveProxy = true,
            trustTunnel = true,
            dpiSidecar = true,
        )
    }
}
