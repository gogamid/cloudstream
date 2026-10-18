package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.ExternalSubtitleCache
import com.lagradost.cloudstream3.ui.player.ExternalSubtitleLimits
import com.lagradost.cloudstream3.ui.player.SubtitleSelectionSession
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class SeamlessSubtitlesTest {
    @Test fun `rapid A B C requests only publish C`() {
        val state = SubtitleSelectionSession()
        val a = state.request("A")
        val b = state.request("B")
        val c = state.request("C")
        assertEquals("C", state.requestedId)
        assertFalse(state.commit(a))
        assertFalse(state.commit(b))
        assertTrue(state.commit(c))
        assertEquals("C", state.activeId)
    }

    @Test fun `pending selection advances before it finishes for language cycling`() {
        val state = SubtitleSelectionSession()
        state.request("English 1")
        assertEquals("English 1", state.requestedId)
        assertNull(state.activeId)
        state.request("English 2")
        assertEquals("English 2", state.requestedId)
    }

    @Test fun `old error cannot undo a newer subtitle`() {
        val state = SubtitleSelectionSession()
        val old = state.request("Russian 1")
        val newest = state.request("Russian 2")
        assertTrue(state.commit(newest))
        assertFalse(state.fail(old))
        assertEquals("Russian 2", state.activeId)
        assertEquals("Russian 2", state.requestedId)
    }

    @Test fun `failed new track preserves previous active track`() {
        val state = SubtitleSelectionSession()
        assertTrue(state.commit(state.request("working")))
        val failed = state.request("broken")
        assertEquals("working", state.activeId)
        assertTrue(state.fail(failed))
        assertEquals("working", state.activeId)
        assertEquals("working", state.requestedId)
    }

    @Test fun `first failure rolls back to subtitles off`() {
        val state = SubtitleSelectionSession()
        assertTrue(state.fail(state.request("broken")))
        assertNull(state.activeId)
        assertNull(state.requestedId)
    }

    @Test fun `off invalidates pending download`() {
        val state = SubtitleSelectionSession()
        val pending = state.request("track")
        state.request(null)
        assertFalse(state.accepts(pending))
        assertFalse(state.commit(pending))
        assertFalse(state.fail(pending))
        assertNull(state.activeId)
    }

    @Test fun `completed or failed requests cannot publish a second time`() {
        val state = SubtitleSelectionSession()
        val first = state.request("working")
        assertTrue(state.commit(first))
        assertFalse(state.fail(first))
        val second = state.request("broken")
        assertTrue(state.fail(second))
        assertFalse(state.commit(second))
        assertEquals("working", state.activeId)
    }

    @Test fun `same URL in new episode still invalidates older work`() {
        val state = SubtitleSelectionSession()
        val firstEpisode = state.request("same-url")
        state.request(null)
        val nextEpisode = state.request("same-url")
        assertFalse(state.commit(firstEpisode))
        assertTrue(state.commit(nextEpisode))
    }

    @Test fun `cached cycling uses two most recently accessed tracks`() {
        val cache = ExternalSubtitleCache<Int>()
        cache.put("A", 1, 100)
        cache.put("B", 2, 100)
        assertEquals(1, cache["A"])
        cache.put("C", 3, 100)
        assertNull(cache["B"])
        assertEquals(1, cache["A"])
        assertEquals(3, cache["C"])
        assertEquals(2, cache.size)
        assertEquals(200, cache.bytes)
    }

    @Test fun `cache also bounds bytes and replacement accounting`() {
        val cache = ExternalSubtitleCache<Int>()
        cache.put("A", 1, ExternalSubtitleLimits.MAX_CACHE_BYTES)
        cache.put("B", 2, 10)
        assertNull(cache["A"])
        assertEquals(10, cache.bytes)
        cache.put("B", 3, 20)
        assertEquals(1, cache.size)
        assertEquals(20, cache.bytes)
        cache.clear()
        assertEquals(0, cache.bytes)
        assertEquals(0, cache.size)
    }

    @Test fun `sync offset adjusts the independent lookup clock in either direction`() {
        val videoPositionMs = 10_000L
        assertEquals(10_000_000L, ExternalSubtitleLimits.positionUs(videoPositionMs, 0))
        assertEquals(9_000_000L, ExternalSubtitleLimits.positionUs(videoPositionMs, -1000))
        assertEquals(11_000_000L, ExternalSubtitleLimits.positionUs(videoPositionMs, 1000))
        assertEquals(-1_000_000L, ExternalSubtitleLimits.positionUs(0, -1000))
    }

    @Test fun `large user offsets cannot overflow subtitle clock`() {
        assertTrue(ExternalSubtitleLimits.positionUs(100, Long.MAX_VALUE) > 0)
        assertTrue(ExternalSubtitleLimits.positionUs(0, Long.MIN_VALUE) < 0)
        assertTrue(ExternalSubtitleLimits.positionUs(Long.MIN_VALUE, -100) < 0)
    }

    @Test fun `subtitle stream retains Cyrillic bytes and respects slice-free loading`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nПривет мир\n"
        for (charset in listOf(Charsets.UTF_8, charset("windows-1251"))) {
            val bytes = text.toByteArray(charset)
            assertArrayEquals(bytes, ExternalSubtitleLimits.read(ByteArrayInputStream(bytes)))
        }
    }

    @Test fun `oversized files fail in the subtitle loader`() {
        val bytes = ByteArray(ExternalSubtitleLimits.MAX_FILE_BYTES + 1)
        try {
            ExternalSubtitleLimits.read(ByteArrayInputStream(bytes))
            fail("Oversized subtitle must not be accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `canceled read stops before another chunk`() {
        var checks = 0
        try {
            ExternalSubtitleLimits.read(ByteArrayInputStream(ByteArray(32_768))) {
                checks += 1
                if (checks == 2) throw CancellationException()
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(2, checks)
    }
}
