package pw.x4.ninety.data.persistence

import pw.x4.ninety.core.model.ProxySelection

/** Полная запись ноды без зависимостей от Android UI и legacy JSON. */
data class PersistedNode(
    val id: String,
    val profileId: String,
    val protocol: String,
    val name: String,
    val host: String,
    val port: Int,
    val uuid: String = "",
    val password: String = "",
    val method: String = "",
    val cipher: String = "auto",
    val alterId: Int = 0,
    val security: String = "none",
    val transport: String = "tcp",
    val flow: String = "",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val alpn: String = "",
    val path: String = "",
    val hostHeader: String = "",
    val serviceName: String = "",
    val mode: String = "",
    val extra: String = "",
    val upMbps: Int = 0,
    val downMbps: Int = 0,
    val obfs: String = "",
    val obfsPassword: String = "",
    val certificatePublicKeySha256: String = "",
    val congestionControl: String = "",
    val udpRelayMode: String = "",
    val insecure: Boolean = false,
    val zeroRttHandshake: Boolean = false,
    val disableSni: Boolean = false,
    val plugin: String = "",
    val pluginOptions: String = "",
    val raw: String = "",
    val fromSubscription: Boolean = false,
)

data class PersistedProfile(
    val id: String,
    val name: String,
    val type: String,
    val url: String = "",
    val used: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val updatedAt: Long = 0,
)

/**
 * Небольшие настройки приложения. Синхронные legacy-facade классы держат локальный кэш,
 * а каноническая копия хранится в Preferences DataStore.
 */
data class PreferenceSnapshot(
    val themePack: String = "kurogane",
    val autoUpdateCheck: Boolean = true,
    val activeNodeId: String? = null,
    val legacySubscriptionUrl: String? = null,
    val autoConnect: Boolean = false,
    val activeProfileId: String? = null,
    val skippedVersion: String? = null,
    val lastSeenVersionCode: Int = 0,
    val optionsJson: String? = null,
)

data class StorageSnapshot(
    val nodes: List<PersistedNode> = emptyList(),
    val profiles: List<PersistedProfile> = emptyList(),
    val preferences: PreferenceSnapshot = PreferenceSnapshot(),
) {
    /** Стабильный порядок нужен для read-back verification после транзакции Room. */
    fun normalized(): StorageSnapshot {
        val cleanProfiles = profiles
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.id }
        val profileIds = cleanProfiles.mapTo(hashSetOf()) { it.id }
        val cleanNodes = nodes
            .filter { it.id.isNotBlank() && it.profileId in profileIds }
            .distinctBy { it.id }
            .sortedBy { it.id }

        val requestedProfile = preferences.activeProfileId
        val activeProfile = requestedProfile
            ?.takeIf(profileIds::contains)
            ?: cleanProfiles.firstOrNull()?.id
        val activeProfileNodes = cleanNodes.filter { it.profileId == activeProfile }
        val requestedNode = preferences.activeNodeId
        val activeNode = when {
            requestedNode == ProxySelection.AUTO_ID && activeProfileNodes.isNotEmpty() -> requestedNode
            requestedNode != null && activeProfileNodes.any { it.id == requestedNode } -> requestedNode
            else -> activeProfileNodes.firstOrNull()?.id
        }

        return copy(
            nodes = cleanNodes,
            profiles = cleanProfiles,
            preferences = preferences.copy(
                activeProfileId = activeProfile,
                activeNodeId = activeNode,
            ),
        )
    }
}

/** Whether JSON files exist matters: an existing `[]` is a valid post-clear rollback journal. */
data class LegacyStorageInput(
    val snapshot: StorageSnapshot,
    val graphPresent: Boolean,
)

enum class StorageSource {
    ROOM,
    LEGACY_IMPORT,
    LEGACY_RECOVERY,
    LEGACY_FALLBACK,
    EMPTY,
}

data class StorageLoadResult(
    val snapshot: StorageSnapshot,
    val source: StorageSource,
    val migrationVerified: Boolean,
)
