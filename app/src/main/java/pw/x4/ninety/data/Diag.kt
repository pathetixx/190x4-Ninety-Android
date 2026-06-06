package pw.x4.ninety.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Диагностика: последний JVM-краш (uncaught handler) + нативный stderr ядра
 * (Libbox.redirectStderr ловит Go-паники). Показывается в Настройках — чтобы
 * видеть причину падения без adb/logcat.
 */
object Diag {
    private fun crashFile(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
    fun stderrFile(ctx: Context) = File(ctx.filesDir, "box-stderr.log")

    fun installCrashHandler(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                crashFile(app).writeText("${Date()}\nthread=${thread.name}\n\n$sw")
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(thread, e)
        }
    }

    fun writeCrash(ctx: Context, label: String, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            crashFile(ctx).writeText("${Date()}\n$label\n\n$sw")
        } catch (_: Throwable) {
        }
    }

    fun lastCrash(ctx: Context): String? =
        crashFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun boxStderr(ctx: Context): String? =
        stderrFile(ctx).takeIf { it.exists() }?.readText()?.takeLast(4000)?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) {
        crashFile(ctx).delete()
        stderrFile(ctx).delete()
    }
}
