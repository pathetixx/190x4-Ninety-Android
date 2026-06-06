package pw.x4.ninety.data

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Импорт узлов из буфера: одиночная ссылка, URL подписки (скачать → распарсить),
 * либо сырое содержимое подписки (base64/plain). Сетевые запросы — в фоне.
 */
object Importer {
    private val main = Handler(Looper.getMainLooper())
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** cb(добавлено, ошибка|null, идётЗагрузка). Зовётся на main-потоке. */
    fun importText(
        raw: String,
        onLoading: () -> Unit,
        onDone: (added: Int, error: String?) -> Unit,
    ) {
        val text = raw.trim()
        if (text.isEmpty()) { onDone(0, "Буфер пуст"); return }

        // одиночная прокси-ссылка
        if (LinkParser.parseLink(text) != null) {
            val ok = Store.addLink(text)
            onDone(if (ok) 1 else 0, if (ok) null else "Узел уже есть")
            return
        }

        // URL подписки — скачиваем
        if (text.startsWith("http://") || text.startsWith("https://")) {
            onLoading()
            Thread({
                try {
                    val body = fetch(text)
                    main.post { onDone(Store.addSubscription(body), null) }
                } catch (e: Exception) {
                    main.post { onDone(0, "Не загрузить подписку: ${e.message}") }
                }
            }, "ninety-sub-fetch").start()
            return
        }

        // сырое содержимое (base64/plain список ссылок)
        val n = Store.addSubscription(text)
        onDone(n, if (n == 0) "Не распознано (ссылка/подписка)" else null)
    }

    private fun fetch(url: String): String {
        val req = Request.Builder()
            .url(url)
            // панели отдают base64-список ссылок для клиентских UA; без него — HTML/YAML
            .header("User-Agent", "v2rayNG/1.9.5")
            .header("Accept", "*/*")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string()
            if (body.isNullOrBlank()) throw IOException("пустой ответ")
            return body
        }
    }
}
