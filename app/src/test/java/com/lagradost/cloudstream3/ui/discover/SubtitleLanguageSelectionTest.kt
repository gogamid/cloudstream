package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.player.SubtitleLanguageSelection
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class SubtitleLanguageSelectionTest {
    private fun sub(code: String?, name: String = "Track", origin: SubtitleOrigin = SubtitleOrigin.URL) =
        SubtitleData(name, "", "test:$name:$code", origin, "application/x-subrip", emptyMap(), code)

    @Test fun `matches ISO codes and regional tags`() {
        for (code in listOf("ru", "rus", "RU-ru", "ru_RU")) {
            val subtitle = sub(code)
            assertEquals(subtitle, SubtitleLanguageSelection.first(listOf(subtitle), "ru"))
        }
        for (code in listOf("en", "eng", "EN-us", "en_GB")) {
            val subtitle = sub(code)
            assertEquals(subtitle, SubtitleLanguageSelection.first(listOf(subtitle), "en"))
        }
    }

    @Test fun `matches named tracks with absent language metadata`() {
        for ((name, wanted) in listOf("Russian" to "ru", "Русский" to "ru", "English SDH" to "en")) {
            val subtitle = sub(null, name)
            assertEquals(subtitle, SubtitleLanguageSelection.first(listOf(subtitle), wanted))
        }
    }

    @Test fun `first matching available track wins regardless of origin`() {
        val english = sub("en")
        val first = sub("rus", "Russian 1", SubtitleOrigin.EMBEDDED_IN_VIDEO)
        val second = sub("ru", "Russian 2", SubtitleOrigin.DOWNLOADED_FILE)
        assertEquals(first, SubtitleLanguageSelection.first(listOf(english, first, second), "ru"))
        assertEquals(second, SubtitleLanguageSelection.first(listOf(second, first), "ru"))
    }

    @Test fun `missing language returns no selection and explicit metadata wins over name`() {
        assertNull(SubtitleLanguageSelection.first(emptyList(), "ru"))
        assertNull(SubtitleLanguageSelection.first(listOf(sub("en")), "ru"))
        assertNull(SubtitleLanguageSelection.first(listOf(sub("de", "Russian")), "ru"))
    }

    @Test fun `cycles matching tracks in order and wraps for both languages`() {
        for (language in listOf("en", "ru")) {
            val first = sub(language, "First")
            val second = sub(language, "Second", SubtitleOrigin.EMBEDDED_IN_VIDEO)
            val third = sub(language, "Third", SubtitleOrigin.DOWNLOADED_FILE)
            val other = sub("de", "German")
            val tracks = listOf(first, other, second, third)
            assertEquals(first, SubtitleLanguageSelection.next(tracks, language, null))
            assertEquals(first, SubtitleLanguageSelection.next(tracks, language, other))
            assertEquals(second, SubtitleLanguageSelection.next(tracks, language, first))
            assertEquals(third, SubtitleLanguageSelection.next(tracks, language, second))
            assertEquals(first, SubtitleLanguageSelection.next(tracks, language, third))
        }
    }

    @Test fun `single missing removed and refreshed tracks are safe`() {
        val first = sub("en", "First")
        val second = sub("en", "Second")
        assertEquals(first, SubtitleLanguageSelection.next(listOf(first), "en", first))
        assertNull(SubtitleLanguageSelection.next(listOf(first), "ru", first))
        assertNull(SubtitleLanguageSelection.next(emptyList(), "en", first))
        assertEquals(second, SubtitleLanguageSelection.next(listOf(second), "en", first))
        assertEquals(second, SubtitleLanguageSelection.next(listOf(first, first, second), "en",
            first.copy(headers = mapOf("Referer" to "updated"))))
    }

    @Test fun `new action names survive stored mapping decoding`() {
        val result = RemoteButtonMappings.decode(setOf(
            "${KeyEvent.KEYCODE_1}:RUSSIAN_SUBTITLES", "${KeyEvent.KEYCODE_2}:ENGLISH_SUBTITLES"))
        assertEquals(RemoteButtonMappings.Action.RUSSIAN_SUBTITLES, result[KeyEvent.KEYCODE_1])
        assertEquals(RemoteButtonMappings.Action.ENGLISH_SUBTITLES, result[KeyEvent.KEYCODE_2])
    }
}
