package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.PlaybackIntent
import org.junit.Assert.*
import org.junit.Test

class PlaybackIntentTest {
    @Test fun `first load requests autoplay whether collection was skipped or completed`() {
        repeat(2) {
            val intent = PlaybackIntent()
            intent.detachPlayer() // Initial collection/reset has no active player.
            intent.beginLoad(autoPlay = true, backendIntent = false)
            assertTrue(intent.value)
        }
    }

    @Test fun `buffering is not a pause request`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true)
        // Backend isPlaying=false during preparation, but playWhenReady=true.
        intent.observeBackend(intent.token, playWhenReady = true)
        assertTrue(intent.value)
        intent.beginLoad(null)
        assertTrue(intent.value)
    }

    @Test fun `old teardown cannot override new autoplay request`() {
        val intent = PlaybackIntent()
        intent.beginLoad(false)
        val old = intent.token
        intent.detachPlayer()
        intent.beginLoad(true)
        intent.observeBackend(old, false)
        assertTrue(intent.value)
    }

    @Test fun `released backend callbacks cannot change retained intent`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true)
        val token = intent.token
        intent.detachPlayer()
        intent.observeBackend(token, false)
        assertTrue(intent.value)
    }

    @Test fun `intentional paused source and reload remain paused`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true)
        intent.request(false)
        intent.detachPlayer()
        intent.beginLoad(autoPlay = null, backendIntent = false)
        assertFalse(intent.value)
        intent.beginLoad(autoPlay = false)
        assertFalse(intent.value)
    }

    @Test fun `playing reload preserves backend intent even if actual playback is buffering`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true)
        intent.detachPlayer()
        intent.beginLoad(autoPlay = null, backendIntent = true)
        assertTrue(intent.value)
    }

    @Test fun `current media controls update intent but stale controls do not`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true)
        val old = intent.token
        intent.observeBackend(old, false)
        assertFalse(intent.value)
        intent.observeBackend(old, true)
        assertTrue(intent.value)
        intent.beginLoad(false)
        intent.observeBackend(old, true)
        assertFalse(intent.value)
    }

    @Test fun `fresh episode autoplay overrides previous paused episode`() {
        val intent = PlaybackIntent()
        intent.beginLoad(false)
        intent.detachPlayer()
        intent.beginLoad(autoPlay = true, backendIntent = false)
        assertTrue(intent.value)
    }
}
