package pw.x4.ninety.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdaterVersionTest {
    @Test
    fun `compares numeric semantic components`() {
        assertTrue(Updater.isNewer("0.3.1", "0.3.0"))
        assertTrue(Updater.isNewer("1.0.0", "0.99.99"))
        assertFalse(Updater.isNewer("0.3.0", "0.3.0"))
        assertFalse(Updater.isNewer("0.2.9", "0.3.0"))
    }

    @Test
    fun `ignores prerelease suffix for numeric ordering`() {
        assertTrue(Updater.isNewer("0.4.0-rc1", "0.3.9"))
        assertFalse(Updater.isNewer("0.3.0-rc1", "0.3.0"))
    }
}
