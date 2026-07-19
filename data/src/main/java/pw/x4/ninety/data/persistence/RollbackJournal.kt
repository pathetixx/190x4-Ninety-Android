package pw.x4.ninety.data.persistence

import java.security.MessageDigest

/**
 * Commit marker for the two legacy JSON files.
 *
 * The files are replaced atomically one by one and this manifest is written last. A process death
 * between those writes leaves a hash mismatch, so the incomplete journal is never allowed to
 * replace a valid Room graph on the next launch.
 */
object RollbackJournal {
    private const val VERSION = "1"

    fun create(nodesJson: String, profilesJson: String): String = buildString {
        appendLine("version=$VERSION")
        appendLine("nodes=${sha256(nodesJson)}")
        appendLine("profiles=${sha256(profilesJson)}")
    }

    fun validates(manifest: String, nodesJson: String, profilesJson: String): Boolean {
        val values = manifest.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        return values["version"] == VERSION &&
            values["nodes"] == sha256(nodesJson) &&
            values["profiles"] == sha256(profilesJson)
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
