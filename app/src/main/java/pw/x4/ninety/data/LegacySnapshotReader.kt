package pw.x4.ninety.data

import android.content.Context
import org.json.JSONArray
import pw.x4.ninety.data.persistence.LegacyStorageInput
import pw.x4.ninety.data.persistence.PreferenceSnapshot
import pw.x4.ninety.data.persistence.RollbackJournal
import pw.x4.ninety.data.persistence.StorageSnapshot
import java.io.File
import java.net.URI

/** Reads the old files without modifying them. Room migration owns the commit and verification. */
internal object LegacySnapshotReader {
    fun read(context: Context): LegacyStorageInput {
        val app = context.applicationContext
        val nodesFile = File(app.filesDir, NODES_FILE)
        val profilesFile = File(app.filesDir, PROFILES_FILE)
        val journalFile = File(app.filesDir, JOURNAL_FILE)
        val preferencesFile = File(app.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE.xml")
        val preferences = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val legacySubscriptionUrl = preferences.getString(KEY_SUB_URL, null)

        val nodesText = readText(nodesFile)
        val profilesText = readText(profilesFile)
        val journalText = readText(journalFile)
        val rawNodes = parseNodes(nodesText)
        val rawProfiles = parseProfiles(profilesText)

        val filesReadable = nodesText.valid && profilesText.valid
        val payloadsParsable = rawNodes != null && (!profilesFile.exists() || rawProfiles != null)
        val journalValid = if (!journalFile.exists()) {
            true // pre-Room legacy installation
        } else {
            val manifest = journalText.content
            val nodesJson = nodesText.content
            val profilesJson = profilesText.content
            journalText.valid && manifest != null && nodesJson != null && profilesJson != null &&
                RollbackJournal.validates(manifest, nodesJson, profilesJson)
        }
        val graphPresent = (nodesFile.exists() || profilesFile.exists()) &&
            filesReadable && payloadsParsable && journalValid

        val (nodes, profiles) = when {
            !graphPresent -> emptyList<Node>() to emptyList()
            profilesFile.exists() -> repairCurrentGraph(rawNodes.orEmpty(), rawProfiles.orEmpty())
            else -> migrateFlatGraph(rawNodes.orEmpty(), legacySubscriptionUrl)
        }

        return LegacyStorageInput(
            snapshot = StorageSnapshot(
                nodes = nodes.map { it.toPersistedNode() },
                profiles = profiles.map { it.toPersistedProfile() },
                preferences = PreferenceSnapshot(
                    themePack = preferences.getString(KEY_THEME, "kurogane") ?: "kurogane",
                    autoUpdateCheck = preferences.getBoolean(KEY_AUTO_UPDATE, true),
                    activeNodeId = preferences.getString(KEY_ACTIVE_NODE, null),
                    legacySubscriptionUrl = legacySubscriptionUrl,
                    autoConnect = preferences.getBoolean(KEY_AUTO_CONNECT, false),
                    activeProfileId = preferences.getString(KEY_ACTIVE_PROFILE, null),
                    skippedVersion = preferences.getString(KEY_SKIPPED_VERSION, null),
                    lastSeenVersionCode = preferences.getInt(KEY_LAST_VERSION, 0),
                    optionsJson = preferences.getString(KEY_OPTIONS_JSON, null),
                ),
            ).normalized(),
            graphPresent = graphPresent,
            preferencesPresent = preferencesFile.exists(),
        )
    }

    private fun readText(file: File): FileText {
        if (!file.exists()) return FileText(valid = true, content = null)
        return runCatching { file.readText() }
            .fold(
                onSuccess = { FileText(valid = true, content = it) },
                onFailure = { FileText(valid = false, content = null) },
            )
    }

    private fun parseNodes(file: FileText): List<Node>? {
        if (!file.valid) return null
        val text = file.content ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { Node.fromJson(array.getJSONObject(it)) }
        }.getOrNull()
    }

    private fun parseProfiles(file: FileText): List<Profile>? {
        if (!file.valid) return null
        val text = file.content ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { Profile.fromJson(array.getJSONObject(it)) }
        }.getOrNull()
    }

    private fun repairCurrentGraph(
        rawNodes: List<Node>,
        rawProfiles: List<Profile>,
    ): Pair<List<Node>, List<Profile>> {
        val profiles = rawProfiles
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .toMutableList()
        val profileIds = profiles.mapTo(hashSetOf()) { it.id }
        val nodes = rawNodes.distinctBy { it.id }.map { node ->
            if (node.subId.isNotBlank() && node.subId in profileIds) {
                node
            } else {
                val profileId = "single:${node.id}"
                if (profileId !in profileIds) {
                    profiles += Profile(
                        id = profileId,
                        name = node.name.ifBlank { node.host },
                        type = "single",
                        updatedAt = System.currentTimeMillis(),
                    )
                    profileIds += profileId
                }
                node.copy(subId = profileId, fromSub = false)
            }
        }
        return nodes to profiles
    }

    private fun migrateFlatGraph(
        oldNodes: List<Node>,
        legacySubscriptionUrl: String?,
    ): Pair<List<Node>, List<Profile>> {
        if (oldNodes.isEmpty()) return emptyList<Node>() to emptyList()
        val now = System.currentTimeMillis()
        val nodes = mutableListOf<Node>()
        val profiles = mutableListOf<Profile>()

        val subscriptionNodes = oldNodes.filter { it.fromSub }
        if (subscriptionNodes.isNotEmpty()) {
            val url = legacySubscriptionUrl.orEmpty()
            val profileId = if (url.isBlank()) "sub:raw:legacy" else Profile.subId(url)
            profiles += Profile(
                id = profileId,
                name = hostOf(url) ?: "Подписка",
                type = "sub",
                url = url,
                updatedAt = now,
            )
            subscriptionNodes.forEach { nodes += it.copy(subId = profileId) }
        }

        oldNodes.filterNot { it.fromSub }.forEach { node ->
            val profileId = "single:${node.id}"
            if (profiles.none { it.id == profileId }) {
                profiles += Profile(
                    id = profileId,
                    name = node.name.ifBlank { node.host },
                    type = "single",
                    updatedAt = now,
                )
                nodes += node.copy(subId = profileId)
            }
        }
        return nodes.distinctBy { it.id } to profiles.distinctBy { it.id }
    }

    private fun hostOf(url: String): String? = runCatching {
        url.takeIf(String::isNotBlank)
            ?.let { URI(it) }
            ?.host
            ?.removePrefix("www.")
    }.getOrNull()

    private data class FileText(
        val valid: Boolean,
        val content: String?,
    )

    private const val NODES_FILE = "nodes.json"
    private const val PROFILES_FILE = "profiles.json"
    private const val JOURNAL_FILE = "storage-journal.v1"
    private const val PREFS_FILE = "ninety"
    private const val KEY_THEME = "theme_pack"
    private const val KEY_AUTO_UPDATE = "auto_update_check"
    private const val KEY_ACTIVE_NODE = "active_node_id"
    private const val KEY_SUB_URL = "subscription_url"
    private const val KEY_AUTO_CONNECT = "auto_connect"
    private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val KEY_LAST_VERSION = "last_seen_version_code"
    private const val KEY_OPTIONS_JSON = "options_json"
}
