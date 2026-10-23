package com.lagradost.cloudstream3.ui.player

/** Counts foreground startup time only, not post-start buffering or manual attempts. */
internal class SourceStartupTimeout(private val automatic: Boolean, limitMs: Long = LIMIT_MS) {
    companion object {
        const val LIMIT_MS = 5_000L
        fun milliseconds(seconds: Int): Long =
            if (seconds >= 0) seconds.toLong() * 1_000L else LIMIT_MS
    }
    private val budgetMs = if (limitMs >= 0L) limitMs else LIMIT_MS
    private var elapsedMs = 0L
    private var started = false

    fun tick(deltaMs: Long, eligible: Boolean, playing: Boolean): Boolean {
        if (playing) started = true
        if (!automatic || started || budgetMs == 0L) return false
        // Saturate at this attempt's budget instead of overflowing on a bad clock delta.
        if (eligible) elapsedMs += minOf(deltaMs.coerceAtLeast(0L), budgetMs - elapsedMs)
        return elapsedMs >= budgetMs
    }
}
