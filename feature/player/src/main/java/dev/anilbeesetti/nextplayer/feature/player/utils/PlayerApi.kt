package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import dev.anilbeesetti.nextplayer.feature.player.extensions.getParcelableUriArray
import dev.anilbeesetti.nextplayer.feature.player.model.Subtitle

data class ExternalPlaybackRequest(
    val uri: Uri,
    val title: String?,
    val positionMs: Long?,
    val subtitles: List<Subtitle>,
    val playlist: List<String>,
    val requestHeaders: Map<String, String>,
    val shouldReturnResult: Boolean,
)

object PlayerApi {

    fun parse(intent: Intent): ExternalPlaybackRequest? {
        val parsedIntent = parseIntentUri(intent)
        val uri = intent.data
            ?.takeUnless { it.scheme.equals(INTENT_SCHEME, ignoreCase = true) }
            ?: parsedIntent?.data
            ?: return null

        val extras = mergeExtras(parsedIntent?.extras, intent.extras)

        return ExternalPlaybackRequest(
            uri = uri,
            title = extras?.getString(API_TITLE),
            positionMs = extras?.getLongCompat(API_POSITION),
            subtitles = extras.toSubtitles(),
            playlist = extras.toPlaylist(),
            requestHeaders = extras.toRequestHeaders(),
            shouldReturnResult = extras?.containsKey(API_RETURN_RESULT) == true,
        )
    }

    fun createResult(isPlaybackFinished: Boolean, duration: Long, position: Long): Intent {
        return Intent(API_RESULT_INTENT).apply {
            if (isPlaybackFinished) {
                putExtra(API_END_BY, API_END_BY_COMPLETION)
            } else {
                putExtra(API_END_BY, API_END_BY_USER)
                if (duration != C.TIME_UNSET) putExtra(API_DURATION, duration.toInt())
                if (position != C.TIME_UNSET) putExtra(API_POSITION, position.toInt())
            }
        }
    }

    private fun parseIntentUri(intent: Intent): Intent? {
        val dataString = intent.dataString ?: return null
        if (!dataString.startsWith("$INTENT_SCHEME:", ignoreCase = true)) return null

        return runCatching { Intent.parseUri(dataString, Intent.URI_INTENT_SCHEME) }
            .getOrNull()
    }

    private fun mergeExtras(fallbackExtras: Bundle?, primaryExtras: Bundle?): Bundle? {
        if (fallbackExtras == null && primaryExtras == null) return null

        return Bundle().apply {
            fallbackExtras?.let(::putAll)
            primaryExtras?.let(::putAll)
        }
    }

    private fun Bundle?.toSubtitles(): List<Subtitle> {
        if (this == null || !containsKey(API_SUBS)) return emptyList()

        val subs = getParcelableUriArray(API_SUBS) ?: return emptyList()
        val subsName = getStringArray(API_SUBS_NAME)
        val subsEnable = getParcelableUriArray(API_SUBS_ENABLE)
        val defaultSub = subsEnable?.firstOrNull() as? Uri

        return subs.mapIndexedNotNull { index, parcelable ->
            val subtitleUri = parcelable as? Uri ?: return@mapIndexedNotNull null
            val subtitleName = subsName?.let { if (it.size > index) it[index] else null }
            Subtitle(
                name = subtitleName,
                uri = subtitleUri,
                isSelected = subtitleUri == defaultSub,
            )
        }
    }

    private fun Bundle?.toPlaylist(): List<String> {
        if (this == null || !containsKey(API_PLAYLIST)) return emptyList()
        val playlist = getParcelableUriArray(API_PLAYLIST) ?: return emptyList()
        return playlist.mapNotNull { (it as? Uri)?.toString() }
    }

    private fun Bundle?.toRequestHeaders(): Map<String, String> {
        if (this == null) return emptyMap()
        val stringExtras = keySet().associateWith { key ->
            getString(key)
        }
        return ExternalRequestHeaders.fromIntentExtras(stringExtras)
    }

    private fun Bundle.getLongCompat(key: String): Long? {
        if (!containsKey(key)) return null

        return runCatching { getLong(key) }
            .getOrElse { runCatching { getInt(key).toLong() }.getOrNull() }
    }

    const val API_TITLE = "title"
    const val API_POSITION = "position"
    const val API_DURATION = "duration"
    const val API_RETURN_RESULT = "return_result"
    const val API_END_BY = "end_by"
    const val API_SUBS = "subs"
    const val API_SUBS_ENABLE = "subs.enable"
    const val API_SUBS_NAME = "subs.name"
    const val API_PLAYLIST = "video_list"

    const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"

    private const val API_END_BY_USER = "user"
    private const val API_END_BY_COMPLETION = "playback_completion"
    private const val INTENT_SCHEME = "intent"
}
