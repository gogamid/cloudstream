package com.lagradost.cloudstream3.ui.player

/** Counts foreground startup time only, not post-start buffering or manual attempts. */
internal class SourceStartupTimeout(private val automatic: Boolean) {
    companion object { const val LIMIT_MS = 5_000L }
    private var elapsedMs = 0L
    private var started = false

    fun tick(deltaMs: Long, eligible: Boolean, playing: Boolean): Boolean {
        if (playing) started = true
        if (!automatic || started) return false
        if (eligible) elapsedMs += deltaMs.coerceAtLeast(0L)
        return elapsedMs >= LIMIT_MS
    }
}
