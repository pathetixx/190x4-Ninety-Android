package pw.x4.ninety.vpn

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Состояние туннеля. */
enum class ConnState { Idle, Connecting, Connected, Stopping }

/**
 * Заглушка контроллера туннеля (милстоун 1). Хранит наблюдаемое состояние для UI.
 * Милстоун 2 заменит тело на реальный VpnService + libbox (BoxService/CommandClient),
 * сохранив публичный контракт [state]/[toggle].
 */
object VpnController {
    var state by mutableStateOf(ConnState.Idle)
        private set

    /** Текущий сервер для hero/статуса (имя ноды). null = не выбран. */
    var activeServer by mutableStateOf<String?>(null)
        private set

    /** Заглушка переключения: имитирует Connecting->Connected без реального ядра. */
    fun toggle() {
        state = when (state) {
            ConnState.Idle -> ConnState.Connecting
            ConnState.Connecting -> ConnState.Connected
            ConnState.Connected -> ConnState.Idle
            ConnState.Stopping -> ConnState.Idle
        }
    }
}
