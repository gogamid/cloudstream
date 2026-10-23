package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SourceStartupTimeout
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class SourceStartupTimeoutTest {
    @Test fun `configured ten seconds replaces rather than adds to five second default`() {
        val timeout = SourceStartupTimeout(true, SourceStartupTimeout.milliseconds(10))
        assertFalse(timeout.tick(5_000, true, false))
        assertFalse(timeout.tick(4_999, true, false))
        assertTrue(timeout.tick(1, true, false))
    }

    @Test fun `custom one to one hundred twenty seconds expire at their own boundary`() {
        for (seconds in 1..120) {
            val limit = SourceStartupTimeout.milliseconds(seconds)
            val timeout = SourceStartupTimeout(true, limit)
            assertFalse(timeout.tick(limit - 1, true, false))
            assertTrue(timeout.tick(1, true, false))
        }
    }

    @Test fun `each automatic fallback receives a fresh configured budget`() {
        val first = SourceStartupTimeout(true, 15_000)
        assertTrue(first.tick(15_000, true, false))
        val next = SourceStartupTimeout(true, 15_000)
        assertFalse(next.tick(5_000, true, false))
        assertFalse(next.tick(9_999, true, false))
        assertTrue(next.tick(1, true, false))
    }

    @Test fun `zero disables timeout rather than immediately expiring`() {
        val timeout = SourceStartupTimeout(true, SourceStartupTimeout.milliseconds(0))
        repeat(3) { assertFalse(timeout.tick(Long.MAX_VALUE, true, false)) }
    }

    @Test fun `configured short wait never imposes a manual source cutoff`() {
        val timeout = SourceStartupTimeout(false, 1_000)
        assertFalse(timeout.tick(Long.MAX_VALUE, true, false))
    }

    @Test fun `custom wait remains disabled after actual playback begins`() {
        val timeout = SourceStartupTimeout(true, 30_000)
        assertFalse(timeout.tick(29_999, true, false))
        assertFalse(timeout.tick(1, true, true))
        assertFalse(timeout.tick(Long.MAX_VALUE, true, false))
    }

    @Test fun `custom wait excludes pause dialog background and interactive seek intervals`() {
        val timeout = SourceStartupTimeout(true, 10_000)
        assertFalse(timeout.tick(4_000, true, false))
        repeat(4) { assertFalse(timeout.tick(600_000, false, false)) }
        assertFalse(timeout.tick(5_999, true, false))
        assertTrue(timeout.tick(1, true, false))
    }

    @Test fun `negative stored seconds fall back safely to the existing default`() {
        assertEquals(5_000L, SourceStartupTimeout.milliseconds(-1))
        assertEquals(5_000L, SourceStartupTimeout.milliseconds(Int.MIN_VALUE))
        val timeout = SourceStartupTimeout(true, -1)
        assertFalse(timeout.tick(4_999, true, false))
        assertTrue(timeout.tick(1, true, false))
    }

    @Test fun `positive restored waits beyond current slider range are respected`() {
        assertEquals(600_000L, SourceStartupTimeout.milliseconds(600))
        assertEquals(Int.MAX_VALUE.toLong() * 1_000L, SourceStartupTimeout.milliseconds(Int.MAX_VALUE))
    }

    @Test fun `negative clock delta cannot reduce elapsed startup time`() {
        val timeout = SourceStartupTimeout(true, 10_000)
        assertFalse(timeout.tick(9_999, true, false))
        assertFalse(timeout.tick(-5_000, true, false))
        assertTrue(timeout.tick(1, true, false))
    }

    @Test fun `large clock deltas saturate instead of overflowing`() {
        val timeout = SourceStartupTimeout(true, Long.MAX_VALUE)
        assertFalse(timeout.tick(Long.MAX_VALUE - 1, true, false))
        assertTrue(timeout.tick(Long.MAX_VALUE, true, false))
        assertTrue(timeout.tick(100, true, false))
    }

    @Test fun `new wait preference is included in backup and restore`() {
        assertTrue("source_startup_wait_seconds".isTransferable())
    }

    @Test fun `automatic sources expire at five seconds`() {
        val timeout = SourceStartupTimeout(true)
        assertFalse(timeout.tick(4999, true, false))
        assertTrue(timeout.tick(1, true, false))
    }

    @Test fun `buffering automatic source expires with repeated checks`() {
        val timeout = SourceStartupTimeout(true)
        // Autoplay intent is not actual playback. Keep passing false until the
        // backend reports that it has really started, including after Skip loading.
        repeat(49) { assertFalse(timeout.tick(100, true, playing = false)) }
        assertTrue(timeout.tick(100, true, playing = false))
        // Each fallback source gets a fresh independent five-second budget.
        val next = SourceStartupTimeout(true)
        assertFalse(next.tick(100, true, playing = false))
        assertTrue(next.tick(4900, true, playing = false))
    }

    @Test fun `manual sources have no short startup deadline`() {
        val timeout = SourceStartupTimeout(false)
        assertFalse(timeout.tick(600000, true, false))
    }

    @Test fun `playing once prevents timeout during later buffering`() {
        val timeout = SourceStartupTimeout(true)
        assertFalse(timeout.tick(4000, true, false))
        assertFalse(timeout.tick(100, true, true))
        assertFalse(timeout.tick(600000, true, false))
    }

    @Test fun `background and dialog intervals do not count`() {
        val timeout = SourceStartupTimeout(true)
        assertFalse(timeout.tick(4000, true, false))
        assertFalse(timeout.tick(600000, false, false))
        assertFalse(timeout.tick(999, true, false))
        assertTrue(timeout.tick(1, true, false))
    }
}
