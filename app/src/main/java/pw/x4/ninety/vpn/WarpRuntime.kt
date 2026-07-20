package pw.x4.ninety.vpn

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wireguard.crypto.KeyPair
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import pw.x4.ninety.core.config.WarpConfig
import pw.x4.ninety.core.model.WarpMode
import pw.x4.ninety.core.model.WarpNoisePreset
import pw.x4.ninety.core.model.WarpRange
import pw.x4.ninety.core.model.WarpRegistration
import pw.x4.ninety.core.model.WarpRegistrationSanitizer
import pw.x4.ninety.core.model.WarpSettings
import pw.x4.ninety.data.Options
import pw.x4.ninety.data.persistence.WarpStore

object WarpRuntime {
    data class Snapshot(
        val registered: Boolean = false,
        val warpPlus: Boolean = false,
        val accountType: String = "",
        val registeredAt: String = "",
        val busy: Boolean = false,
        val error: String? = null,
    )

    var snapshot by mutableStateOf(Snapshot())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operation = Mutex()
    private lateinit var context: Context
    private lateinit var store: WarpStore
    @Volatile private var registration: WarpRegistration? = null

    fun initialize(context: Context) {
        if (::store.isInitialized) return
        this.context = context.applicationContext
        store = WarpStore.create(this.context)
        registration = store.read()
        publish()
    }

    fun configForBuild(options: Options.Data): WarpConfig? {
        val current = registration ?: return null
        return WarpConfig(
            settings = WarpSettings(
                enabled = options.warpEnabled,
                mode = WarpMode.fromWire(options.warpMode),
                endpoint = options.warpEndpoint,
                mtu = options.warpMtu,
                noisePreset = WarpNoisePreset.fromWire(options.warpNoisePreset),
                customCount = WarpRange(options.warpCountFrom, options.warpCountTo),
                customSize = WarpRange(options.warpSizeFrom, options.warpSizeTo),
                customDelay = WarpRange(options.warpDelayFrom, options.warpDelayTo),
            ),
            registration = current,
        )
    }

    fun register(license: String? = null) {
        if (!::store.isInitialized || snapshot.busy) return
        snapshot = snapshot.copy(busy = true, error = null)
        scope.launch {
            operation.withLock {
                runCatching {
                    val normalizedLicense = WarpRegistrationSanitizer.normalizeLicense(license)
                    val old = registration
                    val pair = KeyPair()
                    val response = WarpApi.register(pair.privateKey.toBase64(), pair.publicKey.toBase64())
                    val activated = if (normalizedLicense == null) response else WarpApi.activate(response, normalizedLicense)
                    val clean = requireNotNull(WarpRegistrationSanitizer.sanitize(activated).registration)
                    store.write(clean)
                    registration = clean
                    publish(busy = false)
                    old?.takeIf { it.registrationId.isNotBlank() && it.registrationId != clean.registrationId }
                        ?.let { runCatching { WarpApi.delete(it.registrationId, it.accessToken) } }
                    reloadIfActive()
                }.onFailure { publish(busy = false, error = it.message ?: "WARP registration failed") }
            }
        }
    }

    fun reset() {
        if (!::store.isInitialized || snapshot.busy) return
        snapshot = snapshot.copy(busy = true, error = null)
        scope.launch {
            operation.withLock {
                val old = registration
                old?.takeIf { it.registrationId.isNotBlank() }
                    ?.let { runCatching { WarpApi.delete(it.registrationId, it.accessToken) } }
                store.clear()
                registration = null
                Options.update(context) { it.copy(warpEnabled = false) }
                publish(busy = false)
                reloadIfActive()
            }
        }
    }

    fun applySettings() = reloadIfActive()

    private fun reloadIfActive() {
        if (VpnController.isActive) NinetyVpnService.reload(context)
    }

    private fun publish(busy: Boolean = false, error: String? = null) {
        val current = registration
        snapshot = Snapshot(
            registered = current != null,
            warpPlus = current?.warpPlus == true,
            accountType = current?.accountType.orEmpty(),
            registeredAt = current?.registeredAt.orEmpty(),
            busy = busy,
            error = error,
        )
    }
}

private object WarpApi {
    private const val BASE = "https://api.cloudflareclient.com/v0a2158"
    private const val CLIENT_VERSION = "a-6.10-2158"
    private const val MAX_BODY = 1024 * 1024
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()

    fun register(privateKey: String, publicKey: String): WarpRegistration {
        val body = JSONObject()
            .put("install_id", "ninety-${UUID.randomUUID()}")
            .put("fcm_token", "")
            .put("tos", Instant.now().toString())
            .put("key", publicKey)
            .put("type", "Android")
            .put("model", "Ninety/190x4")
            .put("locale", "en_US")
            .put("warp_enabled", true)
        val root = execute(Request.Builder().url("$BASE/reg").post(body.toString().toRequestBody(jsonType)).headers())
        val account = root.getJSONObject("account")
        val config = root.getJSONObject("config")
        val peer = config.getJSONArray("peers").getJSONObject(0)
        val addresses = config.getJSONObject("interface").getJSONObject("addresses")
        return WarpRegistration(
            registrationId = root.getString("id"),
            accountId = account.optString("id"),
            accessToken = root.getString("token"),
            privateKey = privateKey,
            peerPublicKey = peer.getString("public_key"),
            localIpv4 = addresses.optString("v4"),
            localIpv6 = addresses.optString("v6"),
            clientId = config.getString("client_id"),
            warpPlus = account.optBoolean("warp_plus"),
            accountType = account.optString("account_type", "free"),
            registeredAt = Instant.now().toString(),
        )
    }

    fun activate(source: WarpRegistration, license: String): WarpRegistration {
        val body = JSONObject().put("license", license)
        val root = execute(
            Request.Builder().url("$BASE/reg/${source.registrationId}/account")
                .patch(body.toString().toRequestBody(jsonType)).headers(source.accessToken),
        )
        return source.copy(
            license = root.optString("license").ifBlank { license },
            warpPlus = root.optBoolean("warp_plus", source.warpPlus),
            accountType = root.optString("account_type").ifBlank { source.accountType },
        )
    }

    fun delete(id: String, token: String) {
        val request = Request.Builder().url("$BASE/reg/$id").delete().headers(token).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) error("Cloudflare delete ${response.code}")
        }
    }

    private fun Request.Builder.headers(token: String? = null): Request {
        header("User-Agent", "okhttp/3.12.1")
        header("CF-Client-Version", CLIENT_VERSION)
        token?.let { header("Authorization", "Bearer $it") }
        return build()
    }

    private fun execute(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val body = response.body ?: error("Cloudflare returned an empty response")
        if (body.contentLength() > MAX_BODY) error("Cloudflare response is too large")
        val text = body.charStream().readText().take(MAX_BODY + 1)
        if (text.length > MAX_BODY) error("Cloudflare response is too large")
        if (!response.isSuccessful) error("Cloudflare ${response.code}: ${text.take(240)}")
        JSONObject(text)
    }
}
