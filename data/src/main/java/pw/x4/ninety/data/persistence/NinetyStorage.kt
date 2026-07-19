package pw.x4.ninety.data.persistence

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room + DataStore gateway.
 *
 * Migration is deliberately two-phase: write and read back the Room graph, write and read back
 * DataStore, then set the marker. Legacy files remain untouched and are dual-written as a rollback
 * journal. Because the app writes JSON before Room, a valid graph mismatch means Room did not
 * finish the last commit and can safely be recovered from JSON.
 */
class NinetyStorage private constructor(
    private val database: NinetyDatabase,
    private val preferences: PreferenceStore,
    private val mapper: EntityMapper,
) {
    private val mutex = Mutex()

    suspend fun initialize(legacy: LegacyStorageInput): StorageLoadResult = mutex.withLock {
        val legacySnapshot = legacy.snapshot.normalized()
        val migrationVersion = preferences.migrationVersion()
        val currentPreferences = preferences.read()
        val databaseGraph = readGraph()
        val legacyDiffers = legacy.graphPresent && !databaseGraph.matches(legacySnapshot)

        if (migrationVersion >= CURRENT_MIGRATION_VERSION) {
            if (legacyDiffers) {
                replaceGraphInternal(legacySnapshot.nodes, legacySnapshot.profiles)
                val recovered = StorageSnapshot(
                    nodes = legacySnapshot.nodes,
                    profiles = legacySnapshot.profiles,
                    preferences = currentPreferences,
                ).normalized()
                verifyGraph(recovered.nodes, recovered.profiles)
                return@withLock StorageLoadResult(
                    snapshot = recovered,
                    source = StorageSource.LEGACY_RECOVERY,
                    migrationVerified = true,
                )
            }
            val snapshot = StorageSnapshot(
                nodes = databaseGraph.nodes,
                profiles = databaseGraph.profiles,
                preferences = currentPreferences,
            ).normalized()
            return@withLock StorageLoadResult(
                snapshot = snapshot,
                source = if (snapshot.isEmpty()) StorageSource.EMPTY else StorageSource.ROOM,
                migrationVerified = true,
            )
        }

        val importLegacy = legacy.graphPresent && (databaseGraph.isEmpty() || legacyDiffers)
        val expectedGraph = if (importLegacy) {
            replaceGraphInternal(legacySnapshot.nodes, legacySnapshot.profiles)
            Graph(legacySnapshot.nodes, legacySnapshot.profiles)
        } else {
            // Crash recovery: Room may have committed before the DataStore marker.
            databaseGraph
        }

        val expected = StorageSnapshot(
            nodes = expectedGraph.nodes,
            profiles = expectedGraph.profiles,
            preferences = legacySnapshot.preferences,
        ).normalized()

        preferences.write(expected.preferences)
        verifyGraph(expected.nodes, expected.profiles)
        val readBack = readSnapshotInternal().normalized()
        require(readBack == expected) {
            "Room/DataStore read-back verification failed; legacy data was kept untouched"
        }
        preferences.markMigrationComplete(CURRENT_MIGRATION_VERSION)

        StorageLoadResult(
            snapshot = readBack,
            source = when {
                importLegacy -> StorageSource.LEGACY_IMPORT
                readBack.isEmpty() -> StorageSource.EMPTY
                else -> StorageSource.ROOM
            },
            migrationVerified = true,
        )
    }

    suspend fun readSnapshot(): StorageSnapshot = mutex.withLock {
        readSnapshotInternal().normalized()
    }

    suspend fun replaceGraph(nodes: List<PersistedNode>, profiles: List<PersistedProfile>) {
        mutex.withLock {
            val expected = StorageSnapshot(nodes = nodes, profiles = profiles).normalized()
            requireValidGraph(expected.nodes, expected.profiles)
            replaceGraphInternal(expected.nodes, expected.profiles)
            verifyGraph(expected.nodes, expected.profiles)
        }
    }

    suspend fun writePreferences(snapshot: PreferenceSnapshot) {
        mutex.withLock {
            preferences.write(snapshot)
            require(preferences.read() == snapshot) { "DataStore read-back verification failed" }
        }
    }

    private suspend fun readSnapshotInternal(): StorageSnapshot {
        val graph = readGraph()
        return StorageSnapshot(
            nodes = graph.nodes,
            profiles = graph.profiles,
            preferences = preferences.read(),
        )
    }

    private suspend fun readGraph(): Graph = Graph(
        nodes = database.nodeDao().readAll().map { mapper.fromEntity(it) },
        profiles = database.profileDao().readAll().map { mapper.fromEntity(it) },
    )

    private suspend fun replaceGraphInternal(
        nodes: List<PersistedNode>,
        profiles: List<PersistedProfile>,
    ) {
        requireValidGraph(nodes, profiles)
        database.withTransaction {
            database.nodeDao().deleteAll()
            database.profileDao().deleteAll()
            if (profiles.isNotEmpty()) {
                database.profileDao().insertAll(profiles.map { mapper.toEntity(it) })
            }
            if (nodes.isNotEmpty()) {
                database.nodeDao().insertAll(nodes.map { mapper.toEntity(it) })
            }
        }
    }

    private suspend fun verifyGraph(
        nodes: List<PersistedNode>,
        profiles: List<PersistedProfile>,
    ) {
        val actual = readGraph()
        require(actual.nodes.sortedBy { it.id } == nodes.sortedBy { it.id }) {
            "Room node verification failed"
        }
        require(actual.profiles.sortedBy { it.id } == profiles.sortedBy { it.id }) {
            "Room profile verification failed"
        }
    }

    private fun requireValidGraph(
        nodes: List<PersistedNode>,
        profiles: List<PersistedProfile>,
    ) {
        require(profiles.all { it.id.isNotBlank() }) { "blank profile id" }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "duplicate profile id" }
        val profileIds = profiles.mapTo(hashSetOf()) { it.id }
        require(nodes.all { it.id.isNotBlank() }) { "blank node id" }
        require(nodes.map { it.id }.distinct().size == nodes.size) { "duplicate node id" }
        require(nodes.all { it.profileId in profileIds }) { "node references missing profile" }
    }

    private data class Graph(
        val nodes: List<PersistedNode>,
        val profiles: List<PersistedProfile>,
    ) {
        fun isEmpty(): Boolean = nodes.isEmpty() && profiles.isEmpty()

        fun matches(snapshot: StorageSnapshot): Boolean =
            nodes.sortedBy { it.id } == snapshot.nodes.sortedBy { it.id } &&
                profiles.sortedBy { it.id } == snapshot.profiles.sortedBy { it.id }
    }

    private fun StorageSnapshot.isEmpty(): Boolean = nodes.isEmpty() && profiles.isEmpty()

    companion object {
        const val CURRENT_MIGRATION_VERSION = 1

        @Volatile
        private var instance: NinetyStorage? = null

        fun get(context: Context): NinetyStorage = instance ?: synchronized(this) {
            instance ?: run {
                val codec = AndroidKeystoreSecretCodec()
                NinetyStorage(
                    database = NinetyDatabase.create(context.applicationContext),
                    preferences = PreferenceStore(context.applicationContext, codec),
                    mapper = EntityMapper(codec),
                ).also { instance = it }
            }
        }
    }
}
