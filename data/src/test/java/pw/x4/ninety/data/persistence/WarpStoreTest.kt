package pw.x4.ninety.data.persistence

import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pw.x4.ninety.core.model.WarpRegistration

class WarpStoreTest {
    @Test
    fun `registration round trips through encrypted atomic store`() {
        val directory = createTempDirectory("warp-store").toFile()
        val file = directory.resolve("warp.enc")
        val store = WarpStore(file, PrefixCodec())
        val expected = registration()

        store.write(expected)

        assertTrue(file.exists())
        assertFalse(file.readText().contains(expected.privateKey))
        assertEquals(expected, store.read())
        assertFalse(directory.resolve("warp.enc.new").exists())
    }

    @Test
    fun `invalid or corrupt payload never becomes a registration`() {
        val file = Files.createTempFile("warp-corrupt", ".enc").toFile()
        val store = WarpStore(file, PrefixCodec())

        file.writeText("not-encrypted")
        assertNull(store.read())

        file.writeText("sealed:${WarpWire.encode(registration().copy(peerPublicKey = "bad"))}")
        assertNull(store.read())
    }

    @Test
    fun `clear removes committed and temporary payloads`() {
        val directory = createTempDirectory("warp-clear").toFile()
        val file = directory.resolve("warp.enc")
        val temporary = directory.resolve("warp.enc.new")
        val store = WarpStore(file, PrefixCodec())
        store.write(registration())
        temporary.writeText("partial")

        store.clear()

        assertFalse(file.exists())
        assertFalse(temporary.exists())
        assertNull(store.read())
    }

    private fun registration() = WarpRegistration(
        registrationId = "registration-id",
        accountId = "account-id",
        accessToken = "access-token",
        privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 }),
        peerPublicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 }),
        localIpv4 = "172.16.0.2",
        localIpv6 = "2606:4700:110:8765::2",
        clientId = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
        warpPlus = true,
        accountType = "plus",
        registeredAt = "2026-07-20T00:00:00Z",
    )

    private class PrefixCodec : SecretCodec {
        override fun encrypt(value: String): String = "sealed:$value"
        override fun decrypt(value: String): String {
            require(value.startsWith("sealed:"))
            return value.removePrefix("sealed:")
        }
    }
}
