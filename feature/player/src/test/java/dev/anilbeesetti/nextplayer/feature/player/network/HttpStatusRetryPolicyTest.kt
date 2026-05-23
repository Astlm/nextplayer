package dev.anilbeesetti.nextplayer.feature.player.network

import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.core.model.formatRetryHttpStatusCodes
import dev.anilbeesetti.nextplayer.core.model.parseRetryHttpStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpStatusRetryPolicyTest {

    @Test
    fun parseRetryStatusCodes_keepsOnlyHttpStatusCodesAndSortsUniqueValues() {
        val parsed = parseRetryHttpStatusCodes("403, 401 500\n999 100 401 abc -1 599 600")

        assertEquals(listOf(100, 401, 403, 500, 599), parsed)
    }

    @Test
    fun formatRetryStatusCodes_joinsCodesWithCommaAndSpace() {
        val formatted = formatRetryHttpStatusCodes(listOf(403, 401, 500))

        assertEquals("401, 403, 500", formatted)
    }

    @Test
    fun shouldRetryHttpStatus_returnsTrueOnlyForWhitelistEntries() {
        val policy = HttpStatusRetryPolicy(retryStatusCodes = listOf(401, 403))

        assertTrue(policy.shouldRetryHttpStatus(401))
        assertTrue(policy.shouldRetryHttpStatus(403))
        assertFalse(policy.shouldRetryHttpStatus(404))
        assertFalse(policy.shouldRetryHttpStatus(500))
    }

    @Test
    fun retryDelayMsForStatusCode_returnsDelayForWhitelistedCode() {
        val policy = HttpStatusRetryPolicy(retryStatusCodes = listOf(401, 403))

        assertEquals(0L, policy.retryDelayMsForStatusCode(401, errorCount = 1))
        assertEquals(1000L, policy.retryDelayMsForStatusCode(403, errorCount = 2))
    }

    @Test
    fun retryDelayMsForStatusCode_returnsFatalForNonWhitelistedCode() {
        val policy = HttpStatusRetryPolicy(retryStatusCodes = listOf(401, 403))

        assertEquals(C.TIME_UNSET, policy.retryDelayMsForStatusCode(404, errorCount = 1))
    }
}
