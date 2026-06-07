package pw.x4.ninety.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pw.x4.ninety.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OTA: проверка новой версии через GitHub Releases API и установка APK
 * (как Windows-Ninety). releases/latest игнорирует pre-release (test-m2),
 * поэтому до выхода v0.1.0 вернёт «релизов нет» — это норма.
 *
 * ⚠️ Для install-over подпись APK должна совпадать с установленной — нужен
 * стабильный keystore в CI (иначе «Приложение не установлено»).
 */
object Updater {
    private const val API = "https://api.github.com/repos/pathetixx/190x4-Ninety-Android/releases/latest"

    private val main = Handler(Looper.getMainLooper())
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class Release(val version: String, val apkUrl: String, val notes: String)

    /**
     * Найденный при старте релиз для показа OTA-модалки (как окно апдейта в Windows-Ninety).
     * MainActivity заполняет при автопроверке (с учётом «Позже»), NinetyApp показывает модалку.
     */
    object Available {
        var release by mutableStateOf<Release?>(null)
    }

    /** Проверка обновления. onDone(релиз-если-новее|null, error|null) на main-потоке. */
    fun check(onLoading: () -> Unit, onDone: (newer: Release?, error: String?) -> Unit) {
        onLoading()
        Thread({
            try {
                val rel = fetchLatest()
                val newer = if (isNewer(rel.version, BuildConfig.VERSION_NAME)) rel else null
                main.post { onDone(newer, null) }
            } catch (e: Exception) {
                main.post { onDone(null, e.message ?: "Ошибка проверки") }
            }
        }, "ninety-ota-check").start()
    }

    /** Скачать APK и запустить системный установщик. onProgress(0..100). */
    fun downloadAndInstall(
        context: Context,
        rel: Release,
        onProgress: (Int) -> Unit,
        onDone: (error: String?) -> Unit,
    ) {
        val app = context.applicationContext
        Thread({
            try {
                val file = File(app.cacheDir, "ninety-update.apk")
                download(rel.apkUrl, file, onProgress)
                main.post { install(app, file); onDone(null) }
            } catch (e: Exception) {
                main.post { onDone(e.message ?: "Ошибка загрузки") }
            }
        }, "ninety-ota-dl").start()
    }

    private fun fetchLatest(): Release {
        val req = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Ninety-Android")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) throw IOException("Релизов пока нет")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("пустой ответ")
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank()) throw IOException("нет тега релиза")
            val assets = json.optJSONArray("assets") ?: throw IOException("нет файлов")
            val apks = ArrayList<String>()
            for (i in 0 until assets.length()) {
                val url = assets.getJSONObject(i).optString("browser_download_url")
                if (url.endsWith(".apk")) apks.add(url)
            }
            if (apks.isEmpty()) throw IOException("в релизе нет APK")
            return Release(tag, pickAbi(apks), json.optString("body"))
        }
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val req = Request.Builder().url(url).header("User-Agent", "Ninety-Android").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("пустой ответ")
            val total = body.contentLength()
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val p = (read * 100 / total).toInt()
                            if (p != last) { last = p; main.post { onProgress(p) } }
                        }
                    }
                }
            }
        }
    }

    private fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Выбрать APK под ABI устройства (релиз — per-ABI: …-arm64-v8a.apk / …-armeabi-v7a.apk). */
    private fun pickAbi(apks: List<String>): String {
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            apks.firstOrNull { it.contains(abi) }?.let { return it }
        }
        return apks.first()
    }

    /** Семвер-сравнение a > b (по числовым компонентам). */
    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
