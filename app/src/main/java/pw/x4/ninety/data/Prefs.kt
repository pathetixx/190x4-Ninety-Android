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

    companion object {
        private const val KEY_THEME = "theme_pack"
        private const val KEY_AUTO_UPDATE = "auto_update_check"
        private const val KEY_ACTIVE_NODE = "active_node_id"

        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
