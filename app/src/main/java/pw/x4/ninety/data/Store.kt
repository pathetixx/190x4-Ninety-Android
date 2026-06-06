package pw.x4.ninety.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File

/**
 * Хранилище нод: список (наблюдаемый Compose) + активная нода. Persist в
 * filesDir/nodes.json, активный id — в Prefs. M2: одна плоская коллекция нод
 * (подписки как группы — позже).
 */
object Store {
    private lateinit var file: File
    private lateinit var prefs: Prefs

    val nodes = mutableStateListOf<Node>()
    var activeId by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "nodes.json")
        prefs = Prefs.get(context)
        load()
        activeId = prefs.activeNodeId
    }

    fun activeNode(): Node? = nodes.firstOrNull { it.id == activeId }

    fun setActive(id: String) {
        activeId = id
        prefs.activeNodeId = id
    }

    /** Добавить из одной ссылки. Возвращает true если добавлено. */
    fun addLink(raw: String): Boolean {
        val node = LinkParser.parseLink(raw) ?: return false
        addAll(listOf(node))
        return true
    }

    /** Добавить из контента подписки. Возвращает число добавленных. */
    fun addSubscription(content: String): Int {
        val parsed = LinkParser.parseSubscription(content)
        return addAll(parsed)
    }

    private fun addAll(list: List<Node>): Int {
        var added = 0
        for (n in list) {
            if (nodes.none { it.id == n.id }) {
                nodes.add(n)
                added++
            }
        }
        if (added > 0) {
            if (activeId == null) setActive(nodes.first().id)
            save()
        }
        return added
    }

    fun remove(id: String) {
        nodes.removeAll { it.id == id }
        if (activeId == id) {
            activeId = nodes.firstOrNull()?.id
            prefs.activeNodeId = activeId
        }
        save()
    }

    fun clear() {
        nodes.clear()
        activeId = null
        prefs.activeNodeId = null
        save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            nodes.clear()
            for (i in 0 until arr.length()) {
                nodes.add(Node.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            nodes.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
