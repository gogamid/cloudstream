package com.lagradost.cloudstream3.ui.player

internal object SubtitleSyncSeek {
    /** Small normal seek flushes cues without directly touching playback-thread renderers. */
    fun target(positionMs: Long, durationMs: Long): Long {
        val position = positionMs.coerceAtLeast(0L)
        return if (durationMs > 0 && position >= durationMs - 1) {
            (durationMs - 2).coerceAtLeast(0L)
        } else if (position < Long.MAX_VALUE) position + 1 else position - 1
    }
}
