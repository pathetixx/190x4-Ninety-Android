package pw.x4.ninety.data

import java.security.MessageDigest

/** Collision-resistant identifiers shared by profiles and profile-scoped node instances. */
internal object StableId {
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun node(profileId: String, fingerprint: String): String =
        sha256("ninety-node-v2\u0000$profileId\u0000$fingerprint")

    fun rawProfile(content: String): String =
        "sub:raw:" + sha256(content.trim()).take(32)
}
