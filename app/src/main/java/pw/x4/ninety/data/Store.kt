package pw.x4.ninety.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.json.JSONArray
import pw.x4.ninety.core.model.ProxySelection
import pw.x4.ninety.data.persistence.RollbackJournal
import pw.x4.ninety.data.persistence.StorageSnapshot

/**
 * Synchronous in-memory compatibility store for Compose and VPN callers.
 *
 * Once Room migration is verified, plaintext legacy JSON is deleted and no longer written.
 * Mutations queue an immutable graph snapshot to the serialized background persistence writer.
 * Worker-facing reads share the same monitor as mutations, so VPN reloads never observe a partial
 * profile refresh.
 */
object Store {
    private lateinit var nodesFile: File
    private lateinit var profilesFile: File
    private lateinit var journalFile: File
    private lateinit var prefs: Prefs
    private var legacyJournalEnabled = true

    val nodes = mutableStateListOf<Node>()
    val profiles = mutableStateListOf<Profile>()

    var activeId by mutableStateOf<String?>(null)
        private set
    var activeProfileId by mutableStateOf<String?>(null)
        private set

    @Synchronized
    fun init(context: Context, snapshot: StorageSnapshot, migrationVerified: Boolean) {
        if (::nodesFile.isInitialized) return
        val app = context.applicationContext
        nodesFile = File(app.filesDir, "nodes.json")
        profilesFile = File(app.filesDir, "profiles.json")
        journalFile = File(app.filesDir, "storage-journal.v1")
        prefs = Prefs.get(app)
        legacyJournalEnabled = !migrationVerified

        val migratedIdByStoredId = snapshot.nodes.associate { persisted ->
            persisted.id to persisted.toLegacyNode().id
        }
        nodes.clear()
        nodes.addAll(snapshot.nodes.map { it.toLegacyNode() })
        profiles.clear()
        profiles.addAll(snapshot.profiles.map { it.toLegacyProfile() })

        val requestedProfile = snapshot.preferences.activeProfileId
        activeProfileId = requestedProfile
            ?.takeIf { requested -> profiles.any { it.id == requested } }
            ?: profiles.firstOrNull()?.id

        val activeNodeIds = nodesOf(activeProfileId).mapTo(hashSetOf()) { it.id }
        val requestedNode = snapshot.preferences.activeNodeId
        activeId = when {
            requestedNode == AUTO_ID && activeNodeIds.isNotEmpty() -> AUTO_ID
            requestedNode != null -> migratedIdByStoredId[requestedNode]
                ?.takeIf(activeNodeIds::contains)
                ?: requestedNode.takeIf(activeNodeIds::contains)
                ?: activeNodeIds.firstOrNull()
            else -> activeNodeIds.firstOrNull()
        }

        val graphIdsChanged = snapshot.nodes.any { persisted ->
            migratedIdByStoredId[persisted.id] != persisted.id
        }
        if (prefs.activeProfileId != activeProfileId) prefs.activeProfileId = activeProfileId
        if (prefs.activeNodeId != activeId) prefs.activeNodeId = activeId

        if (migrationVerified) {
            deleteLegacyFiles()
            if (graphIdsChanged) {
                PersistenceRuntime.persistGraph(nodes.toList(), profiles.toList())
            }
        }
    }

    var subscriptionUrl: String?
        get() = prefs.subscriptionUrl
        set(value) { prefs.subscriptionUrl = value }

    const val AUTO_ID = ProxySelection.AUTO_ID

    val selection: ProxySelection?
        get() = ProxySelection.fromPersisted(activeId)

    val isAutoActive: Boolean
        get() = selection == ProxySelection.Auto

    @Synchronized
    fun activeSelectionId(): String? = activeId

    @Synchronized
    fun activeProfileIdValue(): String? = activeProfileId

    @Synchronized
    fun profilesSnapshot(): List<Profile> = profiles.toList()

    @Synchronized
    fun activeNode(): Node? = nodes.firstOrNull { it.id == activeId }

    @Synchronized
    fun hasRunnableSelection(): Boolean = when (selection) {
        ProxySelection.Auto -> supportedActiveNodes().isNotEmpty()
        is ProxySelection.Node -> activeNode()?.supported == true
        null -> false
    }

    @Synchronized
    fun activeNodeLabel(): String? = when {
        isAutoActive -> "Авто"
        else -> activeNode()?.let { it.name.ifBlank { it.host } }
    }

    @Synchronized
    fun activeProfile(): Profile? = profiles.firstOrNull { it.id == activeProfileId }

    @Synchronized
    fun nodesOf(profileId: String?): List<Node> = nodes.filter { it.subId == profileId }

    @Synchronized
    fun activeProfileNodes(): List<Node> = nodesOf(activeProfileId)

    @Synchronized
    fun supportedActiveNodes(): List<Node> = activeProfileNodes().filter { it.supported }

    @Synchronized
    fun nodeCount(profileId: String): Int = nodes.count { it.subId == profileId }

    @Synchronized
    fun setActive(id: String) {
        val next = requireNotNull(ProxySelection.fromPersisted(id)) {
            "proxy selection must not be blank"
        }
        if (next is ProxySelection.Node) {
            require(nodesOf(activeProfileId).any { it.id == next.nodeId }) {
                "proxy selection does not belong to active profile"
            }
        }
        activeId = next.persistedValue
        prefs.activeNodeId = next.persistedValue
    }

    @Synchronized
    fun setActiveProfile(id: String) {
        require(profiles.any { it.id == id }) { "profile does not exist" }
        activeProfileId = id
        prefs.activeProfileId = id
        val first = nodesOf(id).firstOrNull()?.id
        activeId = first
        prefs.activeNodeId = first
    }

    @Synchronized
    fun addSubscriptionProfile(
        url: String,
        content: String,
        info: SubUserinfo,
        name: String? = null,
    ): Int {
        val parsed = LinkParser.parseSubscription(content)
        require(parsed.isNotEmpty()) { "Подписка пуста или не распознана" }
        val legacyRawId = "sub:raw:" + content.trim().hashCode()
        val id = if (url.isBlank()) {
            legacyRawId.takeIf { candidate -> profiles.any { it.id == candidate } }
                ?: StableId.rawProfile(content)
        } else {
            val normalizedUrl = normalizeSubscriptionUrl(url)
            profiles.firstOrNull { profile ->
                profile.isSub && normalizeSubscriptionUrl(profile.url) == normalizedUrl
            }?.id ?: Profile.subId(normalizedUrl)
        }
        nodes.removeAll { it.subId == id }
        val tagged = parsed
            .map { it.copy(fromSub = true, subId = id) }
            .distinctBy(Node::id)
        nodes.addAll(tagged)
        upsertProfile(
            Profile(
                id = id,
                name = name ?: hostOf(url) ?: "Подписка",
                type = "sub",
                url = url.trim(),
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

    @Synchronized
    fun addSingleConfig(raw: String): Boolean {
        val node = LinkParser.parseLink(raw) ?: return false
        val profileId = "single:${node.fingerprint.take(32)}"
        val tagged = node.copy(subId = profileId)
        if (profiles.any { it.id == profileId } || nodes.any { it.id == tagged.id }) return false
        nodes.add(tagged)
        upsertProfile(
            Profile(
                id = profileId,
                name = node.name.ifBlank { node.host },
                type = "single",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        if (activeProfileId == null) setActiveProfile(profileId)
        saveAll()
        return true
    }

    @Synchronized
    fun refreshProfileNodes(id: String, content: String, info: SubUserinfo): Int {
        val parsed = LinkParser.parseSubscription(content)
        require(parsed.isNotEmpty()) { "Подписка пуста или не распознана" }
        nodes.removeAll { it.subId == id }
        val tagged = parsed
            .map { it.copy(fromSub = true, subId = id) }
            .distinctBy(Node::id)
        nodes.addAll(tagged)
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

    @Synchronized
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

    @Synchronized
    fun reparseAllFromRaw(): Int {
        if (nodes.isEmpty()) return 0
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
            nodes.addAll(rebuilt.distinctBy(Node::id))
            val validIds = nodesOf(activeProfileId).mapTo(hashSetOf()) { it.id }
            if (activeId != AUTO_ID && activeId !in validIds) {
                activeId = validIds.firstOrNull()
                prefs.activeNodeId = activeId
            }
            saveAll()
        }
        return healed
    }

    @Synchronized
    fun clear() {
        nodes.clear()
        profiles.clear()
        activeId = null
        activeProfileId = null
        prefs.activeNodeId = null
        prefs.activeProfileId = null
        saveAll()
    }

    private fun saveAll() {
        if (legacyJournalEnabled) {
            val nodesJson = JSONArray().apply { nodes.forEach { put(it.toJson()) } }.toString()
            val profilesJson = JSONArray().apply { profiles.forEach { put(it.toJson()) } }.toString()
            runCatching {
                writeAtomically(nodesFile, nodesJson)
                writeAtomically(profilesFile, profilesJson)
                writeAtomically(journalFile, RollbackJournal.create(nodesJson, profilesJson))
            }.getOrElse { throw IllegalStateException("failed to write legacy rollback journal", it) }
        } else {
            deleteLegacyFiles()
        }
        PersistenceRuntime.persistGraph(nodes.toList(), profiles.toList())
    }

    private fun deleteLegacyFiles() {
        listOf(
            nodesFile,
            profilesFile,
            journalFile,
            File(nodesFile.parentFile, ".${nodesFile.name}.tmp"),
            File(profilesFile.parentFile, ".${profilesFile.name}.tmp"),
            File(journalFile.parentFile, ".${journalFile.name}.tmp"),
        ).forEach { file -> runCatching { file.delete() } }
    }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(target.parentFile, ".${target.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        runCatching {
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING)
        }.getOrThrow()
    }

    private fun normalizeSubscriptionUrl(url: String): String = url.trim()

    private fun hostOf(url: String): String? = runCatching {
        if (url.isBlank()) null else java.net.URI(url).host?.removePrefix("www.")
    }.getOrNull()
}
