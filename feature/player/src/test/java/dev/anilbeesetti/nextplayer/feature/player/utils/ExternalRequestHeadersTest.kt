package dev.anilbeesetti.nextplayer.feature.player.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRequestHeadersTest {

    @Test
    fun fromIntentExtras_keepsOnlySupportedHeaderEntries() {
        val headers = ExternalRequestHeaders.fromIntentExtras(
            mapOf(
                "header.Authorization" to "Bearer token",
                "header.Referer" to "https://example.com",
                "header.Range" to "bytes=0-1",
                "browser_fallback_url" to "https://fallback.example.com",
                "user_agent" to "ignored",
            ),
        )

        assertEquals(
            mapOf(
                "Authorization" to "Bearer token",
                "Referer" to "https://example.com",
            ),
            headers,
        )
    }

    @Test
    fun sanitize_ignores_invalidHeadersAndKeepsLastCaseInsensitiveValue() {
        val headers = ExternalRequestHeaders.sanitize(
            linkedMapOf(
                "Authorization" to "Bearer old",
                "authorization" to "Bearer new",
                "Bad Header" to "ignored",
                "Content-Length" to "123",
            ),
        )

        assertEquals(mapOf("authorization" to "Bearer new"), headers)
    }

    @Test
    fun fingerprint_isStableAcrossHeaderOrder() {
        val firstFingerprint = ExternalRequestHeaders.fingerprint(
            mapOf(
                "Authorization" to "Bearer token",
                "Referer" to "https://example.com",
            ),
        )
        val secondFingerprint = ExternalRequestHeaders.fingerprint(
            linkedMapOf(
                "Referer" to "https://example.com",
                "Authorization" to "Bearer token",
            ),
        )

        assertTrue(firstFingerprint.isNotEmpty())
        assertEquals(firstFingerprint, secondFingerprint)
    }
}
