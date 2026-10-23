package com.lagradost.cloudstream3.ui.player

/** Per-episode selection state, independent of provider arrival order and Android UI. */
internal class SourcePlaybackPlan<T>(private val keyOf: (T) -> Any? = { it }) {
    data class Candidate<T>(val id: T, val video: SeriesSourcePreferences.Video?,
                            val priority: Int, val allowed: Boolean = true)
    private val attempted = mutableSetOf<Any?>()
    private val failed = mutableSetOf<Any?>()
    private val familyFailures = mutableMapOf<String, Int>()
    var skipRequested = false
        private set

    fun requestSkip(canSkip: Boolean): Boolean {
        if (!canSkip) return false
        skipRequested = true
        return true
    }

    fun attempted(id: T) { attempted.add(keyOf(id)) }
    fun failed(id: T, video: SeriesSourcePreferences.Video?) {
        if (failed.add(keyOf(id))) video?.family()?.let { family ->
            familyFailures[family] = (familyFailures[family] ?: 0) + 1
        }
    }

    /** Manual cycling follows the chooser's visible order, wraps and may revisit
     * attempted sources. It must not consume/reset automatic failover state.
     * A now-hidden current source remains an anchor, never a selectable target.
     */
    fun nextManual(candidates: List<Candidate<T>>, current: T?): T? {
        val currentKey = current?.let(keyOf)
        val displayed = candidates.filter {
            it.allowed || (current != null && keyOf(it.id) == currentKey)
        }.distinctBy { keyOf(it.id) }
        if (displayed.isEmpty()) return null
        val index = displayed.indexOfFirst { current != null && keyOf(it.id) == currentKey }
        for (offset in 1..displayed.size) {
            val next = displayed[(index + offset) % displayed.size]
            if (next.allowed && (current == null || keyOf(next.id) != currentKey)) return next.id
        }
        return null
    }

    private data class Ranked<T>(val candidate: Candidate<T>, val failures: Int,
                                 val manualMatch: Int, val workingMatch: Int)

    fun ranked(candidates: List<Candidate<T>>, preferences: SeriesSourcePreferences): List<Candidate<T>> =
        candidates.filter { it.allowed && keyOf(it.id) !in attempted }.distinctBy { keyOf(it.id) }
            .map { candidate ->
                Ranked(candidate,
                    candidate.video?.family()?.let { familyFailures[it] } ?: 0,
                    candidate.video?.let { preferences.video?.match(it) } ?: 0,
                    candidate.video?.let { preferences.lastWorkingVideo?.match(it) } ?: 0)
            }.sortedWith(compareBy<Ranked<T>> { it.failures }
                .thenByDescending { it.manualMatch }.thenByDescending { it.workingMatch }
                .thenByDescending { it.candidate.priority })
            .map { it.candidate }

    /** Collect the complete list unless the user explicitly chooses Skip.
     * Saved choices and quality/source priorities rank links, never imply Skip.
     * Skip changes playback readiness only; collection remains owned by the ViewModel.
     */
    fun ready(candidates: List<Candidate<T>>, collecting: Boolean, canSkip: Boolean): Boolean =
        (!collecting || (skipRequested && canSkip)) &&
            candidates.any { it.allowed && keyOf(it.id) !in attempted }
}
