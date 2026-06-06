package pw.x4.ninety

import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import pw.x4.ninety.data.Store
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
}
