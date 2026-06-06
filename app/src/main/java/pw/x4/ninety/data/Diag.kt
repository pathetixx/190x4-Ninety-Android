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
    private fun runFile(ctx: Context) = File(ctx.filesDir, "box-run.log")
    private fun debugFile(ctx: Context) = File(ctx.filesDir, "box-debug.log")

    /** Путь для log.output sing-box — ядро пишет лог сюда САМО, минуя platform-callback
     *  (writeDebugMessage в этой libbox обычные логи не отдаёт → раньше «логов нет»). */
    fun runLogPath(ctx: Context): String = runFile(ctx).absolutePath

    // ── platform writeDebugMessage (подстраховка, если канал всё же жив) ──
    private var dbgWriter: java.io.Writer? = null

    /** Старт сессии: чистим оба файла (причина в начале, как redirectStderr). */
    @Synchronized fun startRunLog(ctx: Context) {
        try { runFile(ctx).writeText("") } catch (_: Throwable) {}   // log.output допишет сам
        try { dbgWriter?.close() } catch (_: Throwable) {}
        dbgWriter = try { debugFile(ctx).bufferedWriter() } catch (_: Throwable) { null }
    }

    @Synchronized fun appendRunLog(line: String?) {
        val w = dbgWriter ?: return
        try { w.write(line ?: return); w.write("\n"); w.flush() } catch (_: Throwable) {}
    }

    @Synchronized fun stopRunLog() {
        try { dbgWriter?.flush(); dbgWriter?.close() } catch (_: Throwable) {}
        dbgWriter = null
    }

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

    /** Голова stderr — там panic/[signal] + аварийная горутина [running] (redirectStderr
     *  перезатирает файл каждый запуск, причина в начале). Для дисплея, усечён. */
    fun boxStderr(ctx: Context): String? {
        val txt = stderrFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > 8000) txt.take(8000) + "\n…(обрезано — жми «Скопировать» для полного)" else txt
    }

    /** Хвост лога ядра для дисплея — последние события важнее (в отличие от stderr-паники).
     *  Основной источник — log.output (box-run.log), fallback — platform-канал. */
    fun boxRun(ctx: Context): String? {
        val main = runFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
        val txt = main ?: debugFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > 8000) "…(начало — жми «Скопировать»)\n" + txt.takeLast(8000) else txt
    }

    /** Полный отчёт для буфера обмена (без усечения). */
    fun fullReport(ctx: Context): String {
        val sb = StringBuilder()
        lastCrash(ctx)?.let { sb.append("== last_crash ==\n").append(it).append("\n\n") }
        stderrFile(ctx).takeIf { it.exists() }?.let { sb.append("== box stderr ==\n").append(it.readText()).append("\n\n") }
        runFile(ctx).takeIf { it.exists() }?.let { sb.append("== box run (log.output) ==\n").append(it.readText()).append("\n\n") }
        debugFile(ctx).takeIf { it.exists() && it.length() > 0 }?.let { sb.append("== box debug (platform) ==\n").append(it.readText()) }
        return sb.toString().ifBlank { "(пусто)" }
    }

    fun clear(ctx: Context) {
        crashFile(ctx).delete()
        stderrFile(ctx).delete()
        stderrFile(ctx).resolveSibling("box-stderr.log.old").delete()
        runFile(ctx).delete()
        debugFile(ctx).delete()
    }
}
