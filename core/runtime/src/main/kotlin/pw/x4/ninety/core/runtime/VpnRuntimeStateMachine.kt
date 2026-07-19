package pw.x4.ninety.core.runtime

/** Commands accepted by the serialized Android runtime queue. */
sealed interface VpnRuntimeCommand {
    data object Start : VpnRuntimeCommand
    data object Reload : VpnRuntimeCommand
    data class Stop(val error: String? = null) : VpnRuntimeCommand
}

enum class VpnRuntimePhase {
    Idle,
    Starting,
    Connected,
    Reloading,
    Stopping,
}

data class VpnRuntimeState(
    val phase: VpnRuntimePhase = VpnRuntimePhase.Idle,
    val generation: Long = 0,
    val activeServer: String? = null,
    val lastError: String? = null,
)

data class VpnRuntimeTicket internal constructor(
    val generation: Long,
    val command: VpnRuntimeCommand,
)

/**
 * Thread-safe reducer for the VPN lifecycle.
 *
 * Requesting a command advances [VpnRuntimeState.generation] immediately. Long-running platform
 * work keeps the returned [VpnRuntimeTicket] and may publish a result only while that ticket is
 * current. A later stop/reload therefore invalidates every stale completion without depending on
 * thread interruption or libbox cancellation support.
 */
class VpnRuntimeStateMachine(
    initialState: VpnRuntimeState = VpnRuntimeState(),
    private val onStateChanged: (VpnRuntimeState) -> Unit = {},
) {
    private var state = initialState

    @Synchronized
    fun snapshot(): VpnRuntimeState = state

    fun request(command: VpnRuntimeCommand): VpnRuntimeTicket? {
        val result = synchronized(this) {
            val nextPhase = when (command) {
                VpnRuntimeCommand.Start -> when (state.phase) {
                    VpnRuntimePhase.Starting,
                    VpnRuntimePhase.Connected,
                    VpnRuntimePhase.Reloading -> return null

                    VpnRuntimePhase.Idle,
                    VpnRuntimePhase.Stopping -> VpnRuntimePhase.Starting
                }

                VpnRuntimeCommand.Reload -> when (state.phase) {
                    VpnRuntimePhase.Idle,
                    VpnRuntimePhase.Stopping -> return null

                    VpnRuntimePhase.Starting -> VpnRuntimePhase.Starting
                    VpnRuntimePhase.Connected,
                    VpnRuntimePhase.Reloading -> VpnRuntimePhase.Reloading
                }

                is VpnRuntimeCommand.Stop -> VpnRuntimePhase.Stopping
            }

            val ticket = VpnRuntimeTicket(state.generation + 1, command)
            val next = state.copy(
                phase = nextPhase,
                generation = ticket.generation,
                lastError = null,
            )
            state = next
            ticket to next
        }
        onStateChanged(result.second)
        return result.first
    }

    fun completeConnected(ticket: VpnRuntimeTicket, activeServer: String?): Boolean = transition(ticket) {
        if (phase != VpnRuntimePhase.Starting && phase != VpnRuntimePhase.Reloading) return@transition null
        copy(
            phase = VpnRuntimePhase.Connected,
            activeServer = activeServer,
            lastError = null,
        )
    }

    fun completeStopped(ticket: VpnRuntimeTicket): Boolean = transition(ticket) {
        if (ticket.command !is VpnRuntimeCommand.Stop) return@transition null
        copy(
            phase = VpnRuntimePhase.Idle,
            activeServer = null,
            lastError = ticket.command.error,
        )
    }

    fun fail(ticket: VpnRuntimeTicket, error: String): Boolean = transition(ticket) {
        copy(
            phase = VpnRuntimePhase.Idle,
            activeServer = null,
            lastError = error,
        )
    }

    @Synchronized
    fun isCurrent(ticket: VpnRuntimeTicket): Boolean = state.generation == ticket.generation

    private fun transition(
        ticket: VpnRuntimeTicket,
        reducer: VpnRuntimeState.() -> VpnRuntimeState?,
    ): Boolean {
        val next = synchronized(this) {
            if (state.generation != ticket.generation) return false
            val reduced = state.reducer() ?: return false
            state = reduced
            reduced
        }
        onStateChanged(next)
        return true
    }
}
