package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SeriesSourcePreferences
import com.lagradost.cloudstream3.ui.player.SourcePlaybackPlan
import org.junit.Assert.*
import org.junit.Test

class SourcePlaybackPlanTest {
    private fun video(source: String = "Host A", name: String = "Mirror S01E01 1080p",
                      quality: Int = 1080, host: String? = "cdn.example") =
        SeriesSourcePreferences.Video(source, name, quality, "VIDEO", host)
    private fun candidate(id: String, video: SeriesSourcePreferences.Video = video(),
                          priority: Int = 5, allowed: Boolean = true) =
        SourcePlaybackPlan.Candidate(id, video, priority, allowed)
    private fun ready(plan: SourcePlaybackPlan<String>,
                      candidates: List<SourcePlaybackPlan.Candidate<String>>,
                      collecting: Boolean = true, canSkip: Boolean = true) =
        // These full-collection regressions exercise the explicit disabled-count mode.
        plan.ready(candidates, collecting, canSkip, autoStartAfter = 0)

    @Test fun `saved choice still wins but does not skip collecting other mirrors`() {
        val plan = SourcePlaybackPlan<String>()
        val preference = SeriesSourcePreferences(video = video())
        val early = candidate("early", video("Other"), 20)
        assertFalse(ready(plan, listOf(early)))
        val saved = candidate("saved", video(name = "Mirror S01E02 1080p"), 1)
        val links = listOf(early, saved)
        assertFalse(ready(plan, links))
        assertTrue(ready(plan, links, collecting = false))
        assertEquals("saved", plan.ranked(links, preference).first().id)
    }

    @Test fun `even priority ten or much higher cannot imply Skip`() {
        val plan = SourcePlaybackPlan<String>()
        for (priority in listOf(1, 10, 20, Int.MAX_VALUE)) {
            val links = listOf(candidate("other", video("Other"), priority))
            assertFalse(ready(plan, links))
            assertTrue(ready(plan, links, collecting = false))
        }
    }

    @Test fun `weak provider match retains preferred quality after complete collection`() {
        val plan = SourcePlaybackPlan<String>()
        val prefs = SeriesSourcePreferences(video = video(name = "Old filename", host = null))
        val sameQuality = candidate("1080", video(name = "New filename", host = "new.example"), 1)
        val differentQuality = candidate("2160", video(name = "Other filename", quality = 2160), 20)
        val links = listOf(differentQuality, sameQuality)
        assertFalse(ready(plan, links))
        assertTrue(ready(plan, links, collecting = false))
        assertEquals("1080", plan.ranked(links, prefs).first().id)
    }

    @Test fun `each arriving source remains collecting until provider completion`() {
        val plan = SourcePlaybackPlan<String>()
        val links = mutableListOf<SourcePlaybackPlan.Candidate<String>>()
        repeat(100) { index ->
            links += candidate("mirror-$index")
            assertFalse(ready(plan, links))
        }
        assertTrue(ready(plan, links, collecting = false))
        assertEquals(100, plan.ranked(links, SeriesSourcePreferences()).size)
    }

    @Test fun `explicit Skip starts immediately but never selects disallowed links`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("other", video("Other")))
        assertTrue(plan.requestSkip(canSkip = true))
        assertTrue(ready(plan, links))
        assertFalse(ready(plan, listOf(candidate("hidden", allowed = false))))
        assertFalse(ready(plan, emptyList()))
        assertTrue(ready(SourcePlaybackPlan(), links, collecting = false))
    }

    @Test fun `providers which cannot skip must complete extraction`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("saved", priority = 100))
        assertFalse(plan.requestSkip(canSkip = false))
        assertFalse(plan.skipRequested)
        assertFalse(ready(plan, links, canSkip = false))
        assertTrue(ready(plan, links, collecting = false, canSkip = false))
    }

    @Test fun `Skip readiness still requires an unattempted available source`() {
        val plan = SourcePlaybackPlan<String>()
        assertTrue(plan.requestSkip(canSkip = true))
        plan.attempted("first")
        assertFalse(ready(plan, listOf(candidate("first"))))
        assertTrue(ready(plan, listOf(candidate("first"), candidate("late"))))
    }

    @Test fun `Skip never carries over to a new episode`() {
        val links = listOf(candidate("first"))
        val oldEpisode = SourcePlaybackPlan<String>()
        oldEpisode.requestSkip(canSkip = true)
        assertTrue(ready(oldEpisode, links))
        assertFalse(ready(SourcePlaybackPlan(), links))
    }

    @Test fun `empty and fully hidden completed collections do not report ready`() {
        val plan = SourcePlaybackPlan<String>()
        assertFalse(ready(plan, emptyList(), collecting = false))
        assertFalse(ready(plan, listOf(candidate("hidden", allowed = false)), collecting = false))
    }

    @Test fun `fallback searches all untried links not just below saved source`() {
        val plan = SourcePlaybackPlan<String>()
        val saved = candidate("saved", video(), 1)
        val higher = candidate("higher", video("Other"), 20)
        val prefs = SeriesSourcePreferences(video = video())
        plan.attempted(saved.id)
        plan.failed(saved.id, saved.video)
        assertEquals("higher", plan.ranked(listOf(higher, saved), prefs).first().id)
        plan.attempted(higher.id)
        assertTrue(plan.ranked(listOf(saved, higher), prefs).isEmpty())
        assertEquals("late", plan.ranked(listOf(saved, higher, candidate("late")), prefs).single().id)
    }

    @Test fun `avoid consecutive failed host variants and do not retry attempted URLs`() {
        val plan = SourcePlaybackPlan<String>()
        val prefs = SeriesSourcePreferences(video = video())
        val first = candidate("one")
        val variant = candidate("variant", video(quality = 720))
        val other = candidate("other", video("Other", host = "other.example"), 1)
        plan.attempted(first.id)
        plan.failed(first.id, first.video)
        plan.failed(first.id, first.video) // Duplicate errors must not count twice.
        val ranked = plan.ranked(listOf(first, variant, other), prefs)
        assertEquals(listOf("other", "variant"), ranked.map { it.id })
        plan.failed("other-failed-url", other.video)
        // One failure per family is now tied. Duplicating the first error must
        // not add another penalty and defeat the explicit preference.
        assertEquals("variant", plan.ranked(listOf(variant, other), prefs).first().id)
    }

    @Test fun `automatic working hint never replaces available manual preference`() {
        val manual = video("Manual")
        val working = video("Working")
        val prefs = SeriesSourcePreferences(video = manual, lastWorkingVideo = working)
        val links = listOf(candidate("working", working, 50), candidate("manual", manual, 1))
        assertEquals("manual", SourcePlaybackPlan<String>().ranked(links, prefs).first().id)
        assertEquals("working", SourcePlaybackPlan<String>().ranked(links,
            prefs.copy(video = null)).first().id)
    }

    @Test fun `refreshed objects with the same episode URL are not retried`() {
        data class Link(val url: String, val label: String)
        val plan = SourcePlaybackPlan<Link> { it.url }
        val first = Link("episode-url", "Original")
        plan.attempted(first)
        val refreshed = SourcePlaybackPlan.Candidate(Link("episode-url", "New label"), video(), 50)
        assertTrue(plan.ranked(listOf(refreshed), SeriesSourcePreferences()).isEmpty())
    }

    @Test fun `new episode gets new attempt history`() {
        val links = listOf(candidate("source"))
        val firstEpisode = SourcePlaybackPlan<String>()
        firstEpisode.attempted("source")
        assertTrue(firstEpisode.ranked(links, SeriesSourcePreferences()).isEmpty())
        assertEquals("source", SourcePlaybackPlan<String>().ranked(links, SeriesSourcePreferences()).single().id)
    }
}
