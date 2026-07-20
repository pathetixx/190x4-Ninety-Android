package pw.x4.ninety.data.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EntityMapperTest {
    private val codec = PrefixCodec()
    private val mapper = EntityMapper(codec)

    @Test
    fun `node round trip preserves every sensitive value`() {
        val node = PersistedNode(
            id = "node-1",
            profileId = "profile-1",
            protocol = "hysteria2",
            name = "Secret node",
            host = "edge.example.com",
            port = 443,
            uuid = "private-uuid",
            password = "private-password",
            obfs = "salamander",
            obfsPassword = "private-obfs",
            raw = "hysteria2://private-password@edge.example.com:443",
            fromSubscription = true,
        )

        val entity = mapper.toEntity(node)
        assertFalse(entity.uuidCiphertext.contains(node.uuid))
        assertFalse(entity.passwordCiphertext.contains(node.password))
        assertFalse(entity.rawCiphertext.contains(node.raw))
        assertEquals(node, mapper.fromEntity(entity))
    }

    @Test
    fun `profile URL is encrypted and round trips`() {
        val profile = PersistedProfile(
            id = "profile-1",
            name = "Subscription",
            type = "sub",
            url = "https://example.com/private-token",
        )

        val entity = mapper.toEntity(profile)
        assertFalse(entity.urlCiphertext.contains(profile.url))
        assertEquals(profile, mapper.fromEntity(entity))
    }

    private class PrefixCodec : SecretCodec {
        override fun encrypt(value: String): String = if (value.isEmpty()) "" else "cipher:${value.reversed()}"
        override fun decrypt(value: String): String = if (value.isEmpty()) "" else value.removePrefix("cipher:").reversed()
    }
}
