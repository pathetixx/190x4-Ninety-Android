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
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Store

object ClashMonitor : CommandClientHandler {
    data class Snapshot(
        val delays: Map<String, Int> = emptyMap(),
        val selectorNow: String? = null,
        val autoNow: String? = null,
        val connected: Boolean = false,
        val testing: Boolean = false,
        val up: Long = 0,
        val down: Long = 0,
    ) {
        fun effectiveTag(): String? = when (val selected = selectorNow) {
            null, "auto" -> autoNow
            else -> selected
        }

        fun effectiveDelay(): Int? = effectiveTag()?.let { delays[it] }
    }

    var snapshot by mutableStateOf(Snapshot())
        private set

    private val main = Handler(Looper.getMainLooper())
    private var client: CommandClient? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        spawnConnect()
    }

    private fun spawnConnect() {
        Thread({
            if (!running) return@Thread
            try {
                val options = CommandClientOptions()
                options.addCommand(Libbox.CommandGroup)
                options.addCommand(Libbox.CommandStatus)
                options.statusInterval = 1_000_000_000L
                val next = CommandClient(this, options)
                client = next
                next.connect()
                kickAuto(next)
                try { Thread.sleep(1500) } catch (_: Throwable) {}
                if (running) kickAuto(next)
            } catch (_: Throwable) {
                if (running) {
                    try { Thread.sleep(1000) } catch (_: Throwable) {}
                    if (running) spawnConnect()
                }
            }
        }, "ninety-clash").start()
    }

    private fun kickAuto(commandClient: CommandClient) {
        try { commandClient.urlTest("auto") } catch (_: Throwable) {}
    }

    fun stop() {
        if (!running) return
        running = false
        val current = client
        client = null
        Thread({ try { current?.disconnect() } catch (_: Throwable) {} }, "ninety-clash-stop").start()
        QualityRuntime.markTunnelStopped()
        main.post { snapshot = Snapshot() }
    }

    fun urlTestAll() {
        val current = client ?: return
        main.post { snapshot = snapshot.copy(testing = true) }
        Thread({
            try { current.urlTest("auto") } catch (_: Throwable) {}
            main.postDelayed({ snapshot = snapshot.copy(testing = false) }, 6000)
        }, "ninety-urltest").start()
    }

    override fun connected() {
        main.post { snapshot = snapshot.copy(connected = true) }
    }

    override fun disconnected(message: String?) {
        main.post { snapshot = snapshot.copy(connected = false) }
        if (running) {
            val old = client
            client = null
            Thread({
                try { old?.disconnect() } catch (_: Throwable) {}
                try { Thread.sleep(600) } catch (_: Throwable) {}
                if (running) spawnConnect()
            }, "ninety-clash-reconn").start()
        }
    }

    override fun setDefaultLogLevel(level: Int) {}
    override fun clearLogs() {}
    override fun writeLogs(messageList: LogIterator?) {}

    override fun writeStatus(message: StatusMessage) {
        val up = try { message.uplink } catch (_: Throwable) { 0L }
        val down = try { message.downlink } catch (_: Throwable) { 0L }
        main.post { snapshot = snapshot.copy(up = up, down = down, connected = true) }
    }

    override fun initializeClashMode(modeList: StringIterator, currentMode: String) {}
    override fun updateClashMode(newMode: String) {}
    override fun writeConnectionEvents(events: ConnectionEvents?) {}

    override fun writeGroups(message: OutboundGroupIterator?) {
        if (message == null) return
        val delays = HashMap<String, Int>()
        var selectorNow: String? = null
        var autoNow: String? = null
        try {
            val groups = ArrayList<OutboundGroup>()
            while (message.hasNext()) groups.add(message.next())
            groups.forEach { group ->
                when (group.tag) {
                    "proxy" -> selectorNow = group.selected
                    "auto" -> {
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

        main.post {
            val nodes = Store.supportedActiveNodes()
            val idByTag = nodes.associate { ConfigBuilder.tagOf(it) to it.id }
            val completeCurrentBatch = idByTag.isNotEmpty() && idByTag.keys.all(delays::containsKey)
            val warpDirect = Options.data.warpEnabled && Options.data.warpMode == "direct"
            if (completeCurrentBatch && !warpDirect) {
                QualityRuntime.record(
                    candidateNodeIds = nodes.map { it.id },
                    delaysByNodeId = idByTag.mapValues { (tag, _) -> delays[tag] },
                    rawAutoNodeId = autoNow?.let(idByTag::get),
                )
            }
            snapshot = snapshot.copy(
                delays = delays,
                selectorNow = selectorNow,
                autoNow = autoNow,
                connected = true,
                testing = false,
            )
        }
    }
}
