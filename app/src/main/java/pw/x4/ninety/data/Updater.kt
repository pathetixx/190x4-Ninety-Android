package pw.x4.ninety.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pw.x4.ninety.BuildConfig

/** GitHub-release OTA with ABI selection, bounded downloads and mandatory SHA-256 verification. */
object Updater {
    private const val API = "https://api.github.com/repos/pathetixx/190x4-Ninety-Android/releases/latest"

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ninety-ota")
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    data class Release(
        val version: String,
        val apkUrl: String,
        val checksumUrl: String,
        val apkSize: Long,
        val notes: String,
    )

    object Available {
        var release by mutableStateOf<Release?>(null)
    }

    fun check(onLoading: () -> Unit, onDone: (newer: Release?, error: String?) -> Unit) {
        onLoading()
        worker.execute {
            runCatching { fetchLatest() }
                .onSuccess { release ->
                    val newer = release.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
                    main.post { onDone(newer, null) }
                }
                .onFailure { error ->
                    main.post { onDone(null, error.message ?: "Ошибка проверки") }
                }
        }
    }

    fun downloadAndInstall(
        context: Context,
        rel: Release,
        onProgress: (Int) -> Unit,
        onDone: (error: String?) -> Unit,
    ) {
        val app = context.applicationContext
        worker.execute {
            val directory = File(app.cacheDir, "updates").apply { mkdirs() }
            val destination = File(directory, "ninety-${rel.version}.apk")
            val partial = File(directory, destination.name + ".part")
            runCatching {
                destination.delete()
                partial.delete()
                download(rel.apkUrl, partial, rel.apkSize, onProgress)
                val expected = fetchChecksum(rel.checksumUrl)
                val actual = sha256(partial)
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw IOException("SHA-256 обновления не совпадает")
                }
                if (!partial.renameTo(destination)) {
                    partial.copyTo(destination, overwrite = true)
                    partial.delete()
                }
                main.post {
                    runCatching { install(app, destination) }
                        .onSuccess { onDone(null) }
                        .onFailure { onDone(it.message ?: "Не удалось открыть установщик") }
                }
            }.onFailure { error ->
                partial.delete()
                destination.delete()
                main.post { onDone(error.message ?: "Ошибка загрузки") }
            }
        }
    }

    private fun fetchLatest(): Release {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Ninety-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw IOException("Релизов пока нет")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = readBounded(response.body, MAX_API_BYTES, "ответ GitHub API")
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank()) throw IOException("нет тега релиза")
            val assetsJson = json.optJSONArray("assets") ?: throw IOException("нет файлов")
            val assets = buildList {
                for (index in 0 until assetsJson.length()) {
                    val asset = assetsJson.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    val size = asset.optLong("size", -1)
                    if (name.isNotBlank() && url.startsWith("https://")) {
                        add(ReleaseAsset(name, url, size))
                    }
                }
            }
            val apk = pickAbi(assets.filter { it.name.endsWith(".apk") })
            if (apk.size !in 1..MAX_APK_BYTES) throw IOException("некорректный размер APK")
            val checksum = assets.firstOrNull { it.name == "${apk.name}.sha256" }
                ?: throw IOException("релиз не содержит SHA-256 для ${apk.name}")
            return Release(
                version = tag,
                apkUrl = apk.url,
                checksumUrl = checksum.url,
                apkSize = apk.size,
                notes = json.optString("body"),
            )
        }
    }

    private fun download(
        url: String,
        destination: File,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ) {
        val request = Request.Builder().url(url).header("User-Agent", "Ninety-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("пустой ответ")
            val declared = body.contentLength()
            if (declared > MAX_APK_BYTES || expectedSize > MAX_APK_BYTES) {
                throw IOException("APK превышает допустимый размер")
            }
            if (declared > 0 && expectedSize > 0 && declared != expectedSize) {
                throw IOException("размер APK не совпадает с метаданными релиза")
            }
            val total = declared.takeIf { it > 0 } ?: expectedSize
            FileOutputStream(destination).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastProgress = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        if (read > MAX_APK_BYTES) throw IOException("APK превышает допустимый размер")
                        output.write(buffer, 0, count)
                        if (total > 0) {
                            val progress = (read * 100 / total).coerceIn(0, 100).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                main.post { onProgress(progress) }
                            }
                        }
                    }
                    if (expectedSize > 0 && read != expectedSize) {
                        throw IOException("APK загружен не полностью")
                    }
                }
                output.fd.sync()
            }
        }
    }

    private fun fetchChecksum(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Ninety-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("checksum HTTP ${response.code}")
            val value = readBounded(response.body, MAX_CHECKSUM_BYTES, "checksum").trim()
            return Regex("^[0-9a-fA-F]{64}").find(value)?.value
                ?: throw IOException("некорректный SHA-256")
        }
    }

    private fun readBounded(body: okhttp3.ResponseBody?, limit: Long, label: String): String {
        val responseBody = body ?: throw IOException("$label отсутствует")
        if (responseBody.contentLength() > limit) throw IOException("$label слишком большой")
        val bytes = responseBody.source().readByteArray(limit + 1)
        if (bytes.size.toLong() > limit) throw IOException("$label слишком большой")
        return bytes.toString(Charsets.UTF_8)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun pickAbi(apks: List<ReleaseAsset>): ReleaseAsset {
        for (abi in Build.SUPPORTED_ABIS) {
            apks.firstOrNull { asset -> asset.name.contains("-$abi.apk") }?.let { return it }
        }
        throw IOException("в релизе нет APK для ABI ${Build.SUPPORTED_ABIS.joinToString()}")
    }

    internal fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val candidatePart = left.getOrElse(index) { 0 }
            val currentPart = right.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }
        return false
    }

    private data class ReleaseAsset(val name: String, val url: String, val size: Long)

    private const val MAX_API_BYTES = 2L * 1024 * 1024
    private const val MAX_CHECKSUM_BYTES = 4L * 1024
    private const val MAX_APK_BYTES = 250L * 1024 * 1024
}
