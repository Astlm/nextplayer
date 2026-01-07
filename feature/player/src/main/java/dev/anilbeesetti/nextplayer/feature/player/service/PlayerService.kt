package dev.anilbeesetti.nextplayer.feature.player.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE
import androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteFiles
import dev.anilbeesetti.nextplayer.core.common.extensions.getFilenameFromUri
import dev.anilbeesetti.nextplayer.core.common.extensions.getLocalSubtitles
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.extensions.subtitleCacheDir
import dev.anilbeesetti.nextplayer.core.common.logging.NextLogger
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.DecoderPriority
import dev.anilbeesetti.nextplayer.core.model.LoopMode
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.Resume
import dev.anilbeesetti.nextplayer.core.model.StreamCacheClearPolicy
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.R
import dev.anilbeesetti.nextplayer.feature.player.cache.PerVideoStreamCache
import dev.anilbeesetti.nextplayer.feature.player.cache.StreamCacheAnalyticsListener
import dev.anilbeesetti.nextplayer.feature.player.datasource.DashSegmentPrefetcher
import dev.anilbeesetti.nextplayer.feature.player.datasource.SegmentPrefetcher
import dev.anilbeesetti.nextplayer.feature.player.datasource.SmartCachingDataSourceFactory
import dev.anilbeesetti.nextplayer.feature.player.extensions.addAdditionalSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.extensions.audioTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.copy
import dev.anilbeesetti.nextplayer.feature.player.extensions.getManuallySelectedTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.playbackSpeed
import dev.anilbeesetti.nextplayer.feature.player.extensions.positionMs
import dev.anilbeesetti.nextplayer.feature.player.extensions.setExtras
import dev.anilbeesetti.nextplayer.feature.player.extensions.setIsScrubbingModeEnabled
import dev.anilbeesetti.nextplayer.feature.player.extensions.subtitleTrackIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.switchTrack
import dev.anilbeesetti.nextplayer.feature.player.extensions.uriToSubtitleConfiguration
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoGroupIndex
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoTrackIndexInGroup
import dev.anilbeesetti.nextplayer.feature.player.extensions.videoZoom
import dev.anilbeesetti.nextplayer.feature.player.ffmpeg.NoOpDataSource
import dev.anilbeesetti.nextplayer.feature.player.ffmpeg.WmvAsfDetector
import dev.anilbeesetti.nextplayer.feature.player.ffmpeg.WmvAwareExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null
    private var streamCache: PerVideoStreamCache? = null

    @Volatile
    private var latestPlayerPreferences: PlayerPreferences = PlayerPreferences()

    @Volatile
    private var currentMediaItemUri: Uri? = null

    @Volatile
    private var currentMediaItemMimeType: String? = null

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var mediaRepository: MediaRepository

    private val playerPreferences: PlayerPreferences
        get() = latestPlayerPreferences

    private val customCommands = CustomCommands.asSessionCommands()

    private var isMediaItemReady = false

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            isMediaItemReady = false
            mediaItem?.let { item ->
                currentMediaItemUri = item.localConfiguration?.uri
                currentMediaItemMimeType = item.localConfiguration?.mimeType
                serviceScope.launch(Dispatchers.IO) {
                    streamCache?.setActiveMediaId(item.mediaId)
                }
            }
            mediaItem?.mediaMetadata?.let { metadata ->
                mediaSession?.player?.setPlaybackSpeed(
                    metadata.playbackSpeed ?: playerPreferences.defaultPlaybackSpeed,
                )

                metadata.positionMs?.takeIf { playerPreferences.resume == Resume.YES }?.let {
                    mediaSession?.player?.seekTo(it)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            val oldMediaItem = oldPosition.mediaItem ?: return

            when (reason) {
                DISCONTINUITY_REASON_SEEK,
                DISCONTINUITY_REASON_AUTO_TRANSITION,
                -> {
                    if (newPosition.mediaItem == null || oldMediaItem == newPosition.mediaItem) return

                    val updatedPosition = oldPosition.positionMs.takeIf { reason == DISCONTINUITY_REASON_SEEK } ?: C.TIME_UNSET
                    mediaSession?.player?.replaceMediaItem(
                        oldPosition.mediaItemIndex,
                        oldMediaItem.copy(positionMs = updatedPosition),
                    )
                    serviceScope.launch {
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = updatedPosition,
                        )
                    }
                }

                DISCONTINUITY_REASON_REMOVE -> {
                    serviceScope.launch {
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = oldPosition.positionMs,
                        )
                    }
                }
                else -> return
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            val player = mediaSession?.player ?: return
            if (!isMediaItemReady && tracks.groups.isNotEmpty()) {
                isMediaItemReady = true

                if (playerPreferences.rememberSelections) {
                    player.mediaMetadata.audioTrackIndex?.let {
                        player.switchTrack(C.TRACK_TYPE_AUDIO, it)
                    }
                    player.mediaMetadata.subtitleTrackIndex?.let {
                        player.switchTrack(C.TRACK_TYPE_TEXT, it)
                    }

                    val groupIndex = player.mediaMetadata.videoGroupIndex
                    val trackIndexInGroup = player.mediaMetadata.videoTrackIndexInGroup
                    if (groupIndex != null && trackIndexInGroup != null) {
                        val applied = applyVideoQualityOverride(player, groupIndex, trackIndexInGroup)
                        if (!applied) {
                            player.currentMediaItem?.mediaId?.let { uri ->
                                serviceScope.launch {
                                    mediaRepository.updateMediumVideoQuality(
                                        uri = uri,
                                        groupIndex = null,
                                        trackIndexInGroup = null,
                                    )
                                }
                            }
                            player.currentMediaItem?.let { mediaItem ->
                                player.replaceMediaItem(
                                    player.currentMediaItemIndex,
                                    mediaItem.copy(
                                        videoGroupIndex = null,
                                        videoTrackIndexInGroup = null,
                                    ),
                                )
                            }
                        }
                    }
                }

                val selectedVideoFormat = tracks.groups
                    .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
                    ?.let { group ->
                        (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let(group::getTrackFormat)
                    }
                streamCache?.setCurrentVideoQualityKey(
                    selectedVideoFormat?.let { "v_${it.height}_${it.bitrate}" },
                )
                serviceScope.launch(Dispatchers.IO) {
                    streamCache?.deleteOtherVideoQualities()
                }
            }
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            super.onTrackSelectionParametersChanged(parameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            val audioTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_AUDIO)
            val subtitleTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_TEXT)

            val videoGroups = player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
            val videoOverride = parameters.overrides.values
                .firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
            val videoSelection = videoOverride?.let { override ->
                val groupIndex = videoGroups.indexOfFirst { it.mediaTrackGroup == override.mediaTrackGroup }
                    .takeIf { it >= 0 }
                    ?: return@let null
                val trackIndexInGroup = override.trackIndices.firstOrNull() ?: return@let null
                groupIndex to trackIndexInGroup
            }
            val videoGroupIndex = videoSelection?.first
            val videoTrackIndexInGroup = videoSelection?.second

            if (audioTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumAudioTrack(
                        uri = currentMediaItem.mediaId,
                        audioTrackIndex = audioTrackIndex,
                    )
                }
            }

            if (subtitleTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumSubtitleTrack(
                        uri = currentMediaItem.mediaId,
                        subtitleTrackIndex = subtitleTrackIndex,
                    )
                }
            }

            if (
                videoGroupIndex != currentMediaItem.mediaMetadata.videoGroupIndex ||
                videoTrackIndexInGroup != currentMediaItem.mediaMetadata.videoTrackIndexInGroup
            ) {
                serviceScope.launch {
                    mediaRepository.updateMediumVideoQuality(
                        uri = currentMediaItem.mediaId,
                        groupIndex = videoGroupIndex,
                        trackIndexInGroup = videoTrackIndexInGroup,
                    )
                }
            }

            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(
                    audioTrackIndex = audioTrackIndex,
                    subtitleTrackIndex = subtitleTrackIndex,
                    videoGroupIndex = videoGroupIndex,
                    videoTrackIndexInGroup = videoTrackIndexInGroup,
                ),
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return
            val playbackSpeed = playbackParameters.speed

            serviceScope.launch {
                mediaRepository.updateMediumPlaybackSpeed(
                    uri = currentMediaItem.mediaId,
                    playbackSpeed = playbackSpeed,
                )
            }
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(playbackSpeed = playbackSpeed),
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                mediaSession?.player?.trackSelectionParameters = TrackSelectionParameters.DEFAULT
                mediaSession?.player?.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
            }

            if (playbackState == Player.STATE_READY) {
                mediaSession?.player?.let {
                    serviceScope.launch {
                        mediaRepository.updateMediumLastPlayedTime(
                            uri = it.currentMediaItem?.mediaId ?: return@launch,
                            lastPlayedTime = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                if (mediaSession?.player?.repeatMode != Player.REPEAT_MODE_OFF) {
                    mediaSession?.player?.seekTo(0)
                    mediaSession?.player?.play()
                    return
                }
                mediaSession?.run {
                    player.clearMediaItems()
                    player.stop()
                }
                stopSelf()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            mediaSession?.run {
                serviceScope.launch {
                    mediaRepository.updateMediumPosition(
                        uri = player.currentMediaItem?.mediaId ?: return@launch,
                        position = player.currentPosition,
                    )
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            serviceScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(
                        loopMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> LoopMode.OFF
                            Player.REPEAT_MODE_ONE -> LoopMode.ONE
                            Player.REPEAT_MODE_ALL -> LoopMode.ALL
                            else -> LoopMode.OFF
                        },
                    )
                }
            }
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future(Dispatchers.Default) {
            mediaItems.getOrNull(startIndex)?.let { item ->
                runCatching { streamCache?.setActiveMediaId(item.mediaId) }
                currentMediaItemUri = item.localConfiguration?.uri
                currentMediaItemMimeType = item.localConfiguration?.mimeType
            }
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            return@future updatedMediaItems.toMutableList()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = serviceScope.future {
            val command = CustomCommands.fromSessionCommand(customCommand)
                ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

            when (command) {
                CustomCommands.ADD_SUBTITLE_TRACK -> {
                    val subtitleUri = args.getString(CustomCommands.SUBTITLE_TRACK_URI_KEY)?.toUri()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

                    val newSubConfiguration = uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                    mediaSession?.player?.let { player ->
                        val currentMediaItem = player.currentMediaItem ?: return@let
                        val textTracks = player.currentTracks.groups.filter {
                            it.type == C.TRACK_TYPE_TEXT && it.isSupported
                        }

                        mediaRepository.updateMediumPosition(
                            uri = currentMediaItem.mediaId,
                            position = player.currentPosition,
                        )
                        mediaRepository.updateMediumSubtitleTrack(
                            uri = currentMediaItem.mediaId,
                            subtitleTrackIndex = textTracks.size,
                        )
                        mediaRepository.addExternalSubtitleToMedium(
                            uri = currentMediaItem.mediaId,
                            subtitleUri = subtitleUri,
                        )
                        player.addAdditionalSubtitleConfiguration(newSubConfiguration)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_SKIP_SILENCE_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY)
                    mediaSession?.player?.skipSilenceEnabled = enabled
                    mediaSession?.sessionExtras = Bundle().apply {
                        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_SILENCE_ENABLED -> {
                    val enabled = mediaSession?.player?.skipSilenceEnabled ?: false
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, enabled)
                        },
                    )
                }

                CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED -> {
                    val enabled = args.getBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY)
                    mediaSession?.player?.setIsScrubbingModeEnabled(enabled)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_AUDIO_SESSION_ID -> {
                    val audioSessionId = mediaSession?.player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.AUDIO_SESSION_ID_KEY, audioSessionId)
                        },
                    )
                }

                CustomCommands.STOP_PLAYER_SESSION -> {
                    mediaSession?.run {
                        serviceScope.launch {
                            mediaRepository.updateMediumPosition(
                                uri = player.currentMediaItem?.mediaId ?: return@launch,
                                position = player.currentPosition,
                            )
                        }
                    }
                    mediaSession?.run {
                        player.clearMediaItems()
                        player.stop()
                    }
                    stopSelf()
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        latestPlayerPreferences = runBlocking { preferencesRepository.playerPreferences.first() }
        serviceScope.launch(Dispatchers.Default) {
            preferencesRepository.playerPreferences.collect { latestPlayerPreferences = it }
        }

        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (playerPreferences.decoderPriority) {
                    DecoderPriority.DEVICE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderPriority.PREFER_DEVICE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderPriority.PREFER_APP -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                },
            )

        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(playerPreferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(playerPreferences.preferredSubtitleLanguage),
            )
        }

        val upstreamFactory = DefaultDataSource.Factory(applicationContext)
        val segmentPrefetcher = SegmentPrefetcher(
            upstreamFactory = upstreamFactory,
            cacheProvider = { streamCache?.getCache() },
            scope = serviceScope,
        )
        streamCache = PerVideoStreamCache(context = applicationContext)

        val cachingDataSourceFactory = SmartCachingDataSourceFactory(
            upstreamFactory = upstreamFactory,
            shouldUseNoOpDataSource = { uri -> WmvAsfDetector.isWmvAsf(applicationContext, uri) },
            noOpFactory = NoOpDataSource.Factory(),
            cacheProvider = { streamCache?.getCache() },
            shouldUseRangeSegmentingDataSource = { uri ->
                val currentUri = currentMediaItemUri
                val mimeType = currentMediaItemMimeType
                mimeType != MimeTypes.APPLICATION_MPD && currentUri != null && uri == currentUri
            },
            rangeChunkSizeBytesProvider = { latestPlayerPreferences.rangeStreamChunkSizeBytes },
            segmentConcurrentDownloadsProvider = { latestPlayerPreferences.segmentConcurrentDownloads },
            segmentPrefetcher = segmentPrefetcher,
        )

        val dashSegmentPrefetcher = DashSegmentPrefetcher(
            dataSourceFactory = cachingDataSourceFactory,
            segmentPrefetcher = segmentPrefetcher,
            manifestUriProvider = { currentMediaItemUri },
            isDashProvider = { currentMediaItemMimeType == MimeTypes.APPLICATION_MPD },
            segmentConcurrentDownloadsProvider = { latestPlayerPreferences.segmentConcurrentDownloads },
            scope = serviceScope,
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(
            cachingDataSourceFactory,
            WmvAwareExtractorsFactory(applicationContext),
        )

        val minBufferMs = playerPreferences.minBufferMs.coerceAtLeast(0)
        val maxBufferMs = kotlin.math.max(playerPreferences.maxBufferMs, minBufferMs)
        val bufferForPlaybackMs = playerPreferences.bufferForPlaybackMs.coerceAtLeast(0)
        val bufferForPlaybackAfterRebufferMs = playerPreferences.bufferForPlaybackAfterRebufferMs.coerceAtLeast(0)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs,
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val player = ExoPlayer.Builder(applicationContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                playerPreferences.requireAudioFocus,
            )
            .setHandleAudioBecomingNoisy(playerPreferences.pauseOnHeadsetDisconnect)
            .build()
            .also {
                it.addListener(playbackStateListener)
                streamCache?.let { cache ->
                    it.addAnalyticsListener(
                        StreamCacheAnalyticsListener(
                            streamCache = cache,
                            scope = serviceScope,
                        ),
                    )
                }
                it.addAnalyticsListener(dashSegmentPrefetcher)
                it.pauseAtEndOfMediaItems = !playerPreferences.autoplay
                it.repeatMode = when (playerPreferences.loopMode) {
                    LoopMode.OFF -> Player.REPEAT_MODE_OFF
                    LoopMode.ONE -> Player.REPEAT_MODE_ONE
                    LoopMode.ALL -> Player.REPEAT_MODE_ALL
                }
            }

        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
        } catch (e: Exception) {
            NextLogger.e("PlayerService", "Failed to build MediaSession", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.run {
            player.clearMediaItems()
            player.stop()
            player.removeListener(playbackStateListener)
            player.release()
            release()
            mediaSession = null
        }
        val shouldDeleteStreamCache = playerPreferences.streamCacheClearPolicy == StreamCacheClearPolicy.CLEAR_ON_PLAYBACK_SESSION_EXIT
        runCatching { streamCache?.close(deleteFiles = shouldDeleteStreamCache) }
        streamCache = null
        subtitleCacheDir.deleteFiles()
        serviceScope.cancel()
        isServiceRunning = false
    }

    private suspend fun updatedMediaItemsWithMetadata(
        mediaItems: List<MediaItem>,
    ): List<MediaItem> = supervisorScope {
        mediaItems.map { mediaItem ->
            async {
                val uri = mediaItem.mediaId.toUri()
                val video = mediaRepository.getVideoByUri(uri = mediaItem.mediaId)
                val videoState = mediaRepository.getVideoState(uri = mediaItem.mediaId)

                val externalSubs = videoState?.externalSubs ?: emptyList()
                val localSubs = (videoState?.path ?: getPath(uri))?.let {
                    File(it).getLocalSubtitles(
                        context = this@PlayerService,
                        excludeSubsList = externalSubs,
                    )
                } ?: emptyList()

                val existingSubConfigurations = mediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
                val subConfigurations = (localSubs + externalSubs).map { subtitleUri ->
                    uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                }

                val title = mediaItem.mediaMetadata.title ?: video?.nameWithExtension ?: getFilenameFromUri(uri)
                val artwork = video?.thumbnailPath?.toUri() ?: Uri.Builder().apply {
                    val defaultArtwork = R.drawable.artwork_default
                    scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    authority(resources.getResourcePackageName(defaultArtwork))
                    appendPath(resources.getResourceTypeName(defaultArtwork))
                    appendPath(resources.getResourceEntryName(defaultArtwork))
                }.build()

                val positionMs = mediaItem.mediaMetadata.positionMs ?: videoState?.position
                val videoScale = mediaItem.mediaMetadata.videoZoom ?: videoState?.videoScale
                val playbackSpeed = mediaItem.mediaMetadata.playbackSpeed ?: videoState?.playbackSpeed
                val audioTrackIndex = mediaItem.mediaMetadata.audioTrackIndex ?: videoState?.audioTrackIndex
                val subtitleTrackIndex = mediaItem.mediaMetadata.subtitleTrackIndex ?: videoState?.subtitleTrackIndex
                val videoGroupIndex = mediaItem.mediaMetadata.videoGroupIndex ?: videoState?.videoGroupIndex
                val videoTrackIndexInGroup = mediaItem.mediaMetadata.videoTrackIndexInGroup ?: videoState?.videoTrackIndexInGroup

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(existingSubConfigurations + subConfigurations)
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(title)
                            setArtworkUri(artwork)
                            setExtras(
                                positionMs = positionMs,
                                videoScale = videoScale,
                                playbackSpeed = playbackSpeed,
                                audioTrackIndex = audioTrackIndex,
                                subtitleTrackIndex = subtitleTrackIndex,
                                videoGroupIndex = videoGroupIndex,
                                videoTrackIndexInGroup = videoTrackIndexInGroup,
                            )
                        }.build(),
                    )
                }.build()
            }
        }.awaitAll()
    }

    private fun applyVideoQualityOverride(
        player: Player,
        groupIndex: Int,
        trackIndexInGroup: Int,
    ): Boolean {
        val videoGroups = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        if (groupIndex !in videoGroups.indices) return false

        val trackGroup = videoGroups[groupIndex].mediaTrackGroup
        if (trackIndexInGroup !in 0 until trackGroup.length) return false

        val override = TrackSelectionOverride(trackGroup, trackIndexInGroup)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setOverrideForType(override)
            .build()
        return true
    }

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }
}

@get:UnstableApi
private val Player.audioSessionId: Int
    get() = when (this) {
        is ExoPlayer -> this.audioSessionId
        else -> C.AUDIO_SESSION_ID_UNSET
    }

@get:UnstableApi
@set:UnstableApi
private var Player.skipSilenceEnabled: Boolean
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.skipSilenceEnabled
        else -> false
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.skipSilenceEnabled = value
        }
    }
