package pw.x4.ninety.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import pw.x4.ninety.MainActivity
import pw.x4.ninety.data.Store

/**
 * Плитка «Быстрых настроек» (шторка) — вкл/выкл VPN одним тапом без открытия аппы.
 * Если нужен consent на VpnService или не выбран узел — открываем MainActivity
 * (consent-диалог нельзя показать из tile напрямую).
 */
class NinetyTileService : TileService() {

    override fun onStartListening() = sync()

    override fun onClick() {
        if (VpnController.isActive) {
            NinetyVpnService.stop(this)
            setTile(false, "Отключение…") // оптимистично; финальное состояние пнёт VpnController
        } else {
            if (Store.activeNode() == null && !Store.isAutoActive) {
                openApp(null) // нечего подключать — пусть выберут узел
                return
            }
            val prepare = VpnService.prepare(this)
            if (prepare != null) { openApp(MainActivity.ACTION_TOGGLE); return } // consent → через активити
            NinetyVpnService.start(this)
            setTile(true, "Подключение…") // мгновенный отклик плитки, не ждём markConnecting
        }
    }

    private fun sync() {
        val subtitle = when (VpnController.state) {
            ConnState.Connected -> "Защищено"
            ConnState.Connecting -> "Подключение…"
            ConnState.Stopping -> "Отключение…"
            else -> "Отключено"
        }
        setTile(VpnController.isActive, subtitle)
    }

    private fun setTile(active: Boolean, subtitle: String) {
        val t = qsTile ?: return
        t.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Ninety"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = subtitle
        t.updateTile()
    }

    /**
     * Android 14+ требует PendingIntent и бросает UnsupportedOperationException для
     * старого overload. На API 26–33 PendingIntent-overload ещё отсутствует, поэтому
     * legacy-вызов остаётся только в корректно ограждённой ветке.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(action: String?) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action != null) intent.action = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
