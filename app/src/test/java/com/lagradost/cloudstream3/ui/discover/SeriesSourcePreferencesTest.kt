package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class SeriesSourcePreferencesTest {
    private fun track(url: String, suffix: String = "1", origin: SubtitleOrigin = SubtitleOrigin.URL) =
        SubtitleData("English", suffix, url, origin, "application/x-subrip", emptyMap(), "en")

    @Test fun `subtitle choice follows source across episode URLs`() {
        val choice = SeriesSourcePreferences.Subtitle.from(track("episode1"))
        assertTrue(choice.matches(track("episode2")))
        assertFalse(choice.matches(track("episode2", "2")))
        assertFalse(choice.matches(track("episode2", origin = SubtitleOrigin.EMBEDDED_IN_VIDEO)))
        assertFalse(choice.matches(track("episode2").copy(originalName = "Russian", languageCode = "ru")))
    }

    @Test fun `video matching tolerates changing episode and quality labels`() {
        val saved = SeriesSourcePreferences.Video("Host", "Server S01E01 1080p", 1080, "VIDEO")
        assertTrue(saved.match(saved.copy(name = "Server S01E02 1080p")) >= 3)
        assertTrue(saved.match(saved.copy(name = "Server S02E01 720p", quality = 720)) >= 3)
        assertTrue(saved.match(saved.copy(name = "Server Episode 7 1080p")) >= 3)
        assertEquals(0, saved.match(saved.copy(source = "Different host")))
        // Generic source-only matches must not bypass the preferred-source grace.
        assertEquals(1, saved.match(saved.copy(name = "Different server", quality = 720)))
    }

    @Test fun `host identity survives episode filename changes without saving URLs`() {
        val saved = SeriesSourcePreferences.Video("Provider", "Show S01E01.mkv", 1080, "VIDEO", "cdn.example")
        assertTrue(saved.match(saved.copy(name = "Another file S01E02.mkv")) >= 2)
        assertEquals(2, saved.match(saved.copy(name = "Other", host = "another.example")))
        val prefs = SeriesSourcePreferences(video = saved, lastWorkingVideo = saved.copy(name = "Other"))
        assertEquals(prefs, parseJson<SeriesSourcePreferences>(prefs.toJson()))
        assertFalse(prefs.toJson().contains("https://"))
    }

    @Test fun `old video preferences remain readable`() {
        val prefs = parseJson<SeriesSourcePreferences>("""{"video":{"source":"Host","name":"Host 1080p","quality":1080,"type":"VIDEO"},"subtitlesOff":false}""")
        assertNull(prefs.video?.host)
        assertNull(prefs.lastWorkingVideo)
        assertTrue(prefs.video!!.match(prefs.video!!.copy(host = "cdn.example")) >= 3)
    }

    @Test fun `preferences serialize with stable video descriptors and off state`() {
        val value = SeriesSourcePreferences(
            SeriesSourcePreferences.Video("Host", "Host 1080p", 1080, "VIDEO"),
            SeriesSourcePreferences.Subtitle.from(track("episode1")))
        assertEquals(value, parseJson<SeriesSourcePreferences>(value.toJson()))
        assertEquals(SeriesSourcePreferences(subtitlesOff = true),
            parseJson<SeriesSourcePreferences>(SeriesSourcePreferences(subtitlesOff = true).toJson()))
        assertTrue("0/series_source_preferences/123".isTransferable())
    }
}
