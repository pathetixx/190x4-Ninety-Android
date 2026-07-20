package pw.x4.ninety.core.model

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarpTest {
    @Test
    fun `valid registration is normalized`() {
        val result = WarpRegistrationSanitizer.sanitize(registration())

        assertTrue(result.valid)
        assertEquals("172.16.0.2", result.registration?.localIpv4)
        assertEquals("2606:4700:110:8765::2", result.registration?.localIpv6)
    }

    @Test
    fun `invalid keys addresses and license are rejected`() {
        val result = WarpRegistrationSanitizer.sanitize(
            registration().copy(
                privateKey = "bad",
                localIpv4 = "01.2.3.4",
                localIpv6 = "not-ipv6",
                license = "short",
            ),
        )

        assertFalse(result.valid)
        assertNull(result.registration)
        assertTrue(result.errors.any { it.contains("private key") })
        assertTrue(result.errors.any { it.contains("IPv4") })
        assertTrue(result.errors.any { it.contains("IPv6") })
        assertTrue(result.errors.any { it.contains("license") })
    }

    @Test
    fun `settings clamp ranges and default endpoint`() {
        val clean = WarpSettings(
            endpoint = " ",
            mtu = 9999,
            customCount = WarpRange(10, 2),
            customSize = WarpRange(-10, 9999),
            customDelay = WarpRange(6000, -1),
        ).normalized()

        assertEquals(WarpSettings.DEFAULT_ENDPOINT, clean.endpoint)
        assertEquals(1500, clean.mtu)
        assertEquals(WarpRange(2, 10), clean.customCount)
        assertEquals(WarpRange(1, 1500), clean.customSize)
        assertEquals(WarpRange(0, 5000), clean.customDelay)
    }

    @Test
    fun `license accepts only exact desktop contract length`() {
        assertNull(WarpRegistrationSanitizer.normalizeLicense(" "))
        assertEquals("A".repeat(26), WarpRegistrationSanitizer.normalizeLicense(" ${"A".repeat(26)} "))
        assertTrue(runCatching { WarpRegistrationSanitizer.normalizeLicense("A".repeat(25)) }.isFailure)
    }

    private fun registration() = WarpRegistration(
        registrationId = " reg-id ",
        accountId = "account-id",
        accessToken = " token ",
        privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 }),
        peerPublicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 }),
        localIpv4 = "172.16.0.2/32",
        localIpv6 = "2606:4700:110:8765::2/128",
        clientId = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
        registeredAt = "2026-07-20T00:00:00Z",
    )
}
