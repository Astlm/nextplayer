package dev.anilbeesetti.nextplayer.feature.player.utils

import java.security.MessageDigest
import java.util.Locale

internal object ExternalRequestHeaders {
    private val invalidHeaderNames = setOf(
        "accept-encoding",
        "connection",
        "content-length",
        "host",
        "keep-alive",
        "range",
        "transfer-encoding",
        "upgrade",
    )
    private val headerNamePattern = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")

    const val INTENT_EXTRA_PREFIX = "header."

    fun fromIntentExtras(extras: Map<String, String?>): Map<String, String> {
        return extras.asSequence()
            .filter { (key, _) -> key.startsWith(INTENT_EXTRA_PREFIX) }
            .mapNotNull { (key, value) ->
                val headerName = key.removePrefix(INTENT_EXTRA_PREFIX).trim()
                val headerValue = value?.trim().orEmpty()
                if (!isAllowedHeaderName(headerName) || headerValue.isEmpty()) {
                    null
                } else {
                    headerName to headerValue
                }
            }
            .toMapKeepingLastCaseInsensitive()
    }

    fun sanitize(headers: Map<String, String>): Map<String, String> {
        return headers.asSequence()
            .map { (key, value) -> key.trim() to value.trim() }
            .filter { (key, value) -> isAllowedHeaderName(key) && value.isNotEmpty() }
            .toMapKeepingLastCaseInsensitive()
    }

    fun fingerprint(headers: Map<String, String>): String {
        val canonicalHeaders = sanitize(headers)
        if (canonicalHeaders.isEmpty()) return ""

        val canonicalString = canonicalHeaders.entries
            .sortedBy { it.key.lowercase(Locale.US) }
            .joinToString(separator = "\n") { (key, value) ->
                "${key.lowercase(Locale.US)}:$value"
            }

        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalString.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun isAllowedHeaderName(name: String): Boolean {
        val lowerName = name.lowercase(Locale.US)
        return headerNamePattern.matches(name) && lowerName !in invalidHeaderNames
    }

    private fun Sequence<Pair<String, String>>.toMapKeepingLastCaseInsensitive(): Map<String, String> {
        val normalized = linkedMapOf<String, Pair<String, String>>()
        for ((key, value) in this) {
            normalized[key.lowercase(Locale.US)] = key to value
        }
        return normalized.values.associate { it }
    }
}
