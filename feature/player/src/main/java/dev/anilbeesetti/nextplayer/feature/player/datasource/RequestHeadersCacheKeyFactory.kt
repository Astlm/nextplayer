package dev.anilbeesetti.nextplayer.feature.player.datasource

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import dev.anilbeesetti.nextplayer.feature.player.utils.ExternalRequestHeaders

internal class RequestHeadersCacheKeyFactory(
    private val delegate: CacheKeyFactory = CacheKeyFactory.DEFAULT,
) : CacheKeyFactory {

    override fun buildCacheKey(dataSpec: DataSpec): String {
        val sanitizedHeaders = ExternalRequestHeaders.sanitize(dataSpec.httpRequestHeaders)
        val baseDataSpec = if (sanitizedHeaders.isEmpty()) {
            dataSpec
        } else {
            dataSpec.withRequestHeaders(emptyMap())
        }
        val baseKey = delegate.buildCacheKey(baseDataSpec)
        if (sanitizedHeaders.isEmpty()) return baseKey

        return "$baseKey#request_headers=${ExternalRequestHeaders.fingerprint(sanitizedHeaders)}"
    }
}
