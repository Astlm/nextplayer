package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import dev.anilbeesetti.nextplayer.core.ui.R
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.VideoQualitySelectorView(
    modifier: Modifier = Modifier,
    show: Boolean,
    player: Player,
    onDismiss: () -> Unit,
) {
    val videoGroups = player.currentTracks.groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }

    val videoOverride = player.trackSelectionParameters.overrides.values
        .firstOrNull { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO }
    val selectedGroupIndex = videoOverride
        ?.let { override -> videoGroups.indexOfFirst { it.mediaTrackGroup == override.mediaTrackGroup } }
        ?.takeIf { it >= 0 }
    val selectedTrackIndexInGroup = videoOverride?.trackIndices?.firstOrNull()

    val defaultTrackLabel = stringResource(R.string.video_track)
    fun buildQualityLabel(format: Format): String {
        val height = format.height.takeIf { it > 0 }
        val width = format.width.takeIf { it > 0 }
        val bitrate = format.bitrate.takeIf { it > 0 }

        val base = when {
            height != null -> "${height}p"
            width != null -> "${width}w"
            else -> defaultTrackLabel
        }

        val bitrateText = bitrate?.let {
            val mbps = (it / 1_000_000.0 * 10).roundToInt() / 10.0
            " · ${mbps}Mbps"
        }.orEmpty()

        return base + bitrateText
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.select_video_quality),
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            RadioButtonRow(
                selected = videoOverride == null,
                text = stringResource(R.string.auto),
                onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                    onDismiss()
                },
            )

            if (videoGroups.isEmpty()) {
                Text(text = stringResource(R.string.no_video_qualities_found))
                return@Column
            }

            videoGroups.forEachIndexed { groupIndex, group ->
                val trackGroup = group.mediaTrackGroup
                (0 until trackGroup.length).forEach { trackIndex ->
                    RadioButtonRow(
                        selected = selectedGroupIndex == groupIndex && selectedTrackIndexInGroup == trackIndex,
                        text = buildQualityLabel(trackGroup.getFormat(trackIndex)),
                        onClick = {
                            val override = TrackSelectionOverride(trackGroup, trackIndex)
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                                .setOverrideForType(override)
                                .build()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

