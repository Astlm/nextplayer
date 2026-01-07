package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private const val MEDIA_METADATA_POSITION_KEY = "media_metadata_position"
private const val MEDIA_METADATA_PLAYBACK_SPEED_KEY = "media_metadata_playback_speed"
private const val MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY = "audio_track_index"
private const val MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY = "subtitle_track_index"
private const val MEDIA_METADATA_VIDEO_ZOOM_KEY = "media_metadata_video_zoom"
private const val MEDIA_METADATA_VIDEO_GROUP_INDEX_KEY = "video_group_index"
private const val MEDIA_METADATA_VIDEO_TRACK_INDEX_IN_GROUP_KEY = "video_track_index_in_group"

private fun Bundle.setExtras(
    positionMs: Long?,
    videoScale: Float?,
    playbackSpeed: Float?,
    audioTrackIndex: Int?,
    subtitleTrackIndex: Int?,
    videoGroupIndex: Int?,
    videoTrackIndexInGroup: Int?,
) = apply {
    if (positionMs != null) putLong(MEDIA_METADATA_POSITION_KEY, positionMs) else remove(MEDIA_METADATA_POSITION_KEY)
    if (videoScale != null) putFloat(MEDIA_METADATA_VIDEO_ZOOM_KEY, videoScale) else remove(MEDIA_METADATA_VIDEO_ZOOM_KEY)
    if (playbackSpeed != null) putFloat(MEDIA_METADATA_PLAYBACK_SPEED_KEY, playbackSpeed) else remove(MEDIA_METADATA_PLAYBACK_SPEED_KEY)
    if (audioTrackIndex != null) putInt(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY, audioTrackIndex) else remove(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY)
    if (subtitleTrackIndex != null) putInt(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY, subtitleTrackIndex) else remove(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY)
    if (videoGroupIndex != null) putInt(MEDIA_METADATA_VIDEO_GROUP_INDEX_KEY, videoGroupIndex) else remove(MEDIA_METADATA_VIDEO_GROUP_INDEX_KEY)
    if (videoTrackIndexInGroup != null) {
        putInt(MEDIA_METADATA_VIDEO_TRACK_INDEX_IN_GROUP_KEY, videoTrackIndexInGroup)
    } else {
        remove(MEDIA_METADATA_VIDEO_TRACK_INDEX_IN_GROUP_KEY)
    }
}

fun MediaMetadata.Builder.setExtras(
    positionMs: Long? = null,
    videoScale: Float? = null,
    playbackSpeed: Float? = null,
    audioTrackIndex: Int? = null,
    subtitleTrackIndex: Int? = null,
    videoGroupIndex: Int? = null,
    videoTrackIndexInGroup: Int? = null,
) = setExtras(
    Bundle().setExtras(
        positionMs = positionMs,
        videoScale = videoScale,
        playbackSpeed = playbackSpeed,
        audioTrackIndex = audioTrackIndex,
        subtitleTrackIndex = subtitleTrackIndex,
        videoGroupIndex = videoGroupIndex,
        videoTrackIndexInGroup = videoTrackIndexInGroup,
    ),
)

val MediaMetadata.positionMs: Long?
    get() = extras?.run {
        getLong(MEDIA_METADATA_POSITION_KEY)
            .takeIf { containsKey(MEDIA_METADATA_POSITION_KEY) }
    }

val MediaMetadata.playbackSpeed: Float?
    get() = extras?.run {
        getFloat(MEDIA_METADATA_PLAYBACK_SPEED_KEY)
            .takeIf { containsKey(MEDIA_METADATA_PLAYBACK_SPEED_KEY) }
    }

val MediaMetadata.audioTrackIndex: Int?
    get() = extras?.run {
        getInt(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY)
            .takeIf { containsKey(MEDIA_METADATA_AUDIO_TRACK_INDEX_KEY) }
    }

val MediaMetadata.subtitleTrackIndex: Int?
    get() = extras?.run {
        getInt(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY)
            .takeIf { containsKey(MEDIA_METADATA_SUBTITLE_TRACK_INDEX_KEY) }
    }

val MediaMetadata.videoZoom: Float?
    get() = extras?.run {
        getFloat(MEDIA_METADATA_VIDEO_ZOOM_KEY)
            .takeIf { containsKey(MEDIA_METADATA_VIDEO_ZOOM_KEY) }
    }

val MediaMetadata.videoGroupIndex: Int?
    get() = extras?.run {
        getInt(MEDIA_METADATA_VIDEO_GROUP_INDEX_KEY)
            .takeIf { containsKey(MEDIA_METADATA_VIDEO_GROUP_INDEX_KEY) }
    }

val MediaMetadata.videoTrackIndexInGroup: Int?
    get() = extras?.run {
        getInt(MEDIA_METADATA_VIDEO_TRACK_INDEX_IN_GROUP_KEY)
            .takeIf { containsKey(MEDIA_METADATA_VIDEO_TRACK_INDEX_IN_GROUP_KEY) }
    }

fun MediaItem.copy(
    positionMs: Long? = this.mediaMetadata.positionMs,
    videoZoom: Float? = this.mediaMetadata.videoZoom,
    playbackSpeed: Float? = this.mediaMetadata.playbackSpeed,
    audioTrackIndex: Int? = this.mediaMetadata.audioTrackIndex,
    subtitleTrackIndex: Int? = this.mediaMetadata.subtitleTrackIndex,
    videoGroupIndex: Int? = this.mediaMetadata.videoGroupIndex,
    videoTrackIndexInGroup: Int? = this.mediaMetadata.videoTrackIndexInGroup,
) = buildUpon().setMediaMetadata(
    mediaMetadata.buildUpon().setExtras(
        Bundle(mediaMetadata.extras).setExtras(
            positionMs = positionMs,
            videoScale = videoZoom,
            playbackSpeed = playbackSpeed,
            audioTrackIndex = audioTrackIndex,
            subtitleTrackIndex = subtitleTrackIndex,
            videoGroupIndex = videoGroupIndex,
            videoTrackIndexInGroup = videoTrackIndexInGroup,
        ),
    ).build(),
).build()
