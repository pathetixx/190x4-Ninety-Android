package pw.x4.ninety.data

import java.util.Locale

/** Форматтеры для UI (порт desktop formatBytes/formatRate/relativeTime). */
object Fmt {
    /** Объём трафика: Б / КиБ / МиБ / ГиБ. */
    fun bytes(n: Long): String = when {
        n < 1024 -> "$n Б"
        n < 1024L * 1024 -> String.format(Locale.US, "%.1f КиБ", n / 1024.0)
        n < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f МиБ", n / 1024.0 / 1024)
        else -> String.format(Locale.US, "%.2f ГиБ", n / 1024.0 / 1024 / 1024)
    }

    /** Скорость → (значение, единица): Б/с · КБ/с · МБ/с. */
    fun rate(bps: Long): Pair<String, String> = when {
        bps < 1024 -> bps.toString() to "Б/с"
        bps < 1024L * 1024 -> String.format(Locale.US, "%.1f", bps / 1024.0) to "КБ/с"
        else -> String.format(Locale.US, "%.2f", bps / 1024.0 / 1024) to "МБ/с"
    }

    /** Относительное время от ms-таймстампа: «только что / 5м / 2ч / 3д». */
    fun relTime(ms: Long): String {
        if (ms <= 0) return "—"
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000 -> "только что"
            diff < 3_600_000 -> "${diff / 60_000}м"
            diff < 86_400_000 -> "${diff / 3_600_000}ч"
            else -> "${diff / 86_400_000}д"
        }
    }
}
