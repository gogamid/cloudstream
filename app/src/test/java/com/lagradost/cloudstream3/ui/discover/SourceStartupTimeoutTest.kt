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
