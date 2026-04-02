package dev.anilbeesetti.nextplayer.feature.player.datasource

import androidx.media3.datasource.DataSpec
import dev.anilbeesetti.nextplayer.feature.player.utils.ExternalRequestHeaders
import java.util.Locale

internal fun DataSpec.withCurrentRequestHeaders(headers: Map<String, String>): DataSpec {
    val sanitizedHeaders = ExternalRequestHeaders.sanitize(headers)
    if (sanitizedHeaders.isEmpty()) return this

    val scheme = uri.scheme?.lowercase(Locale.US)
    if (scheme != "http" && scheme != "https") return this

    return withAdditionalHeaders(sanitizedHeaders)
}
