package pw.x4.ninety

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pw.x4.ninety.data.persistence.AndroidKeystoreSecretCodec

@RunWith(AndroidJUnit4::class)
class SecurityInstrumentedTest {
    private val aliases = mutableListOf<String>()

    @After
    fun removeTestKeys() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        aliases.forEach { alias -> runCatching { keyStore.deleteEntry(alias) } }
        aliases.clear()
    }

    @Test
    fun keystoreCiphertextRoundTripsWithoutPlaintext() {
        val alias = "ninety-test-${UUID.randomUUID()}".also(aliases::add)
        val codec = AndroidKeystoreSecretCodec(alias)
        val secret = "vless://credential@example.com:443"

        val encrypted = codec.encrypt(secret)

        assertTrue(encrypted.startsWith("enc:v1:"))
        assertFalse(encrypted.contains(secret))
        assertNotEquals(secret, encrypted)
        assertEquals(secret, codec.decrypt(encrypted))
    }

    @Test
    fun fileProviderSharesOnlyApprovedCacheDirectories() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = "${context.packageName}.fileprovider"
        val update = File(context.cacheDir, "updates/test.apk").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val diagnostics = File(context.cacheDir, "diagnostics/test.txt").apply {
            parentFile?.mkdirs()
            writeText("redacted")
        }
        val forbidden = File(context.cacheDir, "forbidden.txt").apply { writeText("private") }

        assertEquals("content", FileProvider.getUriForFile(context, authority, update).scheme)
        assertEquals("content", FileProvider.getUriForFile(context, authority, diagnostics).scheme)
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, forbidden)
        }
    }

    @Test
    fun mainActivitySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
            scenario.recreate()
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
        }
    }
}
