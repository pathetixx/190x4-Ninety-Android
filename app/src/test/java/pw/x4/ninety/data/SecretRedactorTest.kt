package pw.x4.ninety.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactorTest {
    @Test
    fun `redacts proxy links tokens and identifiers`() {
        val raw = """
            vless://secret-uuid@example.com:443?security=reality#node
            Authorization: Bearer abc.def.ghi
            {"password":"hunter2","private_key":"private","token":"token-value"}
            subscription=https://example.com/sub?token=secret
            uuid=123e4567-e89b-42d3-a456-426614174000
        """.trimIndent()

        val redacted = SecretRedactor.redact(raw)

        assertFalse(redacted.contains("secret-uuid"))
        assertFalse(redacted.contains("abc.def.ghi"))
        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("token-value"))
        assertFalse(redacted.contains("123e4567-e89b-42d3-a456-426614174000"))
        assertTrue(redacted.contains("<redacted>"))
    }
}
