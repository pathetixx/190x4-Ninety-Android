package pw.x4.ninety.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import pw.x4.ninety.data.persistence.NinetyStorage
import pw.x4.ninety.data.persistence.PreferenceSnapshot
import pw.x4.ninety.data.persistence.StorageLoadResult
import pw.x4.ninety.data.persistence.StorageSource

/** Synchronous compatibility boundary while Store/Prefs still expose their legacy APIs. */
internal object PersistenceRuntime {
    private const val TAG = "NinetyStorage"

    @Volatile
    private var storage: NinetyStorage? = null

    fun initialize(context: Context): StorageLoadResult {
        val legacy = LegacySnapshotReader.read(context)
        val target = NinetyStorage.get(context.applicationContext).also { storage = it }
        return try {
            runBlocking(Dispatchers.IO) { target.initialize(legacy) }
        } catch (error: Throwable) {
            // Migration marker is not written on failure, so the next launch can retry safely.
            Log.e(TAG, "Room/DataStore bootstrap failed; using untouched legacy snapshot", error)
            StorageLoadResult(
                snapshot = legacy.snapshot,
                source = StorageSource.LEGACY_FALLBACK,
                migrationVerified = false,
            )
        }
    }

    fun persistGraph(nodes: List<Node>, profiles: List<Profile>) {
        val target = storage ?: return
        runCatching {
            runBlocking(Dispatchers.IO) {
                target.replaceGraph(
                    nodes = nodes.map(Node::toPersistedNode),
                    profiles = profiles.map(Profile::toPersistedProfile),
                )
            }
        }.onFailure { Log.e(TAG, "Room graph commit failed; legacy JSON remains authoritative", it) }
    }

    fun persistPreferences(snapshot: PreferenceSnapshot) {
        val target = storage ?: return
        runCatching {
            runBlocking(Dispatchers.IO) { target.writePreferences(snapshot) }
        }.onFailure { Log.e(TAG, "DataStore commit failed; SharedPreferences remains authoritative", it) }
    }
}
