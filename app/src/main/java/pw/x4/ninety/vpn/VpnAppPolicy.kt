package pw.x4.ninety.vpn

/** Android VpnService permits either an allowlist or a denylist, never both. */
internal data class VpnAppPolicy(
    val allowed: Set<String> = emptySet(),
    val disallowed: Set<String> = emptySet(),
) {
    init {
        require(allowed.isEmpty() || disallowed.isEmpty()) {
            "VPN allowlist and denylist are mutually exclusive"
        }
    }
}

internal fun resolveVpnAppPolicy(
    included: Collection<String>,
    excluded: Collection<String>,
    ownPackage: String,
): VpnAppPolicy {
    val cleanIncluded = included.map(String::trim).filter(String::isNotEmpty).toSet() - ownPackage
    return if (cleanIncluded.isNotEmpty()) {
        VpnAppPolicy(allowed = cleanIncluded)
    } else {
        VpnAppPolicy(
            disallowed = (excluded.map(String::trim).filter(String::isNotEmpty).toSet() + ownPackage),
        )
    }
}
