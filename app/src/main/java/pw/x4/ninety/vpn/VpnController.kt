package pw.x4.ninety.vpn

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pw.x4.ninety.core.runtime.VpnRuntimeCommand
import pw.x4.ninety.core.runtime.VpnRuntimePhase
import pw.x4.ninety.core.runtime.VpnRuntimeState
import pw.x4.ninety.core.runtime.VpnRuntimeStateMachine
import pw.x4.ninety.core.runtime.VpnRuntimeTicket

/** Состояние туннеля для Android UI. Reloading отображается как Connecting. */
enum class ConnState { Idle, Connecting, Connected, Stopping }

data class VpnSnapshot(
    val state: ConnState = ConnState.Idle,
    val generation: Long = 0,
    val activeServer: String? = null,
    val lastError: String? = null,
)

/**
 * Единая наблюдаемая граница VPN runtime.
 *
 * State machine меняет generation сразу при поступлении команды. Поэтому долгий start/reload
 * не может опубликовать результат после более нового stop/reload, даже если libbox-вызов нельзя
 * прервать через Thread.interrupt().
 */
object VpnController {
    private val main = Handler(Looper.getMainLooper())
    private val _snapshot = MutableStateFlow(VpnSnapshot())
    val snapshot: StateFlow<VpnSnapshot> = _snapshot.asStateFlow()

    private val machine = VpnRuntimeStateMachine(onStateChanged = ::publish)

    /** Контекст приложения — для пинка QS-плитки на смену состояния. Ставит Application. */
    @Volatile
    var appContext: Context? = null

    val state: ConnState get() = snapshot.value.state
    val activeServer: String? get() = snapshot.value.activeServer
    val lastError: String? get() = snapshot.value.lastError
    val isActive: Boolean get() = state == ConnState.Connecting || state == ConnState.Connected

    internal fun requestStart(): VpnRuntimeTicket? = machine.request(VpnRuntimeCommand.Start)

    internal fun requestReload(): VpnRuntimeTicket? = machine.request(VpnRuntimeCommand.Reload)

    internal fun requestStop(error: String?): VpnRuntimeTicket =
        checkNotNull(machine.request(VpnRuntimeCommand.Stop(error)))

    internal fun completeConnected(ticket: VpnRuntimeTicket, name: String?): Boolean =
        machine.completeConnected(ticket, name)

    internal fun completeStopped(ticket: VpnRuntimeTicket): Boolean =
        machine.completeStopped(ticket)

    internal fun fail(ticket: VpnRuntimeTicket, error: String): Boolean =
        machine.fail(ticket, error)

    internal fun isCurrent(ticket: VpnRuntimeTicket): Boolean = machine.isCurrent(ticket)

    private fun publish(runtime: VpnRuntimeState) {
        val previous = _snapshot.value
        val next = VpnSnapshot(
            state = runtime.phase.toConnState(),
            generation = runtime.generation,
            activeServer = runtime.activeServer,
            lastError = runtime.lastError,
        )
        _snapshot.value = next

        post {
            if (previous.state != ConnState.Connected && next.state == ConnState.Connected) {
                ClashMonitor.start()
            }
            if (next.state == ConnState.Idle || next.state == ConnState.Stopping) {
                ClashMonitor.stop()
            }
            refreshTile()
        }
    }

    private fun VpnRuntimePhase.toConnState(): ConnState = when (this) {
        VpnRuntimePhase.Idle -> ConnState.Idle
        VpnRuntimePhase.Starting,
        VpnRuntimePhase.Reloading,
        -> ConnState.Connecting

        VpnRuntimePhase.Connected -> ConnState.Connected
        VpnRuntimePhase.Stopping -> ConnState.Stopping
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** Перезапросить listening у QS-плитки — она пересинхронизирует состояние из StateFlow. */
    private fun refreshTile() {
        val context = appContext ?: return
        runCatching {
            TileService.requestListeningState(
                context,
                ComponentName(context, NinetyTileService::class.java),
            )
        }
    }
}
