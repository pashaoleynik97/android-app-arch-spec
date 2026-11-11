package {{ROOT_PACKAGE}}.network.log

/**
 * Masks credentials in an HTTP log line **before** it reaches the Reporter (KERNEL-05): external reporters may
 * receive debug output, and a token in a breadcrumb is a token on a third party's server.
 *
 * Covers the example contract's secret-bearing JSON fields and the `Authorization` / `Cookie` headers. A value
 * keeps its first [VISIBLE_PREFIX] characters so two log lines can still be correlated ("is this the same
 * token?") without the value being usable. Add your backend's secret field names to [SECRET_FIELDS].
 *
 * Pure, so the masking contract is unit-tested.
 */
object HttpLogMasking {

    /** JSON field names whose string values are secrets. Matched case-insensitively. */
    val SECRET_FIELDS: Set<String> = setOf(
        "password",
        "accessToken",
        "refreshToken",
        "access_token",
        "refresh_token",
        "token",
        "secret",
        "otp",
    )

    private const val VISIBLE_PREFIX = 4
    private const val MASK = "…(masked)"

    private val jsonField = Regex(
        "\"(" + SECRET_FIELDS.joinToString("|") { Regex.escape(it) } + ")\"(\\s*:\\s*)\"([^\"]*)\"",
        RegexOption.IGNORE_CASE,
    )
    private val header = Regex("^((?:Authorization|Proxy-Authorization|Cookie|Set-Cookie):\\s*)(.+)$", RegexOption.IGNORE_CASE)

    fun mask(line: String): String {
        header.matchEntire(line)?.let { m -> return m.groupValues[1] + maskValue(m.groupValues[2]) }
        return jsonField.replace(line) { m -> "\"${m.groupValues[1]}\"${m.groupValues[2]}\"${maskValue(m.groupValues[3])}\"" }
    }

    private fun maskValue(value: String): String {
        // Keep a scheme word ("Bearer ") readable; mask what follows it.
        val scheme = value.substringBefore(' ', missingDelimiterValue = "")
        val hasScheme = scheme.isNotEmpty() && scheme.none { it == '=' || it == ';' }
        val secret = if (hasScheme) value.substringAfter(' ') else value
        val prefix = if (hasScheme) "$scheme " else ""
        return prefix + secret.take(VISIBLE_PREFIX) + MASK
    }
}
