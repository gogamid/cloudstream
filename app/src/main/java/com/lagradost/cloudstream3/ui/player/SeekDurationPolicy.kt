package com.lagradost.cloudstream3.ui.player

/** Choose the existing setting, not a new remote-control or batching default. */
internal object SeekDurationPolicy {
    const val DEFAULT_MS = 10_000L

    fun amountMs(isTv: Boolean, controlsVisible: Boolean, doubleTapSeconds: Int,
                 tvShownSeconds: Int, tvHiddenSeconds: Int): Long {
        val seconds = if (!isTv) doubleTapSeconds else if (controlsVisible) tvShownSeconds else tvHiddenSeconds
        // Do not clamp valid saved values to the current slider range. Older
        // versions/backups may contain positive amounts outside that range.
        return if (seconds > 0) seconds.toLong() * 1_000L else DEFAULT_MS
    }
}
