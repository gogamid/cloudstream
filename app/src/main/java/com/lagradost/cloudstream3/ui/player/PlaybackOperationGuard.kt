package com.lagradost.cloudstream3.ui.player

import java.util.concurrent.atomic.AtomicLong

/** Invalidates deferred work even when the same source is selected again. */
internal class PlaybackOperationGuard {
    private val generation = AtomicLong(0)
    fun token(): Long = generation.get()
    fun advance(): Long = generation.incrementAndGet()
    fun isCurrent(token: Long): Boolean = generation.get() == token
}
