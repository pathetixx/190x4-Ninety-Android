package pw.x4.ninety.data

import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Импорт профилей: одиночная ссылка → single-конфиг; HTTPS-подписка → загрузка;
 * сырое содержимое → локальный профиль. Сеть работает последовательно в фоне,
 * а все чтения и изменения Compose Store выполняются только на main thread.
 */
object Importer {
    private val main = Handler(Looper.getMainLooper())
    private val network = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ninety-subscriptions")
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** cb(добавлено, ошибка|null). Всегда вызывается на main thread. */
    fun importText(
        raw: String,
        name: String? = null,
        onLoading: () -> Unit,
        onDone: (added: Int, error: String?) -> Unit,
    ) {
        if (!isMainThread()) {
            main.post { importText(raw, name, onLoading, onDone) }
            return
        }
        val text = raw.trim()
        val requestedName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (text.isEmpty()) {
            onDone(0, "Буфер пуст")
            return
        }
        if (text.toByteArray(Charsets.UTF_8).size > MAX_SUBSCRIPTION_BYTES) {
            onDone(0, "Подписка больше ${MAX_SUBSCRIPTION_BYTES / 1024 / 1024} МБ")
            return
        }

        if (LinkParser.parseLink(text) != null) {
            val ok = Store.addSingleConfig(text)
            onDone(if (ok) 1 else 0, if (ok) null else "Конфиг уже есть")
            return
        }

        if (text.startsWith("http://", ignoreCase = true)) {
            onDone(0, "Небезопасные HTTP-подписки запрещены. Используйте HTTPS")
            return
        }
        if (text.startsWith("https://", ignoreCase = true)) {
            onLoading()
            network.execute {
                runCatching { fetch(text) }
                    .onSuccess { (body, info) ->
                        main.post {
                            runCatching {
                                Store.addSubscriptionProfile(text, body, info, name = requestedName)
                            }.onSuccess { added -> onDone(added, null) }
                                .onFailure { error -> onDone(0, importError("Не импортировать подписку", error)) }
                        }
                    }
                    .onFailure { error ->
                        main.post { onDone(0, importError("Не загрузить подписку", error)) }
                    }
            }
            return
        }

        runCatching {
            Store.addSubscriptionProfile(
                url = "",
                content = text,
                info = SubUserinfo.EMPTY,
                name = requestedName ?: "Импорт",
            )
        }.onSuccess { added -> onDone(added, null) }
            .onFailure { onDone(0, "Не распознано (ссылка/подписка)") }
    }

    fun refresh(
        profileId: String,
        onLoading: () -> Unit,
        onDone: (count: Int, error: String?) -> Unit,
    ) {
        if (!isMainThread()) {
            main.post { refresh(profileId, onLoading, onDone) }
            return
        }
        val profile = Store.profiles.firstOrNull { it.id == profileId }
        if (profile == null || !profile.isSub || profile.url.isBlank()) {
            onDone(0, "У профиля нет URL подписки")
            return
        }
        if (!profile.url.startsWith("https://", ignoreCase = true)) {
            onDone(0, "Обновление HTTP-подписки заблокировано. Замените URL на HTTPS")
            return
        }
        onLoading()
        network.execute {
            runCatching { fetch(profile.url) }
                .onSuccess { (body, info) ->
                    main.post {
                        runCatching { Store.refreshProfileNodes(profileId, body, info) }
                            .onSuccess { count -> onDone(count, null) }
                            .onFailure { error -> onDone(0, importError("Не обновить подписку", error)) }
                    }
                }
                .onFailure { error ->
                    main.post { onDone(0, importError("Не обновить подписку", error)) }
                }
        }
    }

    /** Загружает все HTTPS-подписки последовательно, затем атомарно применяет их на main thread. */
    fun refreshAll(onLoading: () -> Unit, onDone: (count: Int, error: String?) -> Unit) {
        if (!isMainThread()) {
            main.post { refreshAll(onLoading, onDone) }
            return
        }
        val subscriptions = Store.profiles.filter { it.isSub && it.url.isNotBlank() }
        if (subscriptions.isEmpty()) {
            onDone(0, "Нет подписок для обновления")
            return
        }
        onLoading()
        network.execute {
            val fetched = mutableListOf<FetchedSubscription>()
            var failed = 0
            subscriptions.forEach { profile ->
                if (!profile.url.startsWith("https://", ignoreCase = true)) {
                    failed++
                } else {
                    runCatching { fetch(profile.url) }
                        .onSuccess { (body, info) -> fetched += FetchedSubscription(profile.id, body, info) }
                        .onFailure { failed++ }
                }
            }
            main.post {
                var total = 0
                fetched.forEach { item ->
                    runCatching { Store.refreshProfileNodes(item.profileId, item.body, item.info) }
                        .onSuccess { total += it }
                        .onFailure { failed++ }
                }
                onDone(total, if (failed > 0) "Часть подписок не обновилась" else null)
            }
        }
    }

    private fun fetch(url: String): Pair<String, SubUserinfo> {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw IOException("требуется HTTPS")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "v2rayNG/1.9.5")
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val responseBody = response.body ?: throw IOException("пустой ответ")
            if (responseBody.contentLength() > MAX_SUBSCRIPTION_BYTES) {
                throw IOException("ответ больше ${MAX_SUBSCRIPTION_BYTES / 1024 / 1024} МБ")
            }
            val bytes = responseBody.source().readByteArray(MAX_SUBSCRIPTION_BYTES + 1)
            if (bytes.size > MAX_SUBSCRIPTION_BYTES) {
                throw IOException("ответ больше ${MAX_SUBSCRIPTION_BYTES / 1024 / 1024} МБ")
            }
            val body = bytes.toString(Charsets.UTF_8)
            if (body.isBlank()) throw IOException("пустой ответ")
            return body to SubUserinfo.parse(response.header("Subscription-Userinfo"))
        }
    }

    private fun importError(prefix: String, error: Throwable): String =
        "$prefix: ${error.message?.take(160) ?: "неизвестная ошибка"}"

    private fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private data class FetchedSubscription(
        val profileId: String,
        val body: String,
        val info: SubUserinfo,
    )

    private const val MAX_SUBSCRIPTION_BYTES = 10L * 1024 * 1024
}
