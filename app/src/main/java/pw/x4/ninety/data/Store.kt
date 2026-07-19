package pw.x4.ninety.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import pw.x4.ninety.core.model.ProxySelection
import java.io.File

/**
 * Хранилище профилей + нод (как desktop-Ninety). Профиль = подписка (много нод)
 * или одиночный конфиг (одна нода); каждая нода привязана к профилю через subId.
 * Активный профиль определяет, какие ноды видны в «Ноды» и в плитке Главной;
 * активная нода (activeId) — конкретный сервер для подключения (из активного
 * профиля). Persist: profiles.json + nodes.json, активные id — в Prefs.
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

    fun init(context: Context) {
        if (::nodesFile.isInitialized) return
        nodesFile = File(context.filesDir, "nodes.json")
        profilesFile = File(context.filesDir, "profiles.json")
        prefs = Prefs.get(context)
        load()
        activeId = prefs.activeNodeId
        activeProfileId = prefs.activeProfileId ?: profiles.firstOrNull()?.id
    }

    // legacy: оставлено для миграции, UI больше не использует
    var subscriptionUrl: String?
        get() = prefs.subscriptionUrl
        set(v) { prefs.subscriptionUrl = v }

    /** Sentinel для активного id: автовыбор быстрейшего узла (urltest). */
    const val AUTO_ID = ProxySelection.AUTO_ID

    /** Типизированный выбор: Auto больше не маскируется под отсутствующую ноду. */
    val selection: ProxySelection?
        get() = ProxySelection.fromPersisted(activeId)

    // ── Выборки ──
    fun activeNode(): Node? = nodes.firstOrNull { it.id == activeId }
    val isAutoActive: Boolean get() = selection == ProxySelection.Auto

    /**
     * Есть ли валидный выбор для запуска VPN.
     * Auto валиден, если в активном профиле есть хотя бы одна поддерживаемая нода.
     */
    fun hasRunnableSelection(): Boolean = when (selection) {
        ProxySelection.Auto -> supportedActiveNodes().isNotEmpty()
        is ProxySelection.Node -> activeNode()?.supported == true
        null -> false
    }

    /** Подпись активного выбора для уведомления/UI: «Авто» либо имя узла. */
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
        // Валидация вынесена в core:model. Значение сохраняем строкой для совместимости
        // с существующими установками и Prefs.
        ProxySelection.fromPersisted(id)
        activeId = id
        prefs.activeNodeId = id
    }

    fun setActiveProfile(id: String) {
        activeProfileId = id
        prefs.activeProfileId = id
        // активная нода переезжает на первую ноду нового профиля
        val first = nodesOf(id).firstOrNull()?.id
        activeId = first
        prefs.activeNodeId = first
    }

    // ── Профили ──

    /** Подписка: создать/заменить профиль и его ноды. url="" → импорт сырого контента. */
    fun addSubscriptionProfile(url: String, content: String, info: SubUserinfo, name: String? = null): Int {
        val parsed = LinkParser.parseSubscription(content)
        require(parsed.isNotEmpty()) { "Подписка пуста или не распознана" }
        val id = if (url.isBlank()) "sub:raw:" + content.trim().hashCode() else Profile.subId(url)
        nodes.removeAll { it.subId == id }
        val tagged = parsed.map { it.copy(fromSub = true, subId = id) }
        for (n in tagged) if (nodes.none { it.id == n.id }) nodes.add(n)
        upsertProfile(
            Profile(id, name ?: hostOf(url) ?: "Подписка", "sub", url,
                info.used, info.total, info.expire, System.currentTimeMillis())
        )
        setActiveProfile(id)
        saveAll()
        return tagged.size
    }

    /** Одиночный конфиг: профиль из одной ноды. false если ссылка не распознана/дубль. */
    fun addSingleConfig(raw: String): Boolean {
        val node = LinkParser.parseLink(raw) ?: return false
        val id = "single:" + node.id
        if (profiles.any { it.id == id }) return false
        nodes.add(node.copy(subId = id))
        upsertProfile(Profile(id, node.name.ifBlank { node.host }, "single", "",
            updatedAt = System.currentTimeMillis()))
        if (activeProfileId == null) setActiveProfile(id)
        saveAll()
        return true
    }

    /** Refresh подписки: заменить ноды профиля, активную сохранить если осталась. */
    fun refreshProfileNodes(id: String, content: String, info: SubUserinfo): Int {
        val parsed = LinkParser.parseSubscription(content)
        require(parsed.isNotEmpty()) { "Подписка пуста или не распознана" }
        nodes.removeAll { it.subId == id }
        val tagged = parsed.map { it.copy(fromSub = true, subId = id) }
        for (n in tagged) if (nodes.none { it.id == n.id }) nodes.add(n)
        profiles.firstOrNull { it.id == id }?.let { p ->
            upsertProfile(p.copy(used = info.used, total = info.total, expire = info.expire,
                updatedAt = System.currentTimeMillis()))
        }
        if (activeProfileId == id && (activeId == null || nodes.none { it.id == activeId })) {
            val first = nodesOf(id).firstOrNull()?.id
            activeId = first; prefs.activeNodeId = first
        }
        saveAll()
        return tagged.size
    }

    fun removeProfile(id: String) {
        profiles.removeAll { it.id == id }
        nodes.removeAll { it.subId == id }
        if (activeProfileId == id) {
            val next = profiles.firstOrNull()
            if (next != null) setActiveProfile(next.id)
            else { activeProfileId = null; prefs.activeProfileId = null; activeId = null; prefs.activeNodeId = null }
        }
        saveAll()
    }

    private fun upsertProfile(p: Profile) {
        val i = profiles.indexOfFirst { it.id == p.id }
        if (i >= 0) profiles[i] = p else profiles.add(p)
    }

    /**
     * Самолечение после апдейта: перечитать КАЖДУЮ ноду из её исходной ссылки (`raw`)
     * текущим [LinkParser]. Подхватывает поля, которые добавили новые версии парсера
     * (например xhttp `extra`: downloadSettings/xmux/паддинги с v0.1.15) — раньше для
     * этого приходилось вручную передобавлять подписку. Локально, без сети.
     *
     * id ноды стабилен (хэш не зависит от `type`/`extra`) → активный выбор НЕ слетает;
     * `subId`/`fromSub` сохраняем (привязка к профилю). Ноды без `raw` или с битой
     * ссылкой остаются как есть. Возвращает число фактически изменившихся нод.
     */
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
        if (healed > 0) { nodes.clear(); nodes.addAll(rebuilt); saveAll() }
        return healed
    }

    fun clear() {
        nodes.clear(); profiles.clear()
        activeId = null; activeProfileId = null
        prefs.activeNodeId = null; prefs.activeProfileId = null
        saveAll()
    }

    // ── Персист + миграция ──
    private fun load() {
        val rawNodes = readNodes()
        if (profilesFile.exists()) {
            try {
                val arr = JSONArray(profilesFile.readText())
                profiles.clear()
                for (i in 0 until arr.length()) profiles.add(Profile.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {}
            nodes.clear(); nodes.addAll(rawNodes)
            return
        }
        // миграция из v0.1.x (плоский список нод + один subscriptionUrl)
        migrate(rawNodes)
    }

    private fun readNodes(): List<Node> {
        if (!nodesFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(nodesFile.readText())
            (0 until arr.length()).map { Node.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    private fun migrate(old: List<Node>) {
        if (old.isEmpty()) return
        val now = System.currentTimeMillis()
        val migrated = mutableListOf<Node>()

        val subNodes = old.filter { it.fromSub }
        if (subNodes.isNotEmpty()) {
            val url = prefs.subscriptionUrl.orEmpty()
            val id = if (url.isBlank()) "sub:raw:legacy" else Profile.subId(url)
            profiles.add(Profile(id, hostOf(url) ?: "Подписка", "sub", url, updatedAt = now))
            subNodes.forEach { migrated.add(it.copy(subId = id)) }
        }
        old.filter { !it.fromSub }.forEach { n ->
            val id = "single:" + n.id
            if (profiles.none { it.id == id }) {
                profiles.add(Profile(id, n.name.ifBlank { n.host }, "single", "", updatedAt = now))
                migrated.add(n.copy(subId = id))
            }
        }
        nodes.clear(); nodes.addAll(migrated)
        val act = prefs.activeNodeId
        activeProfileId = migrated.firstOrNull { it.id == act }?.subId ?: profiles.firstOrNull()?.id
        prefs.activeProfileId = activeProfileId
        saveAll()
    }

    private fun saveAll() {
        try {
            nodesFile.writeText(JSONArray().apply { nodes.forEach { put(it.toJson()) } }.toString())
            profilesFile.writeText(JSONArray().apply { profiles.forEach { put(it.toJson()) } }.toString())
        } catch (_: Exception) {}
    }

    private fun hostOf(url: String): String? = try {
        if (url.isBlank()) null else java.net.URI(url).host?.removePrefix("www.")
    } catch (_: Exception) { null }
}
