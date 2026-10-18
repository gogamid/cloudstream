package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SeriesSourcePreferences
import com.lagradost.cloudstream3.ui.player.SourcePlaybackPlan
import org.junit.Assert.*
import org.junit.Test

class ManualSourceCycleTest {
    private fun candidate(id: String, allowed: Boolean = true, priority: Int = 5) =
        SourcePlaybackPlan.Candidate(id, null, priority, allowed)

    @Test fun `manual cycling follows displayed order and wraps`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("a"), candidate("b"), candidate("c"))
        assertEquals("b", plan.nextManual(links, "a"))
        assertEquals("c", plan.nextManual(links, "b"))
        assertEquals("a", plan.nextManual(links, "c"))
    }

    @Test fun `already attempted sources remain manually selectable`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("a"), candidate("b"))
        plan.attempted("a")
        plan.attempted("b")
        assertTrue(plan.ranked(links, SeriesSourcePreferences()).isEmpty())
        assertEquals("b", plan.nextManual(links, "a"))
        assertEquals("a", plan.nextManual(links, "b"))
    }

    @Test fun `hidden links are skipped but hidden current source remains an order anchor`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("a"), candidate("current", false), candidate("hidden", false), candidate("b"))
        assertEquals("b", plan.nextManual(links, "current"))
        assertEquals("b", plan.nextManual(links, "a"))
        assertEquals("a", plan.nextManual(links, "b"))
    }

    @Test fun `no alternative must not reload or reselect current source`() {
        val plan = SourcePlaybackPlan<String>()
        assertNull(plan.nextManual(emptyList(), "a"))
        assertNull(plan.nextManual(listOf(candidate("a")), "a"))
        assertNull(plan.nextManual(listOf(candidate("a"), candidate("hidden", false)), "a"))
        assertNull(plan.nextManual(listOf(candidate("hidden", false)), null))
    }

    @Test fun `missing current source or initial manual request starts from first visible entry`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("hidden", false), candidate("first"), candidate("second"))
        assertEquals("first", plan.nextManual(links, null))
        assertEquals("first", plan.nextManual(links, "removed"))
    }

    @Test fun `refreshed duplicate source identities cannot cycle onto the same URL`() {
        val plan = SourcePlaybackPlan<String> { it.substringBefore('#') }
        val links = listOf(candidate("a#original"), candidate("a#new-label"), candidate("b#original"), candidate("b#new-label"))
        assertEquals("b#original", plan.nextManual(links, "a#refreshed"))
        assertEquals("a#original", plan.nextManual(links, "b#refreshed"))
        assertNull(plan.nextManual(links.take(2), "a#refreshed"))
    }

    @Test fun `later source arrivals are available on the next press without resetting collection`() {
        val plan = SourcePlaybackPlan<String>()
        val initial = listOf(candidate("a"))
        assertNull(plan.nextManual(initial, "a"))
        assertEquals("late", plan.nextManual(listOf(candidate("late")) + initial, "a"))
        assertFalse(plan.skipRequested)
    }

    @Test fun `manual cycling does not rerank the chooser order or mutate automatic attempts`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("a", priority = 1), candidate("b", priority = 2), candidate("c", priority = 100))
        val prefs = SeriesSourcePreferences()
        plan.attempted("a")
        val before = plan.ranked(links, prefs)
        assertEquals("b", plan.nextManual(links, "a"))
        assertEquals(before, plan.ranked(links, prefs))
        assertEquals(listOf("c", "b"), before.map { it.id })
    }

    @Test fun `hidden duplicate cannot suppress a later visible identity`() {
        val plan = SourcePlaybackPlan<String> { it.substringBefore('#') }
        assertEquals("b#visible", plan.nextManual(listOf(candidate("a"), candidate("b#hidden", false),
            candidate("b#visible")), "a"))
    }
}
