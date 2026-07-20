package pw.x4.ninety.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import pw.x4.ninety.data.persistence.PreferenceSnapshot
import java.util.concurrent.atomic.AtomicReference

/**
 * Synchronous compatibility facade for existing UI/service callers.
 *
 * Reads come from an in-memory DataStore snapshot. Writes are deliberately dual: the old
 * SharedPreferences file is committed first as a rollback journal, then DataStore is written and
 * read back by [PersistenceRuntime].
 */
class Prefs private constructor(
    context: Context,
    initial: PreferenceSnapshot,
) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    private val state = AtomicReference(initial)

    var themePack: String
        get() = state.get().themePack
        set(value) = update(
            transform = { it.copy(themePack = value) },
            legacyWrite = { putString(KEY_THEME, value) },
        )

    var autoUpdateCheck: Boolean
        get() = state.get().autoUpdateCheck
        set(value) = update(
            transform = { it.copy(autoUpdateCheck = value) },
            legacyWrite = { putBoolean(KEY_AUTO_UPDATE, value) },
        )

    var activeNodeId: String?
        get() = state.get().activeNodeId
        set(value) = update(
            transform = { it.copy(activeNodeId = value) },
            legacyWrite = { putNullableString(KEY_ACTIVE_NODE, value) },
        )

    var subscriptionUrl: String?
        get() = state.get().legacySubscriptionUrl
        set(value) = update(
            transform = { it.copy(legacySubscriptionUrl = value) },
            legacyWrite = { putNullableString(KEY_SUB_URL, value) },
        )

    var autoConnect: Boolean
        get() = state.get().autoConnect
        set(value) = update(
            transform = { it.copy(autoConnect = value) },
            legacyWrite = { putBoolean(KEY_AUTO_CONNECT, value) },
        )

    var activeProfileId: String?
        get() = state.get().activeProfileId
        set(value) = update(
            transform = { it.copy(activeProfileId = value) },
            legacyWrite = { putNullableString(KEY_ACTIVE_PROFILE, value) },
        )

    var skippedVersion: String?
        get() = state.get().skippedVersion
        set(value) = update(
            transform = { it.copy(skippedVersion = value) },
            legacyWrite = { putNullableString(KEY_SKIPPED_VERSION, value) },
        )

    var lastSeenVersionCode: Int
        get() = state.get().lastSeenVersionCode
        set(value) = update(
            transform = { it.copy(lastSeenVersionCode = value) },
            legacyWrite = { putInt(KEY_LAST_VERSION, value) },
        )

    var optionsJson: String?
        get() = state.get().optionsJson
        set(value) = update(
            transform = { it.copy(optionsJson = value) },
            legacyWrite = { putNullableString(KEY_OPTIONS_JSON, value) },
        )

    var qualityJson: String?
        get() = state.get().qualityJson
        set(value) = update(
            transform = { it.copy(qualityJson = value) },
            legacyWrite = { putNullableString(KEY_QUALITY_JSON, value) },
        )

    internal val snapshot: PreferenceSnapshot
        get() = state.get()

    @SuppressLint("ApplySharedPref")
    private fun update(
        transform: (PreferenceSnapshot) -> PreferenceSnapshot,
        legacyWrite: SharedPreferences.Editor.() -> Unit,
    ) {
        val next = synchronized(this) {
            transform(state.get()).also {
                state.set(it)
                check(sp.edit().apply(legacyWrite).commit()) {
                    "failed to commit SharedPreferences rollback journal"
                }
            }
        }
        PersistenceRuntime.persistPreferences(next)
    }

    private fun SharedPreferences.Editor.putNullableString(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    companion object {
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
        private const val KEY_QUALITY_JSON = "quality_json"

        @Volatile
        private var instance: Prefs? = null

        fun initialize(context: Context, snapshot: PreferenceSnapshot): Prefs = synchronized(this) {
            Prefs(context.applicationContext, snapshot).also { instance = it }
        }

        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(context.applicationContext, readLegacyFallback(context)).also {
                instance = it
            }
        }

        private fun readLegacyFallback(context: Context): PreferenceSnapshot {
            val sp = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return PreferenceSnapshot(
                themePack = sp.getString(KEY_THEME, "kurogane") ?: "kurogane",
                autoUpdateCheck = sp.getBoolean(KEY_AUTO_UPDATE, true),
                activeNodeId = sp.getString(KEY_ACTIVE_NODE, null),
                legacySubscriptionUrl = sp.getString(KEY_SUB_URL, null),
                autoConnect = sp.getBoolean(KEY_AUTO_CONNECT, false),
                activeProfileId = sp.getString(KEY_ACTIVE_PROFILE, null),
                skippedVersion = sp.getString(KEY_SKIPPED_VERSION, null),
                lastSeenVersionCode = sp.getInt(KEY_LAST_VERSION, 0),
                optionsJson = sp.getString(KEY_OPTIONS_JSON, null),
                qualityJson = sp.getString(KEY_QUALITY_JSON, null),
            )
        }
    }
}
