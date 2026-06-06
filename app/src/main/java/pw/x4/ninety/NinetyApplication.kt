package pw.x4.ninety

import android.app.Application
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.packById

class NinetyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Тема из Prefs до первой композиции.
        NinetyState.pack = packById(Prefs.get(this).themePack)
    }
}
