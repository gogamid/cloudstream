package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SourceStartupTimeout
import org.junit.Assert.*
import org.junit.Test

class SourceStartupTimeoutTest {
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
