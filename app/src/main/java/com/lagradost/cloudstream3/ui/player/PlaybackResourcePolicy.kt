package com.lagradost.cloudstream3.ui.player

internal object PlaybackResourcePolicy {
    const val TV_BUFFER_BYTES = 32 * 1024 * 1024
    const val TV_EPISODE_CACHE_LIMIT = 4

    fun targetBufferBytes(tv: Boolean, configured: Long, defaultBytes: Int): Int = when {
        configured > 0 -> configured.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        tv -> TV_BUFFER_BYTES
        else -> defaultBytes
    }

    fun backBufferMs(tv: Boolean) = if (tv) 0 else 30_000

    /** Keep descriptors for every choice, but attach only the selected external track on TV. */
    fun externalSubtitles(all: Set<SubtitleData>, selected: SubtitleData?, tv: Boolean): List<SubtitleData> =
        all.filter { it.origin != SubtitleOrigin.EMBEDDED_IN_VIDEO &&
            (!tv || (selected != null && it.getId() == selected.getId())) }

    fun <K> episodeCacheEvictions(timestamps: Map<K, Long>, keep: K, now: Long): Set<K> {
        val others = timestamps.entries.filter { it.key != keep }.sortedByDescending { it.value }
        return others.filterIndexed { index, entry ->
            index >= TV_EPISODE_CACHE_LIMIT - 1 || now - entry.value > 20 * 60
        }.mapTo(mutableSetOf()) { it.key }
    }
}
