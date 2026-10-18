package com.lagradost.cloudstream3.ui.player

internal object PlaybackCheckpoint {
    const val INTERVAL_MS = 5_000L

    // Never overwrite a resume point with the unset/zero duration of a loading player.
    fun shouldSave(playing: Boolean, rendered: Boolean, position: Long, duration: Long): Boolean =
        playing && rendered && duration >= 30_000L && position >= 0L && position <= duration
}
