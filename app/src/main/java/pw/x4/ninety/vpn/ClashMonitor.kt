package pw.x4.ninety.vpn

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import pw.x4.ninety.data.Store

enum class ProbePhase {
    Offline,
    Connecting,
    Testing,
    Partial,
    Ready,
    Error,
}

object ClashMonitor {
    data class Snapshot(
        val delays: Map<String, Int> = emptyMap(),
        val selectorNow: String? = null,
        val autoNow: String? = null,
        val connected: Boolean = false,
        val phase: ProbePhase = ProbePhase.Offline,
        val probeCycle: Long = 0,
        val measuredAtMs: Long = 0,
        val lastError: String? = null,
        val up: Long = 0,
        val down: Long = 0,
    ) {
        val testing: Boolean get() = phase == ProbePhase.Testing

        fun effectiveTag(): String? = when (val selected = selectorNow) {
            null, "auto" -> autoNow
            else -> selected
        }

        fun effectiveDelay(): Int? = effectiveTag()?.let(delays::get)?.takeIf(::validDelay)
    }

    var snapshot by mutableStateOf(Snapshot())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ninety-probe")
    }
    private val generation = AtomicLong()
    private val clientEpoch = AtomicLong()
    private val cycle = AtomicLong()

    @Volatile private var running = false
    @Volatile private var client: CommandClient? = null
    @Volatile private var currentGeneration = 0L
    @Volatile private var currentClientEpoch = 0L
    @Volatile private var activeCycle = 0L
    @Volatile private var resultCycle = 0L
    @Volatile private var reconnectAttempt = 0
    @Volatile private var timeoutFuture: ScheduledFuture<*>? = null

    fun start() {
        if (running) return
        running = true
        reconnectAttempt = 0
        val next = generation.incrementAndGet()
        currentGeneration = next
        publish { Snapshot(phase = ProbePhase.Connecting) }
        scheduleConnect(next, 0)
    }

    fun stop() {
        if (!running && snapshot.phase == ProbePhase.Offline) return
        running = false
        currentGeneration = generation.incrementAndGet()
        currentClientEpoch = clientEpoch.incrementAndGet()
        timeoutFuture?.cancel(false)
        timeoutFuture = null
        val old = client
        client = null
        executor.execute { runCatching { old?.disconnect() } }
        publish { Snapshot() }
    }

    fun urlTestAll() {
        val session = currentGeneration
        executor.execute { requestProbe(session, manual = true) }
    }

    private fun scheduleConnect(session: Long, delayMs: Long) {
        executor.schedule({ connect(session) }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun connect(session: Long) {
        if (!isCurrent(session)) return
        val epoch = clientEpoch.incrementAndGet()
        currentClientEpoch = epoch
        publish { it.copy(phase = ProbePhase.Connecting, lastError = null) }
        val old = client
        client = null
        runCatching { old?.disconnect() }

        try {
            val options = CommandClientOptions().apply {
                addCommand(Libbox.CommandStatus)
                if (canProbe()) addCommand(Libbox.CommandGroup)
                statusInterval = STATUS_INTERVAL_NS
            }
            val next = CommandClient(SessionHandler(session, epoch), options)
            if (!isCurrent(session, epoch)) {
                runCatching { next.disconnect() }
                return
            }
            client = next
            next.connect()
        } catch (error: Throwable) {
            scheduleReconnect(session, epoch, error.message ?: "CommandClient connection failed")
        }
    }

    private fun requestProbe(session: Long, manual: Boolean) {
        if (!isCurrent(session)) return
        if (!canProbe()) {
            publish {
                it.copy(
                    phase = ProbePhase.Ready,
                    lastError = if (manual) "В режиме WARP Direct proxy-пинги не используются" else null,
                )
            }
            return
        }
        val current = client
        if (current == null) {
            scheduleReconnect(session, currentClientEpoch, "Монитор ядра ещё не подключён")
            return
        }

        val probeCycle = cycle.incrementAndGet()
        activeCycle = probeCycle
        publish {
            it.copy(
                phase = ProbePhase.Testing,
                probeCycle = probeCycle,
                lastError = null,
            )
        }
        timeoutFuture?.cancel(false)
        runCatching { current.urlTest(AUTO_GROUP) }
            .onFailure { error ->
                publish {
                    it.copy(
                        phase = ProbePhase.Error,
                        lastError = error.message ?: "Не удалось запустить проверку",
                    )
                }
                return
            }

        executor.schedule({
            if (isCurrent(session) && activeCycle == probeCycle && resultCycle < probeCycle) {
                runCatching { client?.urlTest(AUTO_GROUP) }
            }
        }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS)

        timeoutFuture = executor.schedule({
            if (!isCurrent(session) || activeCycle != probeCycle) return@schedule
            publish { currentSnapshot ->
                if (currentSnapshot.probeCycle != probeCycle || !currentSnapshot.testing) {
                    currentSnapshot
                } else if (currentSnapshot.delays.values.any(::validDelay)) {
                    currentSnapshot.copy(
                        phase = ProbePhase.Partial,
                        lastError = "Часть нод не вернула задержку",
                    )
                } else {
                    currentSnapshot.copy(
                        phase = ProbePhase.Error,
                        lastError = "Проверка задержки не ответила за ${PROBE_TIMEOUT_MS / 1000} с",
                    )
                }
            }
        }, PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun handleConnected(session: Long, epoch: Long) {
        if (!isCurrent(session, epoch)) return
        reconnectAttempt = 0
        publish {
            it.copy(
                connected = true,
                phase = if (canProbe()) ProbePhase.Connecting else ProbePhase.Ready,
                lastError = null,
            )
        }
        if (canProbe()) {
            executor.schedule({
                if (isCurrent(session, epoch)) requestProbe(session, manual = false)
            }, INITIAL_PROBE_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun handleDisconnected(session: Long, epoch: Long, message: String?) {
        if (!isCurrent(session, epoch)) return
        client = null
        scheduleReconnect(session, epoch, message?.takeIf(String::isNotBlank) ?: "Монитор ядра отключён")
    }

    private fun scheduleReconnect(session: Long, epoch: Long, message: String) {
        if (!isCurrent(session, epoch)) return
        val delay = (RECONNECT_BASE_MS shl reconnectAttempt.coerceAtMost(4)).coerceAtMost(RECONNECT_MAX_MS)
        reconnectAttempt++
        publish {
            it.copy(
                connected = false,
                phase = ProbePhase.Connecting,
                lastError = message,
            )
        }
        scheduleConnect(session, delay)
    }

    private fun handleStatus(session: Long, epoch: Long, message: StatusMessage) {
        if (!isCurrent(session, epoch)) return
        val up = runCatching { message.uplink }.getOrDefault(0L)
        val down = runCatching { message.downlink }.getOrDefault(0L)
        publish { it.copy(up = up, down = down, connected = true) }
    }

    private fun handleGroups(session: Long, epoch: Long, message: OutboundGroupIterator?) {
        if (!isCurrent(session, epoch) || message == null) return
        val delays = linkedMapOf<String, Int>()
        var selectorNow: String? = null
        var autoNow: String? = null
        try {
            val groups = ArrayList<OutboundGroup>()
            while (message.hasNext()) groups += message.next()
            groups.forEach { group ->
                when (group.tag) {
                    SELECTOR_GROUP -> selectorNow = group.selected
                    AUTO_GROUP -> {
                        autoNow = group.selected
                        val items = group.items
                        while (items.hasNext()) {
                            val item = items.next()
                            delays[item.tag] = item.urlTestDelay
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            return
        }

        val completedCycle = activeCycle
        if (delays.isNotEmpty()) resultCycle = completedCycle
        publish { previous ->
            val expectedTags = Store.supportedActiveNodes().map { ConfigBuilder.tagOf(it) }.toSet()
            val currentDelays = delays.filterKeys { it in expectedTags }
            val allReturned = expectedTags.isNotEmpty() && expectedTags.all(currentDelays::containsKey)
            val anyReturned = currentDelays.isNotEmpty()
            val phase = when {
                !canProbe() -> ProbePhase.Ready
                allReturned -> ProbePhase.Ready
                anyReturned -> ProbePhase.Partial
                previous.testing -> ProbePhase.Testing
                else -> previous.phase
            }
            previous.copy(
                delays = if (anyReturned) currentDelays else previous.delays,
                selectorNow = selectorNow ?: previous.selectorNow,
                autoNow = autoNow ?: previous.autoNow,
                connected = true,
                phase = phase,
                probeCycle = completedCycle,
                measuredAtMs = if (anyReturned) System.currentTimeMillis() else previous.measuredAtMs,
                lastError = when {
                    allReturned -> null
                    anyReturned -> "Получены не все результаты"
                    else -> previous.lastError
                },
            )
        }
    }

    private fun canProbe(): Boolean =
        TunnelModes.current() != TunnelMode.WARP_DIRECT && Store.supportedActiveNodes().isNotEmpty()

    private fun isCurrent(session: Long): Boolean = running && session == currentGeneration

    private fun isCurrent(session: Long, epoch: Long): Boolean =
        isCurrent(session) && epoch == currentClientEpoch

    private fun publish(transform: (Snapshot) -> Snapshot) {
        main.post { snapshot = transform(snapshot) }
    }

    private class SessionHandler(
        private val session: Long,
        private val epoch: Long,
    ) : CommandClientHandler {
        override fun connected() = handleConnected(session, epoch)
        override fun disconnected(message: String?) = handleDisconnected(session, epoch, message)
        override fun setDefaultLogLevel(level: Int) = Unit
        override fun clearLogs() = Unit
        override fun writeLogs(messageList: LogIterator?) = Unit
        override fun writeStatus(message: StatusMessage) = handleStatus(session, epoch, message)
        override fun initializeClashMode(modeList: StringIterator, currentMode: String) = Unit
        override fun updateClashMode(newMode: String) = Unit
        override fun writeConnectionEvents(events: ConnectionEvents?) = Unit
        override fun writeGroups(message: OutboundGroupIterator?) = handleGroups(session, epoch, message)
    }

    private fun validDelay(value: Int): Boolean = value in 1 until INVALID_DELAY

    private const val SELECTOR_GROUP = "proxy"
    private const val AUTO_GROUP = "auto"
    private const val INVALID_DELAY = 65_000
    private const val STATUS_INTERVAL_NS = 1_000_000_000L
    private const val INITIAL_PROBE_DELAY_MS = 350L
    private const val RETRY_DELAY_MS = 2_000L
    private const val PROBE_TIMEOUT_MS = 10_000L
    private const val RECONNECT_BASE_MS = 500L
    private const val RECONNECT_MAX_MS = 8_000L
}
