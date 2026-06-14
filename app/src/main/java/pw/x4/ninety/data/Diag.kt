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
    private fun logcatFile(ctx: Context) = File(ctx.filesDir, "logcat.txt")
    // Legacy-файлы старого standalone-xray (xhttp теперь нативный в sing-box). Больше
    // не пишутся; функции оставлены — fullReport их просто не находит (no-op).
    fun xrayFile(ctx: Context) = File(ctx.filesDir, "xray.log")
    fun xrayConfigFile(ctx: Context) = File(ctx.filesDir, "xray-config.json")

    fun xrayLog(ctx: Context): String? {
        val txt = xrayFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > 8000) "…(начало обрезано — жми «Скопировать»)\n" + txt.takeLast(8000) else txt
    }

    /**
     * Снимок системного logcat при старте процесса. Нативный краш (SIGSEGV/abort в
     * Go-рантайме xray/libbox) НЕ идёт через redirectStderr и не пишется нашими файлами —
     * процесс умирает раньше. Но его трейс остаётся в кольцевом буфере logcat; после
     * перезапуска приложения `logcat -d` ещё видит строки прошлого падения (Go fatal,
     * goroutine-стек, "[signal SIGSEGV]", abort message — всё под нашим UID). Зовётся в
     * Application.onCreate, до любого старта VPN. Без READ_LOGS видны только свои записи —
     * достаточно для нашего краша. Тяжёлое — в отдельном потоке.
     */
    fun snapshotLogcat(ctx: Context) {
        val app = ctx.applicationContext
        Thread({
            try {
                val proc = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "5000")
                    .redirectErrorStream(true).start()
                val txt = proc.inputStream.bufferedReader().readText()
                try { proc.waitFor() } catch (_: Throwable) {}
                // фильтруем шум: оставляем строки с маркерами падения/нашего ядра/процесса
                val keep = txt.lineSequence().filter { l ->
                    l.contains("ninety", true) || l.contains("libbox", true) ||
                    l.contains("xray", true) || l.contains("GoLog", true) ||
                    l.contains("Go ", false) || l.contains("DEBUG") ||
                    l.contains("SIGSEGV") || l.contains("SIGABRT") || l.contains("signal ") ||
                    l.contains("fatal", true) || l.contains("panic", true) ||
                    l.contains("Abort message") || l.contains("AndroidRuntime") ||
                    l.contains("art::", false) || l.contains("backtrace", true) ||
                    l.contains("tombstone", true) || l.contains("#0") || l.contains("#01") ||
                    l.contains("DalvikVM") || l.contains("System.err")
                }.joinToString("\n")
                val out = keep.ifBlank { txt.takeLast(20000) } // если фильтр пуст — хвост сырого
                if (out.isNotBlank()) logcatFile(app).writeText(out)
            } catch (_: Throwable) {}
        }, "ninety-logcat-snap").start()
    }

    fun logcat(ctx: Context): String? {
        val txt = logcatFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > 8000) "…(начало обрезано — жми «Скопировать»)\n" + txt.takeLast(8000) else txt
    }

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
        logcatFile(ctx).takeIf { it.exists() && it.length() > 0 }?.let { sb.append("== logcat snapshot ==\n").append(it.readText()).append("\n\n") }
        xrayConfigFile(ctx).takeIf { it.exists() && it.length() > 0 }?.let { sb.append("== xray-config.json ==\n").append(it.readText()).append("\n\n") }
        xrayFile(ctx).takeIf { it.exists() && it.length() > 0 }?.let { sb.append("== xray.log ==\n").append(it.readText()).append("\n\n") }
        stderrFile(ctx).takeIf { it.exists() }?.let { sb.append("== box stderr ==\n").append(it.readText()).append("\n\n") }
        runFile(ctx).takeIf { it.exists() }?.let { sb.append("== box run (log.output) ==\n").append(it.readText()).append("\n\n") }
        debugFile(ctx).takeIf { it.exists() && it.length() > 0 }?.let { sb.append("== box debug (platform) ==\n").append(it.readText()) }
        return sb.toString().ifBlank { "(пусто)" }
    }

    /** Сохраняет fullReport в cacheDir как txt-файл (для шаринга через FileProvider) и
     *  возвращает его. Имя с датой-временем — несколько отчётов не перетирают друг друга. */
    fun writeReportFile(ctx: Context): File {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(Date())
        val f = File(ctx.cacheDir, "ninety-diag-$stamp.txt")
        f.writeText(fullReport(ctx))
        return f
    }

    fun clear(ctx: Context) {
        crashFile(ctx).delete()
        stderrFile(ctx).delete()
        stderrFile(ctx).resolveSibling("box-stderr.log.old").delete()
        runFile(ctx).delete()
        debugFile(ctx).delete()
        logcatFile(ctx).delete()
        xrayFile(ctx).delete()
        xrayConfigFile(ctx).delete()
    }
}
