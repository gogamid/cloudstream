package com.lagradost.cloudstream3.ui.player

/** Preserve the physical offset when Android restores a pre-integration task.
 * Legacy UI state stored positive=later, while upstream UI now uses positive=earlier.
 * This is video-instance state only, not a preference or a per-series choice.
 */
internal object SubtitleDelayState {
    const val VERSION_KEY = "subtitle_delay_semantics"
    const val VERSION = 1
    fun restore(value: Long, version: Int): Long = if (version < VERSION) -value else value
}
