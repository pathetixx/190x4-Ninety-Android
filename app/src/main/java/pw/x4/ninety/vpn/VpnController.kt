package pw.x4.ninety.vpn

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Состояние туннеля. */
enum class ConnState { Idle, Connecting, Connected, Stopping }

/**
 * Наблюдаемое состояние туннеля для UI. Сеттеры зовёт [NinetyVpnService] (из
 * фонового потока → постим в main). Старт/стоп инициирует Activity (нужен
 * VpnService.prepare consent), поэтому здесь только отражение состояния.
 */
object VpnController {
    var state by mutableStateOf(ConnState.Idle)
        private set
    var activeServer by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    val isActive: Boolean get() = state == ConnState.Connecting || state == ConnState.Connected

    fun markConnecting() = post { state = ConnState.Connecting; lastError = null }
    fun markConnected(name: String?) = post {
        state = ConnState.Connected; activeServer = name
        ClashMonitor.start() // ядро поднято → читаем пинги/эффективный узел
    }
    fun markIdle(error: String?) = post {
        state = ConnState.Idle
        activeServer = null
        ClashMonitor.stop()
        if (error != null) lastError = error
    }
}
