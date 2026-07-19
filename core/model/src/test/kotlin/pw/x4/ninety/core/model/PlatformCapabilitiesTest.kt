package pw.x4.ninety.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilitiesTest {
    @Test
    fun `android exposes only native mobile capabilities`() {
        val android = PlatformCapabilities.Android

        assertTrue(android.tun)
        assertTrue(android.perAppRouting)
        assertTrue(android.alwaysOnVpn)
        assertFalse(android.systemProxy)
        assertFalse(android.nativeKillSwitch)
        assertFalse(android.dpiSidecar)
    }
}
