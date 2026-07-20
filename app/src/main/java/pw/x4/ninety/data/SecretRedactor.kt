package pw.x4.ninety.data

/** Best-effort redaction before diagnostics leave the app. */
internal object SecretRedactor {
    private val shareLink = Regex(
        pattern = "(?i)\\b(vless|vmess|trojan|ss|hysteria2|hy2|tuic)://[^\\s\\\"']+",
    )
    private val bearer = Regex(
        pattern = "(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\\\"']+",
    )
    private val jsonSecret = Regex(
        pattern = "(?i)(\\\"(?:password|token|access_token|private_key|license|uuid|authorization)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")",
    )
    private val assignmentSecret = Regex(
        pattern = "(?i)\\b(password|token|access[_-]?token|private[_-]?key|license|uuid)\\s*[=:]\\s*[^\\s,;]+",
    )
    private val uuid = Regex(
        pattern = "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b",
    )
    private val sensitiveQuery = Regex(
        pattern = "(?i)(https://[^\\s?#]+(?:/[^\\s?#]*)?\\?)[^\\s#]+",
    )

    fun redact(input: String): String = input
        .replace(shareLink) { match -> "${match.groupValues[1].lowercase()}://<redacted>" }
        .replace(bearer, "$1<redacted>")
        .replace(jsonSecret, "$1<redacted>$2")
        .replace(assignmentSecret) { match ->
            val key = match.groupValues[1]
            "$key=<redacted>"
        }
        .replace(uuid, "<uuid-redacted>")
        .replace(sensitiveQuery, "$1<query-redacted>")
}
