@file:Suppress("OPT_IN_USAGE")

package com.lagradost.cloudstream3.ui.discover

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.lagradost.cloudstream3.ui.player.DynamicRangeCompressor
import com.lagradost.cloudstream3.ui.player.PlaybackIntent
import com.lagradost.cloudstream3.ui.player.SubtitleDelayState
import com.lagradost.cloudstream3.utils.ComposeTextFocus
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Contracts at upstream/fork boundaries; not a substitute for device UI/playback tests. */
class UpstreamIntegrationTest {
    @Test fun `start paused applies to a fresh episode without treating buffering as pause`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true, true, startPaused = true, sameEpisode = false)
        assertFalse(intent.value)
        intent.request(true)
        intent.beginLoad(true, true, startPaused = true, sameEpisode = true)
        assertTrue(intent.value)
    }

    @Test fun `source fallback must not undo a deliberate pause`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true, true)
        intent.request(false)
        intent.beginLoad(true, false, startPaused = false, sameEpisode = true)
        assertFalse(intent.value)
    }

    @Test fun `first manual source is not mistaken for a paused source replacement`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true, false, startPaused = false, sameEpisode = true)
        assertTrue(intent.value)
    }

    @Test fun `first manual source still honors start paused despite same episode flag`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true, false, startPaused = true, sameEpisode = true)
        assertFalse(intent.value)
    }

    @Test fun `next episode reapplies start paused but normal autoplay still works`() {
        val intent = PlaybackIntent()
        intent.request(true)
        intent.beginLoad(true, true, startPaused = true, sameEpisode = false)
        assertFalse(intent.value)
        intent.beginLoad(true, false, startPaused = false, sameEpisode = false)
        assertTrue(intent.value)
    }

    @Test fun `manual resume survives backend replacement with start paused enabled`() {
        val intent = PlaybackIntent()
        intent.beginLoad(true, true, startPaused = true)
        val oldToken = intent.token
        intent.request(true)
        intent.beginLoad(null, true, startPaused = true, sameEpisode = true)
        intent.observeBackend(oldToken, false)
        assertTrue(intent.value)
    }

    @Test fun `disposing one Compose editor cannot clear another editor focus guard`() {
        val first = Any()
        val second = Any()
        try {
            ComposeTextFocus.update(first, true)
            ComposeTextFocus.update(second, true)
            ComposeTextFocus.update(first, false)
            assertTrue(ComposeTextFocus.isTyping)
            ComposeTextFocus.update(second, false)
            assertFalse(ComposeTextFocus.isTyping)
        } finally {
            ComposeTextFocus.update(first, false)
            ComposeTextFocus.update(second, false)
        }
    }

    @Test fun `legacy subtitle UI state preserves its actual native offset on restoration`() {
        assertEquals(-1000L, SubtitleDelayState.restore(1000L, 0))
        assertEquals(1000L, SubtitleDelayState.restore(-1000L, 0))
        assertEquals(0L, SubtitleDelayState.restore(0, 0))
    }

    @Test fun `new subtitle offset state is not inverted again`() {
        assertEquals(1000L, SubtitleDelayState.restore(1000L, SubtitleDelayState.VERSION))
        assertEquals(-1000L, SubtitleDelayState.restore(-1000L, SubtitleDelayState.VERSION))
    }

    private fun samples(vararg values: Short): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).also { buffer ->
            values.forEach { buffer.putShort(it) }
            buffer.flip()
        }

    private fun processor() = DynamicRangeCompressor().also {
        it.configure(AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
    }

    @Test fun `disabled compressor is exact PCM passthrough`() {
        val compressor = processor()
        val input = samples(0, 32767, -32768, 42)
        compressor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = compressor.getOutput()
        assertEquals(listOf<Short>(0, 32767, -32768, 42), List(4) { output.short })
    }

    @Test fun `compressor reuses drained storage across input chunks and seeks`() {
        val compressor = processor()
        compressor.queueInput(samples(1, 2))
        val first = compressor.getOutput()
        first.position(first.limit())
        compressor.queueInput(samples(3, 4))
        val second = compressor.getOutput()
        assertSame(first, second)
        assertEquals(3.toShort(), second.short)
        second.position(second.limit())
        compressor.flush()
        compressor.queueInput(samples(5, 6))
        assertSame(first, compressor.getOutput())
    }

    @Test fun `boosted positive PCM peaks saturate instead of wrapping negative`() {
        val compressor = processor().apply { enabled = true; ratio = 1f; makeupGain = 24f }
        compressor.queueInput(samples(32767, -32768))
        val output = compressor.getOutput()
        assertEquals(32767.toShort(), output.short)
        assertEquals((-32768).toShort(), output.short)
    }

    @Test fun `unsupported format cannot retain a previously active PCM configuration`() {
        val compressor = processor()
        assertTrue(compressor.isActive())
        compressor.configure(AudioFormat(44100, 1, C.ENCODING_PCM_8BIT))
        assertFalse(compressor.isActive())
    }

    @Test fun `end of stream and reset clear pending audio without discarding normal seek storage`() {
        val compressor = processor()
        compressor.queueInput(samples(1))
        compressor.queueEndOfStream()
        assertFalse(compressor.isEnded())
        val output = compressor.getOutput()
        output.position(output.limit())
        assertTrue(compressor.isEnded())
        compressor.reset()
        assertFalse(compressor.isActive())
        assertFalse(compressor.isEnded())
    }
}
