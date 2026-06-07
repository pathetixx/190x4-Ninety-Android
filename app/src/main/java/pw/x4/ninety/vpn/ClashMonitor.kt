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

/**
 * Read-only мост к работающему ядру (порт desktop clash-api на Android без REST):
 * CommandClient подписывается на CommandGroup и в реальном времени отдаёт группы
 * outbound'ов. Из них берём:
 *   • пинги нод — URLTestDelay элементов группы urltest "auto";
 *   • эффективный узел авто — .Selected этой же группы (как auto.now на desktop);
 *   • текущий выбор селектора — .Selected группы "proxy".
 * Клиент коннектится к in-process CommandServer (unix-сокет на basePath, который
 * уже выставлен Libbox.setup в сервисе). [urlTestAll] перетестирует весь профиль —
 * это и есть FAB-молния в Нодах.
 */
object ClashMonitor : CommandClientHandler {

    data class Snapshot(
        val delays: Map<String, Int> = emptyMap(), // clash-tag -> ms (0 / >=65000 = недоступна)
        val selectorNow: String? = null,            // "auto" | nodeTag — что выбрано в селекторе
        val autoNow: String? = null,                // эффективная нода авто-группы (быстрейшая)
        val connected: Boolean = false,
        val testing: Boolean = false,
    )

    var snapshot by mutableStateOf(Snapshot())
        private set

    private val main = Handler(Looper.getMainLooper())
    private var client: CommandClient? = null
    @Volatile private var running = false

    /** Поднять монитор (зовётся из VpnController при Connected). Идемпотентно. */
    fun start() {
        if (running) return
        running = true
        spawnConnect()
    }

    private fun spawnConnect() {
        Thread({
            if (!running) return@Thread
            try {
                val opts = CommandClientOptions()
                opts.addCommand(Libbox.CommandGroup)
                opts.statusInterval = 1_000_000_000L // 1s; группам не критично
                val c = CommandClient(this, opts) // gomobile: NewCommandClient → конструктор
                client = c
                c.connect() // дозванивается и стартует read-loop в горутине, возвращается сразу
                // Кикаем urltest сразу + повтор через 1.5с. Первый кик может уйти ДО того,
                // как ядро успело подписать group-стрим (тогда замеры начнутся только по
                // interval=600с → «авто долго собирает»). Повтор гарантирует старт замеров.
                kickAuto(c)
                try { Thread.sleep(1500) } catch (_: Throwable) {}
                if (running) kickAuto(c)
            } catch (_: Throwable) {
                // дозвониться не вышло — повторим, пока активны (ядро могло ещё не поднять сокет)
                if (running) { try { Thread.sleep(1000) } catch (_: Throwable) {}; if (running) spawnConnect() }
            }
        }, "ninety-clash").start()
    }

    private fun kickAuto(c: CommandClient) { try { c.urlTest("auto") } catch (_: Throwable) {} }

    /** Погасить монитор (зовётся из VpnController при Idle). */
    fun stop() {
        if (!running) return
        running = false
        val c = client
        client = null
        Thread({ try { c?.disconnect() } catch (_: Throwable) {} }, "ninety-clash-stop").start()
        main.post { snapshot = Snapshot() }
    }

    /** Перетест всех нод профиля (FAB-молния). Триггерит urltest-группу "auto". */
    fun urlTestAll() {
        val c = client ?: return
        main.post { snapshot = snapshot.copy(testing = true) }
        Thread({
            try { c.urlTest("auto") } catch (_: Throwable) {}
            // «testing» сбросит следующий writeGroups; страховка — таймаут.
            main.postDelayed({ snapshot = snapshot.copy(testing = false) }, 6000)
        }, "ninety-urltest").start()
    }

    // ── CommandClientHandler (сигнатуры — как в эталонном SFA) ──
    override fun connected() { main.post { snapshot = snapshot.copy(connected = true) } }
    override fun disconnected(message: String?) {
        main.post { snapshot = snapshot.copy(connected = false) }
        // Стрим групп умирает при reload ядра (смена ноды/профиля) — переподключаемся,
        // иначе пинги застывают после первого переключения.
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
    override fun writeStatus(message: StatusMessage) {}
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
            for (g in groups) {
                when (g.tag) {
                    "proxy" -> selectorNow = g.selected
                    "auto" -> {
                        autoNow = g.selected
                        val items = g.items
                        while (items.hasNext()) {
                            val it = items.next()
                            delays[it.tag] = it.urlTestDelay
                        }
                    }
                }
            }
        } catch (_: Throwable) { return }
        main.post {
            snapshot = snapshot.copy(
                delays = delays, selectorNow = selectorNow, autoNow = autoNow,
                connected = true, testing = false,
            )
        }
    }
}
