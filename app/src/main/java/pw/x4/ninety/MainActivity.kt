package pw.x4.ninety

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import pw.x4.ninety.data.Prefs
import pw.x4.ninety.data.Store
import pw.x4.ninety.data.Updater
import pw.x4.ninety.ui.NinetyApp
import pw.x4.ninety.ui.theme.NinetyTheme
import pw.x4.ninety.vpn.NinetyVpnService
import pw.x4.ninety.vpn.VpnController

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpn()
        else Toast.makeText(this, "Нужно согласие на VPN", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NinetyTheme {
                NinetyApp(onToggleVpn = ::toggleVpn)
            }
        }
        maybeAutoConnect()
        maybeCheckUpdate()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Тоггл VPN по интенту от QS-плитки (когда нужен был consent — плитка шлёт нас сюда). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE) {
            intent.action = null // одноразово: не повторять тоггл при пересоздании активити
            toggleVpn()
        }
    }

    /** Тихая проверка обновлений при запуске → OTA-модалка, если версия новее и не «отложена». */
    private fun maybeCheckUpdate() {
        val prefs = Prefs.get(this)
        if (!prefs.autoUpdateCheck) return
        Updater.check(onLoading = {}, onDone = { newer, _ ->
            if (newer != null && newer.version != prefs.skippedVersion) {
                Updater.Available.release = newer
            }
        })
    }

    /** Автоподключение к последней ноде при холодном старте. Тихо — только если
     *  согласие на VPN уже выдано (prepare==null); диалог consent не навязываем. */
    private fun maybeAutoConnect() {
        if (Prefs.get(this).autoConnect &&
            !VpnController.isActive &&
            Store.activeNode() != null &&
            VpnService.prepare(this) == null
        ) startVpn()
    }

    private fun toggleVpn() {
        if (VpnController.isActive) {
            NinetyVpnService.stop(this)
            return
        }
        if (Store.activeNode() == null) {
            Toast.makeText(this, "Сначала добавьте и выберите узел во вкладке «Узлы»", Toast.LENGTH_LONG).show()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnConsent.launch(prepare) else startVpn()
    }

    private fun startVpn() {
        if (Store.activeNode() == null) return
        NinetyVpnService.start(this)
    }

    companion object {
        const val ACTION_TOGGLE = "pw.x4.ninety.action.TOGGLE"
    }
}
