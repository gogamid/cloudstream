package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SeriesSourcePreferences
import com.lagradost.cloudstream3.ui.player.SourcePlaybackPlan
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class SourceCountAutoStartTest {
    private fun links(count: Int, allowed: Boolean = true) = (1..count).map {
        SourcePlaybackPlan.Candidate("source-$it", null, priority = 100, allowed = allowed)
    }

    @Test fun `default waits at forty nine and starts at fifty or above`() {
        val plan = SourcePlaybackPlan<String>()
        assertEquals(50, SourcePlaybackPlan.DEFAULT_AUTO_START_COUNT)
        assertFalse(plan.ready(links(49), collecting = true, canSkip = true))
        assertTrue(plan.ready(links(50), collecting = true, canSkip = true))
        assertTrue(plan.ready(links(51), collecting = true, canSkip = true))
    }

    @Test fun `custom threshold is followed instead of hardcoded fifty`() {
        val plan = SourcePlaybackPlan<String>()
        for (count in listOf(1, 5, 10, 25, 100, 500)) {
            assertFalse(plan.ready(links(count - 1), true, true, count))
            assertTrue(plan.ready(links(count), true, true, count))
        }
    }

    @Test fun `zero waits for complete collection even with many available sources`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(plan.ready(links(1_000), true, true, autoStartAfter = 0))
        assertTrue(plan.ready(links(1_000), false, true, autoStartAfter = 0))
    }

    @Test fun `completed extraction with fewer than threshold starts immediately`() {
        assertTrue(SourcePlaybackPlan<String>().ready(links(3), false, true, autoStartAfter = 50))
    }

    @Test fun `manual Skip still starts before threshold and when auto count is disabled`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(plan.ready(links(1), true, true))
        assertTrue(plan.requestSkip(canSkip = true))
        assertTrue(plan.ready(links(1), true, true))
        assertTrue(plan.ready(links(1), true, true, autoStartAfter = 0))
    }

    @Test fun `non skippable generator must finish even after threshold`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(plan.ready(links(50), true, false))
        assertTrue(plan.ready(links(50), false, false))
    }

    @Test fun `hidden sources do not count toward threshold or Skip label`() {
        val plan = SourcePlaybackPlan<String>()
        val hidden = links(100, allowed = false)
        assertEquals(49, plan.usableCount(links(49) + hidden))
        assertFalse(plan.ready(links(49) + hidden, true, true))
        assertTrue(plan.ready(links(50) + hidden, true, true))
    }

    @Test fun `duplicate URLs with different labels count once using attempt identity`() {
        data class Link(val url: String, val label: String)
        val plan = SourcePlaybackPlan<Link> { it.url }
        val duplicates = (1..100).map {
            SourcePlaybackPlan.Candidate(Link("same-url", "Label $it"), null, 100)
        }
        assertEquals(1, plan.usableCount(duplicates))
        assertFalse(plan.ready(duplicates, true, true))
        val distinct = (1..49).map {
            SourcePlaybackPlan.Candidate(Link("url-$it", "Different"), null, 1)
        }
        assertTrue(plan.ready(duplicates + distinct, true, true))
    }

    @Test fun `reaching count does not override exhausted attempt history`() {
        val plan = SourcePlaybackPlan<String>()
        val candidates = links(50)
        candidates.forEach { plan.attempted(it.id) }
        assertFalse(plan.ready(candidates, true, true))
    }

    @Test fun `count readiness neither consumes candidates nor fabricates manual Skip`() {
        val plan = SourcePlaybackPlan<String>()
        val candidates = links(50)
        assertTrue(plan.ready(candidates, true, true))
        assertFalse(plan.skipRequested)
        assertEquals(50, plan.ranked(candidates, SeriesSourcePreferences()).size)
    }

    @Test fun `new episode must reach threshold again`() {
        assertTrue(SourcePlaybackPlan<String>().ready(links(50), true, true))
        assertFalse(SourcePlaybackPlan<String>().ready(links(1), true, true))
    }

    @Test fun `negative invalid setting safely falls back to fifty`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(plan.ready(links(49), true, true, -1))
        assertTrue(plan.ready(links(50), true, true, -1))
    }

    @Test fun `positive restored thresholds outside slider range are respected`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(plan.ready(links(500), true, true, autoStartAfter = 600))
        assertTrue(plan.ready(links(600), true, true, autoStartAfter = 600))
        assertFalse(plan.ready(links(600), true, true, autoStartAfter = Int.MAX_VALUE))
    }

    @Test fun `saved manual source still wins ranking when count triggers playback`() {
        val video = SeriesSourcePreferences.Video("Saved", "Mirror", 1080, "VIDEO")
        val saved = SourcePlaybackPlan.Candidate("saved", video, priority = 1)
        val candidates = links(49) + saved
        val plan = SourcePlaybackPlan<String>()
        assertTrue(plan.ready(candidates, true, true))
        assertEquals("saved", plan.ranked(candidates, SeriesSourcePreferences(video = video)).first().id)
    }

    @Test fun `empty or all hidden result never starts even when threshold is one`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(plan.ready(emptyList(), true, true, 1))
        assertFalse(plan.ready(links(100, allowed = false), true, true, 1))
        assertFalse(plan.ready(emptyList(), false, true, 1))
    }

    @Test fun `count setting remains backed up and restorable`() {
        assertTrue("source_auto_start_count".isTransferable())
    }
}
