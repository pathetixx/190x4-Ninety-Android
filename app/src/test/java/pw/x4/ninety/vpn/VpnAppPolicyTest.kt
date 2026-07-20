package pw.x4.ninety.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpnAppPolicyTest {
    @Test
    fun `allowlist never mixes own package denylist`() {
        val policy = resolveVpnAppPolicy(
            included = listOf("com.example.one", "pw.x4.ninety"),
            excluded = listOf("com.example.blocked"),
            ownPackage = "pw.x4.ninety",
        )

        assertEquals(setOf("com.example.one"), policy.allowed)
        assertTrue(policy.disallowed.isEmpty())
    }

    @Test
    fun `denylist includes own package when allowlist is absent`() {
        val policy = resolveVpnAppPolicy(
            included = emptyList(),
            excluded = listOf("com.example.blocked"),
            ownPackage = "pw.x4.ninety",
        )

        assertTrue(policy.allowed.isEmpty())
        assertEquals(setOf("com.example.blocked", "pw.x4.ninety"), policy.disallowed)
    }
}
