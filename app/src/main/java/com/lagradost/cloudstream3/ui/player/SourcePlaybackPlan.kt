package com.lagradost.cloudstream3.ui.player

/** Per-episode selection state, independent of provider arrival order and Android UI. */
internal class SourcePlaybackPlan<T>(private val keyOf: (T) -> Any? = { it }) {
    data class Candidate<T>(val id: T, val video: SeriesSourcePreferences.Video?,
                            val priority: Int, val allowed: Boolean = true)
    companion object {
        const val SETTLE_MS = 750L
        const val PREFERRED_WAIT_MS = 2_500L
    }

    private val attempted = mutableSetOf<Any?>()
    private val failed = mutableSetOf<Any?>()
    private val familyFailures = mutableMapOf<String, Int>()
    private var firstCandidateAt: Long? = null
    var skipRequested = false

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

    /** The grace period starts with the first usable source, never resets on new arrivals. */
    fun ready(nowMs: Long, candidates: List<Candidate<T>>, preferences: SeriesSourcePreferences,
              collecting: Boolean, mayStartEarly: Boolean, instantPriority: Int): Boolean {
        // Readiness only needs membership/matches, not an O(n log n) ranking
        // on every timer tick. Rank once when an attempt actually starts.
        val available = candidates.filter { it.allowed && keyOf(it.id) !in attempted }
        if (available.isEmpty()) return false
        if (!collecting || skipRequested) return true
        if (!mayStartEarly) return false
        val first = firstCandidateAt ?: nowMs.also { firstCandidateAt = it }
        if (preferences.video != null) {
            if (available.any { it.video?.let(preferences.video::match)?.let { score -> score >= 3 } == true }) return true
            return nowMs - first >= PREFERRED_WAIT_MS
        }
        return available.any { it.priority >= instantPriority } || nowMs - first >= SETTLE_MS
    }
}
