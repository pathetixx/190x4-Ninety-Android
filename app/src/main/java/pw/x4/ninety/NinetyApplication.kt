package pw.x4.ninety

import android.app.Application
import android.util.Log
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.PersistenceRuntime
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Store
import pw.x4.ninety.ui.theme.NinetyState
import pw.x4.ninety.ui.theme.packById
import pw.x4.ninety.vpn.QualityRuntime
import pw.x4.ninety.vpn.VpnController

class NinetyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Перехват JVM-крашей в файл (диагностика без adb).
        Diag.installCrashHandler(this)
        // Снимок logcat: ловит нативный краш ядра (SIGSEGV/abort) прошлой сессии,
        // который мимо redirectStderr. Трейс остаётся в буфере после перезапуска.
        Diag.snapshotLogcat(this)

        // Room/DataStore миграция завершается и проверяется до чтения UI/VPN состояния.
        // При любой ошибке используется нетронутый legacy-снимок, marker не ставится.
        val storage = PersistenceRuntime.initialize(this)
        Prefs.initialize(this, storage.snapshot.preferences)
        Log.i(
            "NinetyStorage",
            "source=${storage.source}, verified=${storage.migrationVerified}, " +
                "profiles=${storage.snapshot.profiles.size}, nodes=${storage.snapshot.nodes.size}",
        )

        // Тема из DataStore-кэша до первой композиции.
        NinetyState.pack = packById(Prefs.get(this).themePack)
        // Загрузка проверенного Room-графа в синхронный compatibility Store.
        Store.init(this, storage.snapshot)

        // Самолечение после апдейта: при росте versionCode перечитать ноды из raw
        // текущим парсером (поднимает фиксы парсера/конфига — раньше требовалось
        // вручную передобавлять подписку). Локально, без сети.
        val prefs = Prefs.get(this)
        if (BuildConfig.VERSION_CODE > prefs.lastSeenVersionCode) {
            Store.reparseAllFromRaw()
            prefs.lastSeenVersionCode = BuildConfig.VERSION_CODE
        }
        // Настройки ядра (для tile-старта VPN без открытия аппы).
        Options.load(this)
        // История Quality Engine загружается после Store/Options и очищается от удалённых нод.
        QualityRuntime.initialize(this)
        // Контекст для пинка QS-плитки при смене состояния туннеля.
        VpnController.appContext = this
    }
}
