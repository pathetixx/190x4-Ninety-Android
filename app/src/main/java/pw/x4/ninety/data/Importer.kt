package pw.x4.ninety.data

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Импорт профилей: одиночная ссылка → single-конфиг; URL подписки → скачать
 * (с userinfo-заголовком) → профиль-подписка; сырое содержимое → профиль без URL.
 * Сетевые запросы — в фоне, колбэки на main.
 */
object Importer {
    private val main = Handler(Looper.getMainLooper())
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** cb(добавлено, ошибка|null). Зовётся на main-потоке. */
    fun importText(
        raw: String,
        onLoading: () -> Unit,
        onDone: (added: Int, error: String?) -> Unit,
    ) {
        val text = raw.trim()
        if (text.isEmpty()) { onDone(0, "Буфер пуст"); return }

        // одиночная прокси-ссылка → single-профиль
        if (LinkParser.parseLink(text) != null) {
            val ok = Store.addSingleConfig(text)
            onDone(if (ok) 1 else 0, if (ok) null else "Конфиг уже есть")
            return
        }

        // URL подписки → скачать → профиль-подписка
        if (text.startsWith("http://") || text.startsWith("https://")) {
            onLoading()
            Thread({
                try {
                    val (body, info) = fetch(text)
                    val added = Store.addSubscriptionProfile(text, body, info)
                    main.post { onDone(added, null) }
                } catch (e: Exception) {
                    main.post { onDone(0, "Не загрузить подписку: ${e.message}") }
                }
            }, "ninety-sub-fetch").start()
            return
        }

        // сырое содержимое (base64/plain список ссылок) → профиль без URL
        try {
            val n = Store.addSubscriptionProfile("", text, SubUserinfo.EMPTY, name = "Импорт")
            onDone(n, null)
        } catch (e: Exception) {
            onDone(0, "Не распознано (ссылка/подписка)")
        }
    }

    /** Обновить ноды профиля-подписки по его URL. cb на main-потоке. */
    fun refresh(
        profileId: String,
        onLoading: () -> Unit,
        onDone: (count: Int, error: String?) -> Unit,
    ) {
        val p = Store.profiles.firstOrNull { it.id == profileId }
        if (p == null || !p.isSub || p.url.isBlank()) {
            onDone(0, "У профиля нет URL подписки"); return
        }
        onLoading()
        Thread({
            try {
                val (body, info) = fetch(p.url)
                val count = Store.refreshProfileNodes(profileId, body, info)
                main.post { onDone(count, null) }
            } catch (e: Exception) {
                main.post { onDone(0, "Не обновить подписку: ${e.message}") }
            }
        }, "ninety-sub-refresh").start()
    }

    /** Обновить все профили-подписки последовательно. */
    fun refreshAll(onLoading: () -> Unit, onDone: (count: Int, error: String?) -> Unit) {
        val subs = Store.profiles.filter { it.isSub && it.url.isNotBlank() }
        if (subs.isEmpty()) { onDone(0, "Нет подписок для обновления"); return }
        onLoading()
        Thread({
            var total = 0; var err: String? = null
            for (p in subs) {
                try {
                    val (body, info) = fetch(p.url)
                    total += Store.refreshProfileNodes(p.id, body, info)
                } catch (e: Exception) { err = "Часть подписок не обновилась" }
            }
            val t = total; val e = err
            main.post { onDone(t, e) }
        }, "ninety-sub-refresh-all").start()
    }

    private fun fetch(url: String): Pair<String, SubUserinfo> {
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
            val info = SubUserinfo.parse(resp.header("Subscription-Userinfo"))
            return body to info
        }
    }
}
