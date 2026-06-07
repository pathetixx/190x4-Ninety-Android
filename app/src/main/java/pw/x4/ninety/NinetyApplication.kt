package pw.x4.ninety

import android.app.Application
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.packById
import pw.x4.ninety.vpn.VpnController

class NinetyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Перехват JVM-крашей в файл (диагностика без adb).
        Diag.installCrashHandler(this)
        // Тема из Prefs до первой композиции.
        NinetyState.pack = packById(Prefs.get(this).themePack)
        // Загрузка узлов/активного.
        Store.init(this)
        // Настройки ядра (для tile-старта VPN без открытия аппы).
        Options.load(this)
        // Контекст для пинка QS-плитки при смене состояния туннеля.
        VpnController.appContext = this
    }
}
