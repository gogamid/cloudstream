package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SubtitleStartupGate
import org.junit.Assert.*
import org.junit.Test

class SubtitleStartupGateTest {
    @Test fun `initial external subtitle is queued until video is ready`() {
        val gate = SubtitleStartupGate<String>()
        assertNull(gate.request("english", false))
        assertEquals(SubtitleStartupGate.Request("english", false), gate.videoReady(gate.token))
    }

    @Test fun `newest selection during video preparation wins`() {
        val gate = SubtitleStartupGate<String>()
        assertNull(gate.request("english", false))
        assertNull(gate.request("russian", true))
        assertEquals(SubtitleStartupGate.Request("russian", true), gate.videoReady(gate.token))
    }

    @Test fun `automatic callbacks cannot silence a queued explicit selection`() {
        val gate = SubtitleStartupGate<String> { it.substringBefore('#') }
        gate.request("english#original", true)
        gate.request("english#refreshed", false)
        assertEquals(SubtitleStartupGate.Request("english#refreshed", true), gate.videoReady(gate.token))
    }

    @Test fun `off or embedded selection discards pending external download`() {
        val gate = SubtitleStartupGate<String>()
        gate.request("external", true)
        gate.cancelPending()
        assertNull(gate.videoReady(gate.token))
    }

    @Test fun `ready starts subtitles even when video starts paused`() {
        val gate = SubtitleStartupGate<String>()
        gate.request("english", false)
        // No isPlaying/first-frame condition: READY also works with playWhenReady=false.
        assertNotNull(gate.videoReady(gate.token))
    }

    @Test fun `later independent subtitle changes do not wait on rebuffering`() {
        val gate = SubtitleStartupGate<String>()
        gate.videoReady(gate.token)
        assertEquals(SubtitleStartupGate.Request("english", true), gate.request("english", true))
        assertEquals(SubtitleStartupGate.Request("russian", true), gate.request("russian", true))
        assertNull(gate.videoReady(gate.token))
    }

    @Test fun `duplicate ready events drain a pending request only once`() {
        val gate = SubtitleStartupGate<String>()
        gate.request("english", false)
        val token = gate.token
        assertNotNull(gate.videoReady(token))
        assertNull(gate.videoReady(token))
    }

    @Test fun `source replacement resets readiness and drops stale subtitle work`() {
        val gate = SubtitleStartupGate<String>()
        gate.request("old", true)
        val oldToken = gate.token
        gate.reset()
        assertNull(gate.videoReady(oldToken))
        assertNull(gate.request("new", false))
        assertNull(gate.videoReady(oldToken))
        assertEquals(SubtitleStartupGate.Request("new", false), gate.videoReady(gate.token))
    }

    @Test fun `release after successful preparation still gates the next source`() {
        val gate = SubtitleStartupGate<String>()
        gate.videoReady(gate.token)
        val oldToken = gate.token
        gate.reset()
        assertNull(gate.request("next episode", false))
        assertNull(gate.videoReady(oldToken))
        assertEquals("next episode", gate.videoReady(gate.token)?.value)
    }
}
