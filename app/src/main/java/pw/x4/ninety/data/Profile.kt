package pw.x4.ninety.data

import org.json.JSONObject

/** Subscription or single-node profile shown by the Android and desktop-compatible UI. */
data class Profile(
    val id: String,
    val name: String,
    val type: String,
    val url: String = "",
    val used: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val updatedAt: Long = 0,
) {
    val isSub: Boolean get() = type == "sub"

    fun daysLeft(): Int? = if (expire > 0) {
        ((expire * 1000L - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
    } else {
        null
    }

    fun usedFraction(): Float? =
        if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type); put("url", url)
        put("used", used); put("total", total); put("expire", expire); put("updatedAt", updatedAt)
    }

    companion object {
        /** New profiles use SHA-256; Store reuses an existing legacy id when the URL already exists. */
        fun subId(url: String): String =
            "sub:" + StableId.sha256(url.trim()).take(32)

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

/** `Subscription-Userinfo: upload=..; download=..; total=..; expire=..`. */
data class SubUserinfo(val used: Long, val total: Long, val expire: Long) {
    companion object {
        val EMPTY = SubUserinfo(0, 0, 0)

        fun parse(header: String?): SubUserinfo {
            if (header.isNullOrBlank()) return EMPTY
            var upload = 0L
            var download = 0L
            var total = 0L
            var expire = 0L
            for (part in header.split(";")) {
                val keyValue = part.split("=", limit = 2)
                if (keyValue.size != 2) continue
                val value = keyValue[1].trim().toLongOrNull() ?: continue
                when (keyValue[0].trim().lowercase()) {
                    "upload" -> upload = value
                    "download" -> download = value
                    "total" -> total = value
                    "expire" -> expire = value
                }
            }
            return SubUserinfo(upload + download, total, expire)
        }
    }
}
