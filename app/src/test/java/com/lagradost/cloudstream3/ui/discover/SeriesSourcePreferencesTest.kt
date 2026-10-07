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
