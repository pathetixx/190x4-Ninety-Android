package pw.x4.ninety.data

import org.json.JSONObject

/**
 * Профиль — подписка (много нод) или одиночный конфиг (одна нода). Модель как в
 * desktop-Ninety: Профили = подписки + single-конфиги, активный помечен точкой;
 * Ноды показывают узлы активного профиля. Поля userinfo (used/total/expire)
 * приходят из заголовка `Subscription-Userinfo` при загрузке подписки.
 */
data class Profile(
    val id: String,
    val name: String,
    val type: String,          // "sub" | "single"
    val url: String = "",
    val used: Long = 0,        // upload+download, байты
    val total: Long = 0,       // байты, 0 = безлимит
    val expire: Long = 0,      // unix-секунды, 0 = бессрочно
    val updatedAt: Long = 0,   // ms последнего обновления
) {
    val isSub: Boolean get() = type == "sub"

    /** Осталось дней до истечения, либо null если бессрочно. */
    fun daysLeft(): Int? = if (expire > 0) {
        ((expire * 1000L - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
    } else null

    /** Доля израсходованного трафика [0..1], либо null если безлимит/неизвестно. */
    fun usedFraction(): Float? = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type); put("url", url)
        put("used", used); put("total", total); put("expire", expire); put("updatedAt", updatedAt)
    }

    companion object {
        /** Стабильный id профиля-подписки по её URL. */
        fun subId(url: String): String = "sub:" + url.trim().hashCode()

        fun fromJson(o: JSONObject) = Profile(
            id = o.optString("id"),
            name = o.optString("name"),
            type = o.optString("type", "sub"),
            url = o.optString("url"),
            used = o.optLong("used"),
            total = o.optLong("total"),
            expire = o.optLong("expire"),
            updatedAt = o.optLong("updatedAt"),
        )
    }
}

/** userinfo из заголовка `Subscription-Userinfo: upload=..; download=..; total=..; expire=..`. */
data class SubUserinfo(val used: Long, val total: Long, val expire: Long) {
    companion object {
        val EMPTY = SubUserinfo(0, 0, 0)
        fun parse(header: String?): SubUserinfo {
            if (header.isNullOrBlank()) return EMPTY
            var up = 0L; var down = 0L; var total = 0L; var expire = 0L
            for (part in header.split(";")) {
                val kv = part.split("=", limit = 2)
                if (kv.size != 2) continue
                val v = kv[1].trim().toLongOrNull() ?: continue
                when (kv[0].trim().lowercase()) {
                    "upload" -> up = v
                    "download" -> down = v
                    "total" -> total = v
                    "expire" -> expire = v
                }
            }
            return SubUserinfo(up + down, total, expire)
        }
    }
}
