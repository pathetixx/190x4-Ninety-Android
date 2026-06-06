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

    // ── живой лог ядра (writeDebugMessage от libbox) ───────────
    private const val RUN_LOG_CAP = 256 * 1024 // защита от роста: режем хвост при переполнении
    private var runWriter: java.io.Writer? = null
    private var runFileRef: File? = null

    /** Старт сессии лога: пересоздать файл (как redirectStderr — причина в начале). */
    @Synchronized fun startRunLog(ctx: Context) {
        try { runWriter?.close() } catch (_: Throwable) {}
        val f = runFile(ctx)
        runFileRef = f
        runWriter = try { f.bufferedWriter().also { it.write("== box run ${Date()} ==\n") } } catch (_: Throwable) { null }
    }

    /** Строка лога ядра. Без аллокаций сверх необходимого; режем файл при переполнении. */
    @Synchronized fun appendRunLog(line: String?) {
        val w = runWriter ?: return
        val f = runFileRef ?: return
        try {
            w.write(line ?: return); w.write("\n"); w.flush()
            if (f.length() > RUN_LOG_CAP) {
                w.close()
                val tail = f.readText().takeLast(RUN_LOG_CAP / 2)
                f.writeText("…(начало обрезано)\n$tail")
                runWriter = java.io.FileWriter(f, true).buffered() // append-режим дальше
            }
        } catch (_: Throwable) {}
    }

    @Synchronized fun stopRunLog() {
        try { runWriter?.flush(); runWriter?.close() } catch (_: Throwable) {}
        runWriter = null
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

    /** Хвост лога ядра для дисплея — последние события важнее (в отличие от stderr-паники). */
    fun boxRun(ctx: Context): String? {
        val txt = runFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > 8000) "…(начало — жми «Скопировать»)\n" + txt.takeLast(8000) else txt
    }

    /** Полный отчёт для буфера обмена (без усечения). */
    fun fullReport(ctx: Context): String {
        val sb = StringBuilder()
        lastCrash(ctx)?.let { sb.append("== last_crash ==\n").append(it).append("\n\n") }
        stderrFile(ctx).takeIf { it.exists() }?.let { sb.append("== box stderr ==\n").append(it.readText()).append("\n\n") }
        runFile(ctx).takeIf { it.exists() }?.let { sb.append("== box run ==\n").append(it.readText()) }
        return sb.toString().ifBlank { "(пусто)" }
    }

    fun clear(ctx: Context) {
        crashFile(ctx).delete()
        stderrFile(ctx).delete()
        stderrFile(ctx).resolveSibling("box-stderr.log.old").delete()
        runFile(ctx).delete()
    }
}
