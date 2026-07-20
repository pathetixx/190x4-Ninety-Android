package pw.x4.ninety.data

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.x4.ninety.data.persistence.NinetyStorage
import pw.x4.ninety.data.persistence.PreferenceSnapshot
import pw.x4.ninety.data.persistence.StorageLoadResult
import pw.x4.ninety.data.persistence.StorageSource

/** Startup is synchronous; all hot-path Room/DataStore writes are serialized in the background. */
internal object PersistenceRuntime {
    private const val TAG = "NinetyStorage"

    private val writerDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ninety-storage-writer")
    }.asCoroutineDispatcher()
    private val writerScope = CoroutineScope(SupervisorJob() + writerDispatcher)

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
        val persistedNodes = nodes.map(Node::toPersistedNode)
        val persistedProfiles = profiles.map(Profile::toPersistedProfile)
        writerScope.launch {
            runCatching {
                target.replaceGraph(nodes = persistedNodes, profiles = persistedProfiles)
            }.onFailure { Log.e(TAG, "Room graph commit failed", it) }
        }
    }

    fun persistPreferences(snapshot: PreferenceSnapshot) {
        val target = storage ?: return
        writerScope.launch {
            runCatching { target.writePreferences(snapshot) }
                .onFailure { Log.e(TAG, "DataStore commit failed", it) }
        }
    }
}
