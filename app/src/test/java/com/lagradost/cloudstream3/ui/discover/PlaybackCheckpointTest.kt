package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.PlaybackCheckpoint
import org.junit.Assert.*
import org.junit.Test

class PlaybackCheckpointTest {
    @Test fun `active playback receives frequent checkpoints`() {
        assertEquals(5000L, PlaybackCheckpoint.INTERVAL_MS)
        assertTrue(PlaybackCheckpoint.shouldSave(true, true, 600000L, 7200000L))
    }

    @Test fun `loading paused and invalid timelines cannot overwrite progress`() {
        assertFalse(PlaybackCheckpoint.shouldSave(false, true, 600000L, 7200000L))
        assertFalse(PlaybackCheckpoint.shouldSave(true, false, 0L, 7200000L))
        assertFalse(PlaybackCheckpoint.shouldSave(true, true, 0L, 0L))
        assertFalse(PlaybackCheckpoint.shouldSave(true, true, -1L, 7200000L))
        assertFalse(PlaybackCheckpoint.shouldSave(true, true, 7200001L, 7200000L))
        assertFalse(PlaybackCheckpoint.shouldSave(true, true, 0L, Long.MIN_VALUE + 1))
    }

    @Test fun `valid start and end positions can be saved`() {
        assertTrue(PlaybackCheckpoint.shouldSave(true, true, 0L, 7200000L))
        assertTrue(PlaybackCheckpoint.shouldSave(true, true, 7200000L, 7200000L))
    }
}
