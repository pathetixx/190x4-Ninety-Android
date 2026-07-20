package pw.x4.ninety.core.model

/**
 * Выбор выхода, независимый от Android UI и хранилища.
 *
 * Раньше `"auto"` хранился в том же поле, что и id ноды, поэтому проверки через
 * `activeNode() != null` ошибочно считали Auto отсутствующим выбором. Тип делает
 * это состояние явным и не позволяет создать ноду с зарезервированным id.
 */
sealed interface ProxySelection {
    val persistedValue: String

    data object Auto : ProxySelection {
        override val persistedValue: String = AUTO_ID
    }

    data class Node(val nodeId: String) : ProxySelection {
        init {
            require(nodeId.isNotBlank()) { "nodeId must not be blank" }
            require(nodeId != AUTO_ID) { "'$AUTO_ID' is reserved for automatic selection" }
        }

        override val persistedValue: String = nodeId
    }

    companion object {
        const val AUTO_ID: String = "auto"

        fun fromPersisted(value: String?): ProxySelection? = when {
            value.isNullOrBlank() -> null
            value == AUTO_ID -> Auto
            else -> Node(value)
        }
    }
}
