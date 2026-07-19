package pw.x4.ninety.data

import android.content.Context
import org.json.JSONArray
import pw.x4.ninety.data.persistence.LegacyStorageInput
import pw.x4.ninety.data.persistence.PreferenceSnapshot
import pw.x4.ninety.data.persistence.StorageSnapshot
import java.io.File

/** Reads the old files without modifying them. Room migration owns the commit and verification. */
internal object LegacySnapshotReader {
    fun read(context: Context): LegacyStorageInput {
        val app = context.applicationContext
        val nodesFile = File(app.filesDir, NODES_FILE)
        val profilesFile = File(app.filesDir, PROFILES_FILE)
        val preferencesFile = File(app.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE.xml")
        val preferences = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val legacySubscriptionUrl = preferences.getString(KEY_SUB_URL, null)

        val rawNodes = readNodes(nodesFile)
        val parsedProfiles = readProfiles(profilesFile)
        val (nodes, profiles) = if (parsedProfiles != null) {
            repairCurrentGraph(rawNodes, parsedProfiles)
        } else {
            migrateFlatGraph(rawNodes, legacySubscriptionUrl)
        }

        return LegacyStorageInput(
            snapshot = StorageSnapshot(
                nodes = nodes.map(Node::toPersistedNode),
                profiles = profiles.map(Profile::toPersistedProfile),
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
            graphPresent = nodesFile.exists() || profilesFile.exists(),
            preferencesPresent = preferencesFile.exists(),
        )
    }

    private fun readNodes(file: File): List<Node> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { Node.fromJson(array.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    /** null means missing/corrupt and triggers the v0.1 flat-list migration path. */
    private fun readProfiles(file: File): List<Profile>? {
        if (!file.exists()) return null
        return runCatching {
            val array = JSONArray(file.readText())
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

        val subscriptionNodes = oldNodes.filter(Node::fromSub)
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

        oldNodes.filterNot(Node::fromSub).forEach { node ->
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
            ?.let(::java.net.URI)
            ?.host
            ?.removePrefix("www.")
    }.getOrNull()

    private const val NODES_FILE = "nodes.json"
    private const val PROFILES_FILE = "profiles.json"
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
