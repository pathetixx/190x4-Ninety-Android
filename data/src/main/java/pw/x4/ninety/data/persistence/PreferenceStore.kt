package pw.x4.ninety.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

internal class PreferenceStore(
    context: Context,
    private val secrets: SecretCodec,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
    )

    suspend fun read(): PreferenceSnapshot {
        val values = dataStore.data.first()
        return PreferenceSnapshot(
            themePack = values[THEME] ?: "kurogane",
            autoUpdateCheck = values[AUTO_UPDATE] ?: true,
            activeNodeId = values[ACTIVE_NODE],
            legacySubscriptionUrl = values[LEGACY_SUBSCRIPTION_URL]
                ?.let(secrets::decrypt),
            autoConnect = values[AUTO_CONNECT] ?: false,
            activeProfileId = values[ACTIVE_PROFILE],
            skippedVersion = values[SKIPPED_VERSION],
            lastSeenVersionCode = values[LAST_SEEN_VERSION] ?: 0,
            optionsJson = values[OPTIONS_JSON],
            qualityJson = values[QUALITY_JSON],
        )
    }

    suspend fun migrationVersion(): Int = dataStore.data.first()[MIGRATION_VERSION] ?: 0

    suspend fun write(snapshot: PreferenceSnapshot) {
        dataStore.edit { values -> values.write(snapshot) }
    }

    suspend fun markMigrationComplete(version: Int) {
        dataStore.edit { it[MIGRATION_VERSION] = version }
    }

    private fun MutablePreferences.write(snapshot: PreferenceSnapshot) {
        this[THEME] = snapshot.themePack
        this[AUTO_UPDATE] = snapshot.autoUpdateCheck
        setNullable(ACTIVE_NODE, snapshot.activeNodeId)
        setNullable(
            LEGACY_SUBSCRIPTION_URL,
            snapshot.legacySubscriptionUrl?.takeIf(String::isNotEmpty)?.let(secrets::encrypt),
        )
        this[AUTO_CONNECT] = snapshot.autoConnect
        setNullable(ACTIVE_PROFILE, snapshot.activeProfileId)
        setNullable(SKIPPED_VERSION, snapshot.skippedVersion)
        this[LAST_SEEN_VERSION] = snapshot.lastSeenVersionCode
        setNullable(OPTIONS_JSON, snapshot.optionsJson)
        setNullable(QUALITY_JSON, snapshot.qualityJson)
    }

    private fun <T : Any> MutablePreferences.setNullable(
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        const val FILE_NAME = "ninety.preferences_pb"
        val MIGRATION_VERSION = intPreferencesKey("storage_migration_version")
        val THEME = stringPreferencesKey("theme_pack")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update_check")
        val ACTIVE_NODE = stringPreferencesKey("active_node_id")
        val LEGACY_SUBSCRIPTION_URL = stringPreferencesKey("legacy_subscription_url")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile_id")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
        val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version_code")
        val OPTIONS_JSON = stringPreferencesKey("options_json")
        val QUALITY_JSON = stringPreferencesKey("quality_json")
    }
}
