package pw.x4.ninety.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/** JVM/native diagnostics retained locally for troubleshooting. */
object Diag {
    private fun crashFile(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
    fun stderrFile(ctx: Context) = File(ctx.filesDir, "box-stderr.log")
    private fun runFile(ctx: Context) = File(ctx.filesDir, "box-run.log")
    private fun debugFile(ctx: Context) = File(ctx.filesDir, "box-debug.log")
    private fun logcatFile(ctx: Context) = File(ctx.filesDir, "logcat.txt")
    fun xrayFile(ctx: Context) = File(ctx.filesDir, "xray.log")
    fun xrayConfigFile(ctx: Context) = File(ctx.filesDir, "xray-config.json")

    fun xrayLog(ctx: Context): String? {
        val txt = xrayFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > DISPLAY_LIMIT) {
            "…(начало обрезано — жми «Скопировать»)\n" + txt.takeLast(DISPLAY_LIMIT)
        } else {
            txt
        }
    }

    fun snapshotLogcat(ctx: Context) {
        val app = ctx.applicationContext
        Thread({
            try {
                val proc = ProcessBuilder("logcat", "-d", "-v", "time", "-t", "5000")
                    .redirectErrorStream(true)
                    .start()
                val txt = proc.inputStream.bufferedReader().readText()
                try { proc.waitFor() } catch (_: Throwable) {}
                val keep = txt.lineSequence().filter { line ->
                    line.contains("ninety", true) || line.contains("libbox", true) ||
                        line.contains("xray", true) || line.contains("GoLog", true) ||
                        line.contains("Go ", false) || line.contains("DEBUG") ||
                        line.contains("SIGSEGV") || line.contains("SIGABRT") ||
                        line.contains("signal ") || line.contains("fatal", true) ||
                        line.contains("panic", true) || line.contains("Abort message") ||
                        line.contains("AndroidRuntime") || line.contains("art::", false) ||
                        line.contains("backtrace", true) || line.contains("tombstone", true) ||
                        line.contains("#0") || line.contains("#01") ||
                        line.contains("DalvikVM") || line.contains("System.err")
                }.joinToString("\n")
                val out = keep.ifBlank { txt.takeLast(20_000) }
                if (out.isNotBlank()) logcatFile(app).writeText(out)
            } catch (_: Throwable) {
                // Diagnostics must never affect app startup.
            }
        }, "ninety-logcat-snap").start()
    }

    fun logcat(ctx: Context): String? {
        val txt = logcatFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > DISPLAY_LIMIT) {
            "…(начало обрезано — жми «Скопировать»)\n" + txt.takeLast(DISPLAY_LIMIT)
        } else {
            txt
        }
    }

    fun runLogPath(ctx: Context): String = runFile(ctx).absolutePath

    private var dbgWriter: java.io.Writer? = null

    @Synchronized
    fun startRunLog(ctx: Context) {
        try { runFile(ctx).writeText("") } catch (_: Throwable) {}
        try { dbgWriter?.close() } catch (_: Throwable) {}
        dbgWriter = try { debugFile(ctx).bufferedWriter() } catch (_: Throwable) { null }
    }

    @Synchronized
    fun appendRunLog(line: String?) {
        val writer = dbgWriter ?: return
        try {
            writer.write(line ?: return)
            writer.write("\n")
            writer.flush()
        } catch (_: Throwable) {
            // Logging failures are non-fatal.
        }
    }

    @Synchronized
    fun stopRunLog() {
        try {
            dbgWriter?.flush()
            dbgWriter?.close()
        } catch (_: Throwable) {
        }
        dbgWriter = null
    }

    fun installCrashHandler(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                crashFile(app).writeText("${Date()}\nthread=${thread.name}\n\n$writer")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun writeCrash(ctx: Context, label: String, error: Throwable) {
        try {
            val writer = StringWriter()
            error.printStackTrace(PrintWriter(writer))
            crashFile(ctx).writeText("${Date()}\n$label\n\n$writer")
        } catch (_: Throwable) {
        }
    }

    fun lastCrash(ctx: Context): String? =
        crashFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun boxStderr(ctx: Context): String? {
        val txt = stderrFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } ?: return null
        return if (txt.length > DISPLAY_LIMIT) {
            txt.take(DISPLAY_LIMIT) + "\n…(обрезано — жми «Скопировать» для полного)"
        } else {
            txt
        }
    }

    fun boxRun(ctx: Context): String? {
        val primary = runFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
        val txt = primary
            ?: debugFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
            ?: return null
        return if (txt.length > DISPLAY_LIMIT) {
            "…(начало — жми «Скопировать»)\n" + txt.takeLast(DISPLAY_LIMIT)
        } else {
            txt
        }
    }

    /** Exported diagnostics are bounded and redacted before reaching clipboard/share targets. */
    fun fullReport(ctx: Context): String {
        val report = buildString {
            append("== privacy ==\nCredentials, share links and URL query values are automatically redacted.\n\n")
            lastCrash(ctx)?.let { append("== last_crash ==\n").append(it).append("\n\n") }
            appendFileSection("logcat snapshot", logcatFile(ctx))
            appendFileSection("xray-config.json", xrayConfigFile(ctx))
            appendFileSection("xray.log", xrayFile(ctx))
            appendFileSection("box stderr", stderrFile(ctx))
            appendFileSection("box run (log.output)", runFile(ctx))
            appendFileSection("box debug (platform)", debugFile(ctx))
        }.ifBlank { "(пусто)" }
        val bounded = if (report.length > REPORT_LIMIT) {
            "…(отчёт ограничен последними $REPORT_LIMIT символами)\n" + report.takeLast(REPORT_LIMIT)
        } else {
            report
        }
        return SecretRedactor.redact(bounded)
    }

    private fun StringBuilder.appendFileSection(label: String, file: File) {
        if (file.exists() && file.length() > 0) {
            append("== ").append(label).append(" ==\n")
                .append(file.readText())
                .append("\n\n")
        }
    }

    fun writeReportFile(ctx: Context): File {
        val directory = File(ctx.cacheDir, "diagnostics").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(Date())
        return File(directory, "ninety-diag-$stamp.txt").apply {
            writeText(fullReport(ctx))
        }
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
        File(ctx.cacheDir, "diagnostics").deleteRecursively()
    }

    private const val DISPLAY_LIMIT = 8_000
    private const val REPORT_LIMIT = 1_000_000
}
