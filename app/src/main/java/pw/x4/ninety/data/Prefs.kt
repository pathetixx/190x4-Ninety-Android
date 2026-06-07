package pw.x4.ninety.data

import android.content.Context
import android.content.SharedPreferences

/** Тонкая обёртка над SharedPreferences (security-crypto не используем — см. опыт hub190x4). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("ninety", Context.MODE_PRIVATE)

    var themePack: String
        get() = sp.getString(KEY_THEME, "kurogane") ?: "kurogane"
        set(v) = sp.edit().putString(KEY_THEME, v).apply()

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    var activeNodeId: String?
        get() = sp.getString(KEY_ACTIVE_NODE, null)
        set(v) = sp.edit().putString(KEY_ACTIVE_NODE, v).apply()

    var subscriptionUrl: String?
        get() = sp.getString(KEY_SUB_URL, null)
        set(v) = sp.edit().putString(KEY_SUB_URL, v).apply()

    var autoConnect: Boolean
        get() = sp.getBoolean(KEY_AUTO_CONNECT, false)
        set(v) = sp.edit().putBoolean(KEY_AUTO_CONNECT, v).apply()

    var activeProfileId: String?
        get() = sp.getString(KEY_ACTIVE_PROFILE, null)
        set(v) = sp.edit().putString(KEY_ACTIVE_PROFILE, v).apply()

    // Версия, на которую юзер нажал «Позже» в OTA-модалке — больше не навязываем её.
    var skippedVersion: String?
        get() = sp.getString(KEY_SKIPPED_VERSION, null)
        set(v) = sp.edit().putString(KEY_SKIPPED_VERSION, v).apply()

    companion object {
        private const val KEY_THEME = "theme_pack"
        private const val KEY_AUTO_UPDATE = "auto_update_check"
        private const val KEY_ACTIVE_NODE = "active_node_id"
        private const val KEY_SUB_URL = "subscription_url"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
        private const val KEY_SKIPPED_VERSION = "skipped_version"

        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
