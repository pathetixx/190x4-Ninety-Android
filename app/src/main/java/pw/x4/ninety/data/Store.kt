package pw.x4.ninety.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.data.persistence.StorageSnapshot
import java.io.File

/**
 * Compatibility store for existing Compose and VPN callers.
 *
 * The in-memory API stays synchronous, but startup data comes from verified Room state. Mutations
 * are dual-written in crash-safe order: legacy JSON rollback journal first, then Room transaction.
 */
object Store {
    private lateinit var nodesFile: File
    private lateinit var profilesFile: File
    private lateinit var prefs: Prefs

    val nodes = mutableStateListOf<Node>()
    val profiles = mutableStateListOf<Profile>()

    var activeId by mutableStateOf<String?>(null)
        private set
    var activeProfileId by mutableStateOf<String?>(null)
        private set

    fun init(context: Context, snapshot: StorageSnapshot) {
        if (::nodesFile.isInitialized) return
        val app = context.applicationContext
        nodesFile = File(app.filesDir, "nodes.json")
        profilesFile = File(app.filesDir, "profiles.json")
        prefs = Prefs.get(app)

        nodes.clear()
        nodes.addAll(snapshot.nodes.map { it.toLegacyNode() })
        profiles.clear()
        profiles.addAll(snapshot.profiles.map { it.toLegacyProfile() })
        activeId = snapshot.preferences.activeNodeId
        activeProfileId = snapshot.preferences.activeProfileId ?: profiles.firstOrNull()?.id
    }

    // legacy: оставлено для миграции/rollback, UI больше не использует
    var subscriptionUrl: String?
        get() = prefs.subscriptionUrl
        set(value) { prefs.subscriptionUrl = value }

    /** Sentinel для активного id: автовыбор быстрейшего узла (urltest). */
    const val AUTO_ID = ProxySelection.AUTO_ID

    /** Типизированный выбор: Auto больше не маскируется под отсутствующую ноду. */
    val selection: ProxySelection?
        get() = ProxySelection.fromPersisted(activeId)

    // ── Выборки ──
    fun activeNode(): Node? = nodes.firstOrNull { it.id == activeId }
    val isAutoActive: Boolean get() = selection == ProxySelection.Auto

    /** Auto валиден, если в активном профиле есть хотя бы одна поддерживаемая нода. */
    fun hasRunnableSelection(): Boolean = when (selection) {
        ProxySelection.Auto -> supportedActiveNodes().isNotEmpty()
        is ProxySelection.Node -> activeNode()?.supported == true
        null -> false
    }

    fun activeNodeLabel(): String? = when {
        isAutoActive -> "Авто"
        else -> activeNode()?.let { it.name.ifBlank { it.host } }
    }

    fun activeProfile(): Profile? = profiles.firstOrNull { it.id == activeProfileId }
    fun nodesOf(profileId: String?): List<Node> = nodes.filter { it.subId == profileId }
    fun activeProfileNodes(): List<Node> = nodesOf(activeProfileId)
    fun supportedActiveNodes(): List<Node> = activeProfileNodes().filter { it.supported }
    fun nodeCount(profileId: String): Int = nodes.count { it.subId == profileId }

    // ── Активный выбор ──
    fun setActive(id: String) {
        val next = requireNotNull(ProxySelection.fromPersisted(id)) {
            "proxy selection must not be blank"
        }
        activeId = next.persistedValue
        prefs.activeNodeId = next.persistedValue
    }

    fun setActiveProfile(id: String) {
        activeProfileId = id
        prefs.activeProfileId = id
        val first = nodesOf(id).firstOrNull()?.id
        activeId = first
        prefs.activeNodeId = first
    }

    // ── Профили ──

    /** Подписка: создать/заменить профиль и его ноды. url="" → импорт сырого контента. */
    fun addSubscriptionProfile(
        url: String,
        content: String,
        info: SubUserinfo,
        name: String? = null,
    ): Int {
        val parsed = LinkParser.parseSubscription(content)
        require(parsed.isNotEmpty()) { "Подписка пуста или не распознана" }
        val id = if (url.isBlank()) "sub:raw:" + content.trim().hashCode() else Profile.subId(url)
        nodes.removeAll { it.subId == id }
        val tagged = parsed.map { it.copy(fromSub = true, subId = id) }
        for (node in tagged) if (nodes.none { it.id == node.id }) nodes.add(node)
        upsertProfile(
            Profile(
                id = id,
                name = name ?: hostOf(url) ?: "Подписка",
                type = "sub",
                url = url,
                used = info.used,
                total = info.total,
                expire = info.expire,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        setActiveProfile(id)
        saveAll()
        return tagged.size
    }

    /** Одиночный конфиг: профиль из одной ноды. false если ссылка не распознана/дубль. */
    fun addSingleConfig(raw: String): Boolean {
        val node = LinkParser.parseLink(raw) ?: return false
        val id = "single:${node.id}"
        if (profiles.any { it.id == id }) return false
        nodes.add(node.copy(subId = id))
        upsertProfile(
            Profile(
                id = id,
                name = node.name.ifBlank { node.host },
                type = "single",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        if (activeProfileId == null) setActiveProfile(id)
        saveAll()
        return true
    }

    /** Refresh подписки: заменить ноды профиля, активный выбор сохранить если он валиден. */
    fun refreshProfileNodes(id: String, content: String, info: SubUserinfo): Int {
        val parsed = LinkParser.parseSubscription(content)
        require(parsed.isNotEmpty()) { "Подписка пуста или не распознана" }
        nodes.removeAll { it.subId == id }
        val tagged = parsed.map { it.copy(fromSub = true, subId = id) }
        for (node in tagged) if (nodes.none { it.id == node.id }) nodes.add(node)
        profiles.firstOrNull { it.id == id }?.let { profile ->
            upsertProfile(
                profile.copy(
                    used = info.used,
                    total = info.total,
                    expire = info.expire,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        if (activeProfileId == id) {
            val keepCurrent = when (val current = selection) {
                ProxySelection.Auto -> supportedActiveNodes().isNotEmpty()
                is ProxySelection.Node -> nodes.any { it.id == current.nodeId && it.subId == id }
                null -> false
            }
            if (!keepCurrent) {
                val first = nodesOf(id).firstOrNull()?.id
                activeId = first
                prefs.activeNodeId = first
            }
        }
        saveAll()
        return tagged.size
    }

    fun removeProfile(id: String) {
        profiles.removeAll { it.id == id }
        nodes.removeAll { it.subId == id }
        if (activeProfileId == id) {
            val next = profiles.firstOrNull()
            if (next != null) {
                setActiveProfile(next.id)
            } else {
                activeProfileId = null
                activeId = null
                prefs.activeProfileId = null
                prefs.activeNodeId = null
            }
        }
        saveAll()
    }

    private fun upsertProfile(profile: Profile) {
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) profiles[index] = profile else profiles.add(profile)
    }

    /** Reparse every raw link with the current parser after an app update. */
    fun reparseAllFromRaw(): Int {
        if (!::nodesFile.isInitialized || nodes.isEmpty()) return 0
        var healed = 0
        val rebuilt = nodes.map { old ->
            if (old.raw.isBlank()) return@map old
            val fresh = LinkParser.parseLink(old.raw) ?: return@map old
            val merged = fresh.copy(subId = old.subId, fromSub = old.fromSub)
            if (merged.toJson().toString() != old.toJson().toString()) healed++
            merged
        }
        if (healed > 0) {
            nodes.clear()
            nodes.addAll(rebuilt)
            saveAll()
        }
        return healed
    }

    fun clear() {
        nodes.clear()
        profiles.clear()
        activeId = null
        activeProfileId = null
        prefs.activeNodeId = null
        prefs.activeProfileId = null
        saveAll()
    }

    /** JSON first = rollback journal; Room second = encrypted source of truth. */
    private fun saveAll() {
        runCatching {
            nodesFile.writeText(JSONArray().apply { nodes.forEach { put(it.toJson()) } }.toString())
            profilesFile.writeText(JSONArray().apply { profiles.forEach { put(it.toJson()) } }.toString())
        }.getOrElse { throw IllegalStateException("failed to write legacy rollback journal", it) }

        PersistenceRuntime.persistGraph(nodes.toList(), profiles.toList())
    }

    private fun hostOf(url: String): String? = runCatching {
        if (url.isBlank()) null else java.net.URI(url).host?.removePrefix("www.")
    }.getOrNull()
}
