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
import pw.x4.ninety.core.runtime.VpnRuntimeCommand
import pw.x4.ninety.core.runtime.VpnRuntimeTicket
import pw.x4.ninety.data.Diag
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.Store
import java.io.File
import java.net.NetworkInterface as JNetworkInterface
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * VpnService + libbox runtime.
 *
 * Start, reload and stop are executed by one FIFO executor. Each command also owns a generation
 * ticket created before it enters the queue. A later command invalidates long-running older work,
 * so an old start/reload can never publish Connected or stop a newer tunnel.
 */
class NinetyVpnService : VpnService(), PlatformInterface, CommandServerHandler {
    private val resourceLock = Any()
    private val runtimeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ninety-vpn-runtime")
    }

    @Volatile
    private var destroyed = false

    @Volatile
    private var commandServer: CommandServer? = null

    @Volatile
    private var inFlightServer: CommandServer? = null

    @Volatile
    private var pfd: ParcelFileDescriptor? = null

    @Volatile
    private var monitorListener: InterfaceUpdateListener? = null

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val cm by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }

    // ── lifecycle ──────────────────────────────────────────────
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                updateNotificationSafely("Отключение…")
                enqueue(VpnController.requestStop(null))
                return START_NOT_STICKY
            }

            ACTION_RELOAD -> {
                VpnController.requestReload()?.let {
                    updateNotificationSafely("Применение настроек…")
                    enqueue(it)
                }
                return START_STICKY
            }

            else -> {
                val ticket = VpnController.requestStart() ?: return START_STICKY
                if (!enterForeground(ticket)) return START_NOT_STICKY
                enqueue(ticket)
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        runtimeExecutor.shutdownNow()
        closeEngineResources()
        Diag.stopRunLog()
        if (VpnController.snapshot.value.state != ConnState.Idle) {
            val ticket = VpnController.requestStop(null)
            VpnController.completeStopped(ticket)
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    override fun onRevoke() {
        enqueue(VpnController.requestStop("VPN отозван системой"))
        super.onRevoke()
    }

    private fun enterForeground(ticket: VpnRuntimeTicket): Boolean = try {
        ensureChannel()
        startForeground(NOTIF_ID, notification("Подключение…"))
        true
    } catch (error: Throwable) {
        val message = "Не удалось запустить службу: ${error.message}"
        Diag.writeCrash(this, "startForeground", error)
        if (VpnController.fail(ticket, message)) stopSelf()
        false
    }

    private fun enqueue(ticket: VpnRuntimeTicket) {
        if (destroyed) return
        try {
            runtimeExecutor.execute {
                when (ticket.command) {
                    VpnRuntimeCommand.Start -> executeStart(ticket)
                    VpnRuntimeCommand.Reload -> executeReload(ticket)
                    is VpnRuntimeCommand.Stop -> executeStop(ticket)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Service destruction already invalidated the ticket and synchronously closed resources.
        }
    }

    private fun executeStart(ticket: VpnRuntimeTicket) {
        if (!VpnController.isCurrent(ticket)) return
        var localServer: CommandServer? = null
        try {
            runCatching { Libbox.redirectStderr(Diag.stderrFile(this).absolutePath) }
            Diag.startRunLog(this)

            val supported = Store.supportedActiveNodes()
            require(supported.isNotEmpty()) { "Нет поддерживаемых узлов" }
            Options.load(this)
            val config = ConfigBuilder.build(supported, Store.activeId, Diag.runLogPath(this))
            if (!VpnController.isCurrent(ticket)) return

            val work = File(filesDir, "work").apply { mkdirs() }
            val options = SetupOptions().apply {
                setBasePath(filesDir.absolutePath)
                setWorkingPath(work.absolutePath)
                setTempPath(cacheDir.absolutePath)
                setCommandServerListenPort(0)
                setCommandServerSecret("")
                setLogMaxLines(300L)
                setFixAndroidStack(false)
                setDebug(false)
            }
            Libbox.setup(options)
            Libbox.checkConfig(config)
            if (!VpnController.isCurrent(ticket)) return

            val server = Libbox.newCommandServer(this, this)
            localServer = server
            synchronized(resourceLock) {
                inFlightServer = server
                commandServer = server
            }
            server.start()
            if (!VpnController.isCurrent(ticket)) return

            // options must be non-null: libbox dereferences it in command_server.go.
            server.startOrReloadService(config, OverrideOptions())
            if (!VpnController.isCurrent(ticket)) return

            synchronized(resourceLock) { inFlightServer = null }
            val name = Store.activeNodeLabel() ?: supported.firstOrNull()?.name
            if (VpnController.completeConnected(ticket, name)) {
                updateNotificationSafely("Защищено${name?.let { " · $it" } ?: ""}")
                localServer = null // ownership moved to commandServer
            }
        } catch (error: Throwable) {
            Diag.writeCrash(this, "startTunnel", error)
            val message = error.message ?: "Ошибка запуска туннеля"
            if (VpnController.fail(ticket, message)) finishFailedRuntime()
        } finally {
            val unfinished = localServer
            unfinished?.let(::closeSpecificServer)
            synchronized(resourceLock) {
                if (inFlightServer === unfinished) inFlightServer = null
                if (commandServer === unfinished) commandServer = null
            }
            if (!VpnController.isCurrent(ticket)) {
                closeEngineResources()
                Diag.stopRunLog()
            }
        }
    }

    private fun executeReload(ticket: VpnRuntimeTicket) {
        if (!VpnController.isCurrent(ticket)) return
        val server = commandServer
        if (server == null) {
            if (!enterForeground(ticket)) return
            executeStart(ticket)
            return
        }

        try {
            val supported = Store.supportedActiveNodes()
            require(supported.isNotEmpty()) { "Нет поддерживаемых узлов" }
            Options.load(this)
            val config = ConfigBuilder.build(supported, Store.activeId, Diag.runLogPath(this))
            if (!VpnController.isCurrent(ticket)) return

            server.startOrReloadService(config, OverrideOptions())
            if (!VpnController.isCurrent(ticket)) return

            val name = Store.activeNodeLabel() ?: supported.firstOrNull()?.name
            if (VpnController.completeConnected(ticket, name)) {
                updateNotificationSafely("Защищено${name?.let { " · $it" } ?: ""}")
            }
        } catch (error: Throwable) {
            Diag.writeCrash(this, "reloadTunnel", error)
            val message = error.message ?: "Ошибка перезагрузки"
            if (VpnController.fail(ticket, message)) finishFailedRuntime()
        }
    }

    private fun executeStop(ticket: VpnRuntimeTicket) {
        closeEngineResources()
        Diag.stopRunLog()
        if (VpnController.completeStopped(ticket)) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    private fun finishFailedRuntime() {
        closeEngineResources()
        Diag.stopRunLog()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun closeEngineResources() {
        val resources = synchronized(resourceLock) {
            val captured = EngineResources(
                servers = listOfNotNull(inFlightServer, commandServer).distinct(),
                callback = networkCallback,
                descriptor = pfd,
            )
            inFlightServer = null
            commandServer = null
            networkCallback = null
            monitorListener = null
            pfd = null
            captured
        }
        resources.servers.forEach(::closeSpecificServer)
        resources.callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        runCatching { resources.descriptor?.close() }
    }

    private fun closeSpecificServer(server: CommandServer) {
        runCatching { server.closeService() }
        runCatching { server.close() }
    }

    private data class EngineResources(
        val servers: List<CommandServer>,
        val callback: ConnectivityManager.NetworkCallback?,
        val descriptor: ParcelFileDescriptor?,
    )

    // ── PlatformInterface ──────────────────────────────────────
    override fun openTun(options: TunOptions): Int {
        val builder = Builder()
        drainRoutes(options.getInet4Address()) { builder.addAddress(it.address(), it.prefix()) }
        drainRoutes(options.getInet6Address()) { builder.addAddress(it.address(), it.prefix()) }
        builder.setMtu(options.getMTU())

        var v4routes = 0
        drainRoutes(options.getInet4RouteAddress()) {
            builder.addRoute(it.address(), it.prefix())
            v4routes++
        }
        if (v4routes == 0) builder.addRoute("0.0.0.0", 0)
        // Add IPv6 only when libbox supplies it; unconditional ::/0 caused long v6 fallbacks.
        drainRoutes(options.getInet6RouteAddress()) { builder.addRoute(it.address(), it.prefix()) }

        runCatching { builder.addDnsServer(options.getDNSServerAddress().getValue()) }
        drainStrings(options.getIncludePackage()) { runCatching { builder.addAllowedApplication(it) } }
        drainStrings(options.getExcludePackage()) { runCatching { builder.addDisallowedApplication(it) } }
        runCatching { builder.addDisallowedApplication(packageName) }

        builder.setSession("Ninety")
        val descriptor = builder.establish()
            ?: throw IllegalStateException("establish() вернул null (нет согласия VPN)")
        val previous = synchronized(resourceLock) {
            val old = pfd
            pfd = descriptor
            old
        }
        runCatching { previous?.close() }
        return descriptor.fd
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!protect(fd)) throw IllegalStateException("protect($fd) failed")
    }

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner = throw UnsupportedOperationException("findConnectionOwner не поддержан")

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        monitorListener = listener
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = pushDefault(network)
            override fun onCapabilitiesChanged(
                network: Network,
                caps: android.net.NetworkCapabilities,
            ) = pushDefault(network)
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        cm.activeNetwork?.let(::pushDefault)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val callback = synchronized(resourceLock) {
            val current = networkCallback
            networkCallback = null
            monitorListener = null
            current
        }
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
    }

    private fun pushDefault(network: Network) {
        val name = runCatching { cm.getLinkProperties(network)?.interfaceName }.getOrNull() ?: return
        val index = runCatching { JNetworkInterface.getByName(name)?.index ?: -1 }.getOrDefault(-1)
        runCatching { monitorListener?.updateDefaultInterface(name, index, false, false) }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val output = ArrayList<LbNetworkInterface>()
        try {
            for (networkInterface in Collections.list(JNetworkInterface.getNetworkInterfaces())) {
                val item = LbNetworkInterface()
                item.setName(networkInterface.name)
                item.setIndex(networkInterface.index)
                item.setMTU(runCatching { networkInterface.mtu }.getOrDefault(0))
                val addresses = ArrayList<String>()
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val ip = (interfaceAddress.address?.hostAddress ?: continue).substringBefore('%')
                    addresses.add("$ip/${interfaceAddress.networkPrefixLength.toInt()}")
                }
                item.setAddresses(FixedStringIterator(addresses))
                var flags = 0
                if (networkInterface.isUp) flags = flags or 0x1 or 0x40
                if (networkInterface.isLoopback) flags = flags or 0x8
                if (networkInterface.isPointToPoint) flags = flags or 0x10
                if (networkInterface.supportsMulticast()) flags = flags or 0x1000
                if (!networkInterface.isLoopback && !networkInterface.isPointToPoint) flags = flags or 0x2
                item.setFlags(flags)
                item.setType(Libbox.InterfaceTypeOther)
                item.setDNSServer(FixedStringIterator(emptyList()))
                item.setMetered(false)
                output.add(item)
            }
        } catch (_: Throwable) {
            // libbox treats an empty iterator as no additional platform interfaces.
        }
        return FixedNetworkInterfaceIterator(output)
    }

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun systemCertificates(): StringIterator = FixedStringIterator(emptyList())
    override fun clearDNSCache() {}
    override fun sendNotification(notification: LibboxNotification?) {}

    // ── CommandServerHandler ───────────────────────────────────
    override fun serviceStop() {
        if (VpnController.snapshot.value.state == ConnState.Connected) {
            enqueue(VpnController.requestStop(null))
        }
    }

    override fun serviceReload() {
        if (VpnController.snapshot.value.state == ConnState.Connected) {
            VpnController.requestReload()?.let(::enqueue)
        }
    }

    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply {
            setAvailable(false)
            setEnabled(false)
        }

    override fun setSystemProxyEnabled(enabled: Boolean) {}

    override fun writeDebugMessage(message: String?) {
        Diag.appendRunLog(message)
    }

    // ── helpers ────────────────────────────────────────────────
    private inline fun drainRoutes(
        iterator: RoutePrefixIterator?,
        action: (io.nekohasekai.libbox.RoutePrefix) -> Unit,
    ) {
        if (iterator == null) return
        while (iterator.hasNext()) action(iterator.next())
    }

    private inline fun drainStrings(iterator: StringIterator?, action: (String) -> Unit) {
        if (iterator == null) return
        while (iterator.hasNext()) action(iterator.next())
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Туннель", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_mono)
        .setContentTitle("Ninety")
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0,
            "Отключить",
            PendingIntent.getService(
                this,
                1,
                Intent(this, NinetyVpnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun updateNotificationSafely(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
        }
    }

    companion object {
        const val ACTION_START = "pw.x4.ninety.action.START"
        const val ACTION_STOP = "pw.x4.ninety.action.STOP"
        const val ACTION_RELOAD = "pw.x4.ninety.action.RELOAD"
        private const val CHANNEL = "ninety_vpn"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NinetyVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NinetyVpnService::class.java).setAction(ACTION_STOP),
            )
        }

        fun reload(context: Context) {
            context.startService(
                Intent(context, NinetyVpnService::class.java).setAction(ACTION_RELOAD),
            )
        }
    }
}
