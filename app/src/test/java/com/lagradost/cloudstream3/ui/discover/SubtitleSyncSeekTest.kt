package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SubtitleSyncSeek
import org.junit.Assert.*
import org.junit.Test

class SubtitleSyncSeekTest {
    @Test fun `refresh stays at video position in milliseconds`() {
        assertEquals(600001L, SubtitleSyncSeek.target(600000L, 7200000L))
        assertEquals(1L, SubtitleSyncSeek.target(0L, 7200000L))
    }

    @Test fun `end position stays within the timeline and still changes`() {
        assertEquals(7199998L, SubtitleSyncSeek.target(7200000L, 7200000L))
        assertEquals(7199998L, SubtitleSyncSeek.target(7199999L, 7200000L))
        assertEquals(0L, SubtitleSyncSeek.target(0L, 1L))
    }

    @Test fun `unset duration and invalid positions are safe`() {
        assertEquals(600001L, SubtitleSyncSeek.target(600000L, Long.MIN_VALUE + 1L))
        assertEquals(1L, SubtitleSyncSeek.target(-1L, 7200000L))
        assertTrue(SubtitleSyncSeek.target(Long.MAX_VALUE, -1L) >= 0L)
    }
}
