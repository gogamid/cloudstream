package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SeriesSourcePreferences
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.player.SubtitleToggleSelection
import org.junit.Assert.*
import org.junit.Test

class SubtitleToggleSelectionTest {
    private fun sub(name: String = "English", url: String = "test:english",
                    origin: SubtitleOrigin = SubtitleOrigin.URL) =
        SubtitleData(name, "", url, origin, "application/x-subrip", emptyMap(), "en")

    @Test fun `off remembers the active or pending track even before descriptors arrive`() {
        val state = SubtitleToggleSelection()
        val track = sub()
        assertNull(state.target(track, emptyList(), null))
        assertEquals(track, state.target(null, listOf(track), null))
    }

    @Test fun `repeated toggles restore selected track instead of cycling sources`() {
        val state = SubtitleToggleSelection()
        val track = sub()
        val other = sub("Other", "test:other")
        repeat(3) {
            assertNull(state.target(track, listOf(other, track), other))
            assertEquals(track, state.target(null, listOf(other, track), other))
        }
    }

    @Test fun `restore uses refreshed available descriptor not cached headers`() {
        val state = SubtitleToggleSelection()
        val track = sub()
        state.remember(track)
        val refreshed = track.copy(headers = mapOf("Referer" to "updated"))
        assertSame(refreshed, state.target(null, listOf(refreshed), null))
    }

    @Test fun `stable descriptor can restore a changed URL or embedded track id`() {
        for (origin in SubtitleOrigin.entries) {
            val state = SubtitleToggleSelection()
            state.remember(sub(origin = origin))
            val refreshed = sub(url = "test:new-source", origin = origin)
            assertSame(refreshed, state.target(null, listOf(refreshed), null))
        }
    }

    @Test fun `empty list does not reuse stale URL or forget choice for late arrivals`() {
        val state = SubtitleToggleSelection()
        val track = sub()
        state.remember(track)
        assertNull(state.target(null, emptyList(), track))
        assertEquals(track, state.target(null, listOf(track), null))
    }

    @Test fun `without remembered track use saved preferred choice then display order`() {
        val state = SubtitleToggleSelection()
        val first = sub()
        val preferred = sub("Preferred", "test:preferred")
        assertEquals(preferred, state.target(null, listOf(first, preferred), preferred))
        assertEquals(first, state.target(null, listOf(first, preferred), null))
    }

    @Test fun `unavailable preference cannot introduce a new URL`() {
        val state = SubtitleToggleSelection()
        val available = sub()
        val unavailable = sub("Gone", "test:gone")
        assertEquals(available, state.target(null, listOf(available), unavailable))
        assertNull(state.target(null, emptyList(), unavailable))
    }

    @Test fun `off does not erase the remembered choice`() {
        val state = SubtitleToggleSelection()
        val track = sub()
        state.remember(track)
        state.remember(null)
        assertEquals(track, state.target(null, listOf(track), null))
    }

    @Test fun `new episode clears remembered selection before choosing another default`() {
        val state = SubtitleToggleSelection()
        val first = sub()
        val second = sub("Second", "test:second")
        state.remember(first)
        assertEquals(first, state.target(null, listOf(first, second), second))
        state.clear()
        assertEquals(second, state.target(null, listOf(first, second), second))
    }

    @Test fun `explicit off retains series descriptor and unrelated source preferences`() {
        val descriptor = SeriesSourcePreferences.Subtitle.from(sub())
        val source = SeriesSourcePreferences.Video("Host", "Mirror", 1080, "VIDEO")
        val preferences = SeriesSourcePreferences(video = source, subtitle = descriptor, lastWorkingVideo = source)
        val off = preferences.withSubtitleSelection(null)
        assertTrue(off.subtitlesOff)
        assertEquals(descriptor, off.subtitle)
        assertEquals(source, off.video)
        assertEquals(source, off.lastWorkingVideo)
        assertEquals(preferences, off.withSubtitleSelection(descriptor))
    }

    @Test fun `embedded tracks from an earlier mirror are excluded without hiding external files`() {
        val state = SubtitleToggleSelection()
        val stale = sub(url = "old-id", origin = SubtitleOrigin.EMBEDDED_IN_VIDEO)
        val native = sub(url = "current-id", origin = SubtitleOrigin.EMBEDDED_IN_VIDEO)
        val external = sub()
        val downloaded = sub(origin = SubtitleOrigin.DOWNLOADED_FILE)
        assertEquals(listOf(external, downloaded, native), state.available(
            listOf(stale, external, downloaded, native), setOf("current-id")))
    }

    @Test fun `source switch restores fresh native descriptor instead of accumulated stale id`() {
        val state = SubtitleToggleSelection()
        val stale = sub(url = "old-id", origin = SubtitleOrigin.EMBEDDED_IN_VIDEO)
        val native = sub(url = "current-id", origin = SubtitleOrigin.EMBEDDED_IN_VIDEO)
        state.remember(stale)
        val available = state.available(listOf(stale, native), setOf("current-id"))
        assertSame(native, state.target(null, available, stale))
    }

    @Test fun `legacy off without a descriptor stays compatible and explicit on records new track`() {
        val legacy = SeriesSourcePreferences(subtitlesOff = true)
        assertNull(legacy.withSubtitleSelection(null).subtitle)
        val descriptor = SeriesSourcePreferences.Subtitle.from(sub())
        assertEquals(SeriesSourcePreferences(subtitle = descriptor), legacy.withSubtitleSelection(descriptor))
    }
}
