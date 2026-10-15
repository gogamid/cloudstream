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
    private fun ready(plan: SourcePlaybackPlan<String>, now: Long,
                      candidates: List<SourcePlaybackPlan.Candidate<String>>,
                      preferences: SeriesSourcePreferences = SeriesSourcePreferences(),
                      collecting: Boolean = true, canSkip: Boolean = true) =
        plan.ready(now, candidates, preferences, collecting, canSkip, instantPriority = 10)

    @Test fun `saved choice wins over a higher priority early mirror`() {
        val plan = SourcePlaybackPlan<String>()
        val preference = SeriesSourcePreferences(video = video())
        val early = candidate("early", video("Other"), 20)
        assertFalse(ready(plan, 0, listOf(early), preference))
        val saved = candidate("saved", video(name = "Mirror S01E02 1080p"), 1)
        assertTrue(ready(plan, 700, listOf(early, saved), preference))
        assertEquals("saved", plan.ranked(listOf(early, saved), preference).first().id)
    }

    @Test fun `missing saved source has a bounded wait which arrivals do not extend`() {
        val plan = SourcePlaybackPlan<String>()
        val preference = SeriesSourcePreferences(video = video())
        val other = candidate("other", video("Other"), 5)
        assertFalse(ready(plan, 1000, listOf(other), preference))
        assertFalse(ready(plan, 3499, listOf(other, candidate("later", video("Third"))), preference))
        assertTrue(ready(plan, 3500, listOf(other), preference))
    }

    @Test fun `weak provider match retains preferred quality but waits for actual host`() {
        val plan = SourcePlaybackPlan<String>()
        val prefs = SeriesSourcePreferences(video = video(name = "Old filename", host = null))
        val sameQuality = candidate("1080", video(name = "New filename", host = "new.example"), 1)
        val differentQuality = candidate("2160", video(name = "Other filename", quality = 2160), 20)
        val links = listOf(differentQuality, sameQuality)
        assertFalse(ready(plan, 0, links, prefs))
        assertTrue(ready(plan, 2500, links, prefs))
        assertEquals("1080", plan.ranked(links, prefs).first().id)
    }

    @Test fun `without preference settle briefly and then play during extraction`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("first"))
        assertFalse(ready(plan, 100, links))
        assertFalse(ready(plan, 849, links))
        assertTrue(ready(plan, 850, links))
    }

    @Test fun `skip and completion start immediately but never select disallowed links`() {
        val plan = SourcePlaybackPlan<String>()
        val preferences = SeriesSourcePreferences(video = video())
        val links = listOf(candidate("other", video("Other")))
        plan.skipRequested = true
        assertTrue(ready(plan, 0, links, preferences))
        assertFalse(ready(plan, 0, listOf(candidate("hidden", allowed = false)), preferences))
        assertFalse(ready(plan, 0, emptyList(), preferences))
        assertTrue(ready(SourcePlaybackPlan(), 0, links, preferences, collecting = false))
    }

    @Test fun `providers which cannot skip are allowed to complete extraction`() {
        val plan = SourcePlaybackPlan<String>()
        val links = listOf(candidate("saved", priority = 100))
        assertFalse(ready(plan, 0, links, canSkip = false))
        assertFalse(ready(plan, 100000, links, canSkip = false))
        assertTrue(ready(plan, 100001, links, collecting = false, canSkip = false))
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
