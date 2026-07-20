package pw.x4.ninety.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeRegistryTest {
    @Test
    fun desktopThemeRegistryIsCompleteAndUnique() {
        val expected = setOf(
            "kurogane", "shiro", "sakura", "cyan", "glacier", "midnight",
            "synthwave", "ronin", "matrix", "amber", "mono", "command",
            "kintsugi", "aurora", "porcelain", "titanium",
        )
        assertEquals(expected, ThemePacks.map { it.id }.toSet())
        assertEquals(expected.size, ThemePacks.size)
        assertTrue(ThemePacks.all { it.label.isNotBlank() && it.kicker.isNotBlank() })
    }
}
