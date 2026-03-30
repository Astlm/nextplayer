package io.github.anilbeesetti.nextlib.media3ext.renderer

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.Collections
import java.util.WeakHashMap

private val subtitleDelayMillisByPlayer = Collections.synchronizedMap(WeakHashMap<ExoPlayer, Long>())
private val subtitleSpeedByPlayer = Collections.synchronizedMap(WeakHashMap<ExoPlayer, Float>())

// Compatibility shim for the vendored nextlib build, which does not include the
// upstream renderer extension accessors used by the app module.
@get:UnstableApi
@set:UnstableApi
var ExoPlayer.subtitleDelayMilliseconds: Long
    get() = subtitleDelayMillisByPlayer[this] ?: 0L
    set(value) {
        if (value == 0L) {
            subtitleDelayMillisByPlayer.remove(this)
        } else {
            subtitleDelayMillisByPlayer[this] = value
        }
    }

@get:UnstableApi
@set:UnstableApi
var ExoPlayer.subtitleSpeed: Float
    get() = subtitleSpeedByPlayer[this] ?: 1f
    set(value) {
        if (value == 1f) {
            subtitleSpeedByPlayer.remove(this)
        } else {
            subtitleSpeedByPlayer[this] = value
        }
    }
