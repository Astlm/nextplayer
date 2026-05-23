package dev.anilbeesetti.nextplayer.feature.player.network

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

internal class HttpStatusRetryPolicy(
    retryStatusCodes: Collection<Int>,
) {
    private val retryStatusCodes = retryStatusCodes.toSet()

    fun shouldRetryHttpStatus(statusCode: Int): Boolean {
        return statusCode in retryStatusCodes
    }

    fun retryDelayMsForStatusCode(statusCode: Int, errorCount: Int): Long {
        return if (shouldRetryHttpStatus(statusCode)) {
            defaultRetryDelayMs(errorCount)
        } else {
            C.TIME_UNSET
        }
    }

    private fun defaultRetryDelayMs(errorCount: Int): Long {
        return ((errorCount - 1) * 1000L).coerceAtMost(5000L)
    }
}

@UnstableApi
internal class WhitelistHttpStatusLoadErrorHandlingPolicy(
    private val retryStatusCodesProvider: () -> Collection<Int>,
) : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val responseCode = loadErrorInfo.exception.httpResponseCodeOrNull()
            ?: return super.getRetryDelayMsFor(loadErrorInfo)

        return HttpStatusRetryPolicy(retryStatusCodesProvider())
            .retryDelayMsForStatusCode(responseCode, loadErrorInfo.errorCount)
    }
}

private fun IOException.httpResponseCodeOrNull(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) {
            return current.responseCode
        }
        current = current.cause
    }
    return null
}
