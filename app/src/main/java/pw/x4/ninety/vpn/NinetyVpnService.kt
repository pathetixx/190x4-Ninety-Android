package pw.x4.ninety.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.Notification as LibboxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import io.nekohasekai.libbox.NetworkInterface as LbNetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import pw.x4.ninety.MainActivity
import pw.x4.ninety.R
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Store
import java.io.File
import java.net.NetworkInterface as JNetworkInterface
import java.util.Collections

/**
 * Туннель: VpnService + libbox (daemon-модель). Реализует PlatformInterface
 * (OpenTun отдаёт fd VpnService, autoDetect=protect, монитор сети, перечень
 * интерфейсов) и CommandServerHandler. CommandServer владеет жизненным циклом box.
 */
class NinetyVpnService : VpnService(), PlatformInterface, CommandServerHandler {

    private var commandServer: CommandServer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var monitorListener: InterfaceUpdateListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val cm by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    // ── lifecycle ──────────────────────────────────────────────
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTunnel(null); return START_NOT_STICKY }
            // смена ноды/режима на лету: пересобрать конфиг под новый Store.activeId.
            // Если туннель не поднят — игнор (UI зовёт только при активном).
            ACTION_RELOAD -> { if (commandServer != null) doReload() else stopSelf(); return START_STICKY }
            else -> startTunnel()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel(null)
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel("VPN отозван системой")
        super.onRevoke()
    }

    private fun startTunnel() {
        if (commandServer != null) return
        VpnController.markConnecting()
        try {
            ensureChannel()
            startForeground(NOTIF_ID, notification("Подключение…"))
        } catch (e: Throwable) {
            Diag.writeCrash(this, "startForeground", e)
            stopTunnel("Не удалось запустить службу: ${e.message}")
            return
        }
        Thread({
            try {
                try { Libbox.redirectStderr(Diag.stderrFile(this).absolutePath) } catch (_: Throwable) {}
                Diag.startRunLog(this)
                val supported = Store.supportedActiveNodes()
                require(supported.isNotEmpty()) { "Нет поддерживаемых узлов" }
                val config = ConfigBuilder.build(supported, Store.activeId, Diag.runLogPath(this))

                val work = File(filesDir, "work").apply { mkdirs() }
                val opts = SetupOptions()
                opts.setBasePath(filesDir.absolutePath)
                opts.setWorkingPath(work.absolutePath)
                opts.setTempPath(cacheDir.absolutePath)
                opts.setCommandServerListenPort(0)
                opts.setCommandServerSecret("")
                opts.setLogMaxLines(300L)
                opts.setFixAndroidStack(false)
                opts.setDebug(false)
                Libbox.setup(opts)
                Libbox.checkConfig(config)

                val server = Libbox.newCommandServer(this, this)
                server.start()
                commandServer = server
                // ВАЖНО: options != null — движок разыменовывает его (command_server.go:173),
                // null → SIGSEGV. Пустой OverrideOptions (nil-итераторы безопасны).
                server.startOrReloadService(config, OverrideOptions())

                val name = Store.activeNodeLabel() ?: supported.firstOrNull()?.name
                VpnController.markConnected(name)
                updateNotification("Защищено${name?.let { " · $it" } ?: ""}")
            } catch (e: Throwable) {
                Diag.writeCrash(this, "startTunnel", e)
                stopTunnel(e.message ?: "Ошибка запуска туннеля")
            }
        }, "ninety-vpn-start").start()
    }

    private fun stopTunnel(error: String?) {
        try { commandServer?.closeService() } catch (_: Throwable) {}
        try { commandServer?.close() } catch (_: Throwable) {}
        commandServer = null
        networkCallback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Throwable) {} }
        networkCallback = null
        monitorListener = null
        try { pfd?.close() } catch (_: Throwable) {}
        pfd = null
        Diag.stopRunLog()
        stopForeground(STOP_FOREGROUND_REMOVE)
        VpnController.markIdle(error)
        stopSelf()
    }

    // ── PlatformInterface ──────────────────────────────────────
    override fun openTun(options: TunOptions): Int {
        val builder = Builder()
        drainRoutes(options.getInet4Address()) { builder.addAddress(it.address(), it.prefix()) }
        drainRoutes(options.getInet6Address()) { builder.addAddress(it.address(), it.prefix()) }
        builder.setMtu(options.getMTU())

        var v4routes = 0
        drainRoutes(options.getInet4RouteAddress()) { builder.addRoute(it.address(), it.prefix()); v4routes++ }
        if (v4routes == 0) builder.addRoute("0.0.0.0", 0)
        // v6-маршрут добавляем ТОЛЬКО если движок реально его дал. Безусловный ::/0
        // заворачивал v6 в туннель без v6-outbound → v6-dial висел ~30с до фоллбэка на v4.
        drainRoutes(options.getInet6RouteAddress()) { builder.addRoute(it.address(), it.prefix()) }

        try { builder.addDnsServer(options.getDNSServerAddress().getValue()) } catch (_: Throwable) {}

        drainStrings(options.getIncludePackage()) { try { builder.addAllowedApplication(it) } catch (_: Throwable) {} }
        drainStrings(options.getExcludePackage()) { try { builder.addDisallowedApplication(it) } catch (_: Throwable) {} }
        // собственный трафик мимо туннеля — чтобы подписки/OTA не петляли.
        try { builder.addDisallowedApplication(packageName) } catch (_: Throwable) {}

        builder.setSession("Ninety")
        // при reload openTun зовётся повторно — закрываем прежний fd, чтобы не текли.
        try { pfd?.close() } catch (_: Throwable) {}
        val p = builder.establish() ?: throw IllegalStateException("establish() вернул null (нет согласия VPN)")
        pfd = p
        return p.fd
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!protect(fd)) throw IllegalStateException("protect($fd) failed")
    }

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int,
        destinationAddress: String, destinationPort: Int,
    ): ConnectionOwner = throw UnsupportedOperationException("findConnectionOwner не поддержан")

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        monitorListener = listener
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = pushDefault(network)
            override fun onCapabilitiesChanged(network: Network, caps: android.net.NetworkCapabilities) = pushDefault(network)
        }
        networkCallback = cb
        try { cm.registerDefaultNetworkCallback(cb) } catch (_: Throwable) {}
        cm.activeNetwork?.let { pushDefault(it) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkCallback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Throwable) {} }
        networkCallback = null
        monitorListener = null
    }

    private fun pushDefault(network: Network) {
        val name = try { cm.getLinkProperties(network)?.interfaceName } catch (_: Throwable) { null } ?: return
        val index = try { JNetworkInterface.getByName(name)?.index ?: -1 } catch (_: Throwable) { -1 }
        try { monitorListener?.updateDefaultInterface(name, index, false, false) } catch (_: Throwable) {}
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val out = ArrayList<LbNetworkInterface>()
        try {
            for (ni in Collections.list(JNetworkInterface.getNetworkInterfaces())) {
                val item = LbNetworkInterface()
                item.setName(ni.name)
                item.setIndex(ni.index)
                item.setMTU(try { ni.mtu } catch (_: Throwable) { 0 })
                val addrs = ArrayList<String>()
                for (ia in ni.interfaceAddresses) {
                    // IPv6 link-local приходит с zone-суффиксом (fe80::..%dummy0);
                    // netip.MustParsePrefix в движке паникует на zone в префиксе — срезаем.
                    val ip = (ia.address?.hostAddress ?: continue).substringBefore('%')
                    addrs.add("$ip/${ia.networkPrefixLength.toInt()}")
                }
                item.setAddresses(FixedStringIterator(addrs))
                var fl = 0
                if (ni.isUp) fl = fl or 0x1 or 0x40
                if (ni.isLoopback) fl = fl or 0x8
                if (ni.isPointToPoint) fl = fl or 0x10
                if (ni.supportsMulticast()) fl = fl or 0x1000
                if (!ni.isLoopback && !ni.isPointToPoint) fl = fl or 0x2
                item.setFlags(fl)
                item.setType(Libbox.InterfaceTypeOther)
                item.setDNSServer(FixedStringIterator(emptyList()))
                item.setMetered(false)
                out.add(item)
            }
        } catch (_: Throwable) {}
        return FixedNetworkInterfaceIterator(out)
    }

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun systemCertificates(): StringIterator = FixedStringIterator(emptyList())
    override fun clearDNSCache() {}
    override fun sendNotification(notification: LibboxNotification?) {}

    // ── CommandServerHandler ───────────────────────────────────
    override fun serviceStop() { stopTunnel(null) }

    override fun serviceReload() { doReload() }

    /** Пересборка конфига под текущий Store.activeId без полного рестарта службы. */
    private fun doReload() {
        val server = commandServer ?: return
        Thread({
            try {
                val supported = Store.supportedActiveNodes()
                if (supported.isEmpty()) return@Thread
                server.startOrReloadService(ConfigBuilder.build(supported, Store.activeId, Diag.runLogPath(this)), OverrideOptions())
                val name = Store.activeNodeLabel()
                VpnController.markConnected(name)
                updateNotification("Защищено${name?.let { " · $it" } ?: ""}")
            } catch (e: Throwable) {
                stopTunnel(e.message ?: "Ошибка перезагрузки")
            }
        }, "ninety-vpn-reload").start()
    }

    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply { setAvailable(false); setEnabled(false) }

    override fun setSystemProxyEnabled(enabled: Boolean) {}
    // Единственный канал логов ядра sing-box (level/route/dns/outbound) — раньше
    // выбрасывали → «логов нет». Пишем в файл, видно в Настройках/«Скопировать».
    override fun writeDebugMessage(message: String?) { Diag.appendRunLog(message) }

    // ── helpers ────────────────────────────────────────────────
    private inline fun drainRoutes(it: RoutePrefixIterator?, f: (io.nekohasekai.libbox.RoutePrefix) -> Unit) {
        if (it == null) return
        while (it.hasNext()) f(it.next())
    }

    private inline fun drainStrings(it: StringIterator?, f: (String) -> Unit) {
        if (it == null) return
        while (it.hasNext()) f(it.next())
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Туннель", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Ninety")
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .addAction(
            0, "Отключить",
            PendingIntent.getService(
                this, 1, Intent(this, NinetyVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    companion object {
        const val ACTION_START = "pw.x4.ninety.action.START"
        const val ACTION_STOP = "pw.x4.ninety.action.STOP"
        const val ACTION_RELOAD = "pw.x4.ninety.action.RELOAD"
        private const val CHANNEL = "ninety_vpn"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, NinetyVpnService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NinetyVpnService::class.java).setAction(ACTION_STOP)
            )
        }

        /** Перестроить туннель под новый активный узел (только если он поднят). */
        fun reload(context: Context) {
            context.startService(
                Intent(context, NinetyVpnService::class.java).setAction(ACTION_RELOAD)
            )
        }
    }
}
