package pw.x4.ninety.core.config

import pw.x4.ninety.core.model.ProxyNode

/** Stable persisted identity paired with a normalized proxy node. */
data class ConfigNode(
    val id: String,
    val proxy: ProxyNode,
) {
    init {
        require(id.isNotBlank()) { "config node id must not be blank" }
    }
}
