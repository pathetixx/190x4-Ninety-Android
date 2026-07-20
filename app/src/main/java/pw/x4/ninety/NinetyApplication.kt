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
import pw.x4.ninety.vpn.WarpRuntime

class NinetyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.installCrashHandler(this)
        Diag.snapshotLogcat(this)

        val storage = PersistenceRuntime.initialize(this)
        Prefs.initialize(this, storage.snapshot.preferences)
        Log.i(
            "NinetyStorage",
            "source=${storage.source}, verified=${storage.migrationVerified}, " +
                "profiles=${storage.snapshot.profiles.size}, nodes=${storage.snapshot.nodes.size}",
        )

        NinetyState.pack = packById(Prefs.get(this).themePack)
        Store.init(this, storage.snapshot)

        val prefs = Prefs.get(this)
        if (BuildConfig.VERSION_CODE > prefs.lastSeenVersionCode) {
            Store.reparseAllFromRaw()
            prefs.lastSeenVersionCode = BuildConfig.VERSION_CODE
        }
        Options.load(this)
        WarpRuntime.initialize(this)
        QualityRuntime.initialize(this)
        VpnController.appContext = this
    }
}
