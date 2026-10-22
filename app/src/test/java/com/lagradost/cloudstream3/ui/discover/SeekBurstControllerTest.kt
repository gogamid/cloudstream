package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.PlaybackIntent
import com.lagradost.cloudstream3.ui.player.SeekBurstController
import org.junit.Assert.*
import org.junit.Test

class SeekBurstControllerTest {
    private class Fixture(playing: Boolean = true) {
        val intent = PlaybackIntent().apply { beginLoad(playing) }
        var position = 60_000L
        var duration: Long? = 120_000L
        var backendPlaying = playing
        var pauses = 0
        var changed = 0
        val seeks = mutableListOf<Long>()
        val restored = mutableListOf<Boolean>()
        val previews = mutableListOf<SeekBurstController.Target?>()
        val failures = mutableListOf<Exception>()
        val tasks = linkedMapOf<Runnable, Long>()
        var onSeek: (() -> Unit)? = null
        var failPause = false
        var failSeek = false
        lateinit var controller: SeekBurstController
        init {
            controller = SeekBurstController(
                position = { position }, duration = { duration }, intent = { intent.value },
                pause = {
                    pauses++
                    if (failPause) throw IllegalStateException("pause")
                    backendPlaying = false
                    if (!controller.isChangingPlayback) intent.observeBackend(intent.token, false)
                },
                seek = {
                    if (failSeek) throw IllegalStateException("seek")
                    seeks += it
                    position = it
                    onSeek?.invoke()
                },
                restore = {
                    restored += it
                    backendPlaying = it
                    if (!controller.isChangingPlayback) intent.observeBackend(intent.token, it)
                },
                preview = { previews += it }, schedule = { task, delay -> tasks[task] = delay },
                remove = { tasks.remove(it); Unit }, changed = { changed++ },
                failed = { failures += it },
            )
        }
        fun quiet() {
            val task = tasks.keys.single()
            tasks.remove(task)
            task.run()
        }
    }

    @Test fun `many rewind presses preview one target with a single scheduled task and backend seek`() {
        val f = Fixture()
        repeat(500) { f.controller.add(-100) }
        assertEquals(1, f.pauses)
        assertFalse(f.backendPlaying)
        assertTrue(f.intent.value)
        assertTrue(f.seeks.isEmpty())
        assertEquals(1, f.tasks.size)
        assertEquals(SeekBurstController.QUIET_MS, f.tasks.values.single())
        assertEquals(10_000L, f.previews.last()?.toMs)
        f.quiet()
        assertEquals(listOf(10_000L), f.seeks)
        assertEquals(listOf(true), f.restored)
        assertTrue(f.backendPlaying)
        assertFalse(f.controller.isActive)
        assertNull(f.previews.last())
    }

    @Test fun `initially paused video remains paused`() {
        val f = Fixture(false)
        f.controller.add(-10_000)
        f.quiet()
        assertEquals(listOf(false), f.restored)
        assertFalse(f.backendPlaying)
    }

    @Test fun `explicit pause during a burst overrides automatic resume`() {
        val f = Fixture()
        f.controller.add(-10_000)
        f.intent.request(false)
        f.controller.finish()
        assertEquals(listOf(50_000L), f.seeks)
        assertEquals(listOf(false), f.restored)
        assertTrue(f.tasks.isEmpty())
    }

    @Test fun `explicit play during a paused burst commits and resumes`() {
        val f = Fixture(false)
        f.controller.add(-10_000)
        f.intent.request(true)
        f.controller.finish()
        assertTrue(f.backendPlaying)
        assertEquals(listOf(true), f.restored)
    }

    @Test fun `audio focus loss while picking a target is not undone`() {
        val f = Fixture()
        f.controller.add(-10_000)
        f.intent.observeBackend(f.intent.token, false)
        f.quiet()
        assertFalse(f.backendPlaying)
        assertEquals(listOf(false), f.restored)
    }

    @Test fun `buffering is not interpreted as user pause`() {
        val f = Fixture()
        f.backendPlaying = false
        f.controller.add(-10_000)
        f.quiet()
        assertEquals(listOf(true), f.restored)
    }

    @Test fun `mixed directions accumulate from pending target not unchanged backend position`() {
        val f = Fixture()
        f.controller.add(-20_000)
        f.controller.add(-20_000)
        f.controller.add(5_000)
        assertEquals(60_000L, f.position)
        assertEquals(25_000L, f.previews.last()?.toMs)
        f.quiet()
        assertEquals(listOf(25_000L), f.seeks)
    }

    @Test fun `bounds prevent negative seek and accidental next episode at exact duration`() {
        val f = Fixture()
        f.controller.add(Long.MIN_VALUE)
        assertEquals(0L, f.previews.last()?.toMs)
        f.controller.add(Long.MAX_VALUE)
        assertEquals(119_999L, f.previews.last()?.toMs)
        f.quiet()
        assertEquals(listOf(119_999L), f.seeks)
    }

    @Test fun `huge positive deltas saturate without wrapping backwards`() {
        val f = Fixture()
        f.duration = null
        f.controller.add(Long.MAX_VALUE)
        f.controller.add(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, f.previews.last()?.toMs)
        f.quiet()
        assertEquals(listOf(Long.MAX_VALUE), f.seeks)
    }

    @Test fun `unset duration is not treated as a negative upper bound`() {
        val f = Fixture()
        f.duration = -9223372036854775807L
        f.controller.add(10_000)
        f.quiet()
        assertEquals(listOf(70_000L), f.seeks)
    }

    @Test fun `latest duration is checked again when committing`() {
        val f = Fixture()
        f.controller.add(10_000)
        f.duration = 30_000
        f.quiet()
        assertEquals(listOf(29_999L), f.seeks)
    }

    @Test fun `return to original position or repeated presses at zero need no seek`() {
        val f = Fixture()
        f.controller.add(-10_000)
        f.controller.add(10_000)
        f.quiet()
        assertTrue(f.seeks.isEmpty())
        f.position = 0
        repeat(100) { f.controller.add(-10_000) }
        f.quiet()
        assertTrue(f.seeks.isEmpty())
    }

    @Test fun `zero delta does nothing`() {
        val f = Fixture()
        f.controller.add(0)
        assertEquals(0, f.pauses)
        assertTrue(f.tasks.isEmpty())
        assertTrue(f.previews.isEmpty())
    }

    @Test fun `absolute seek cancellation restores intent without applying obsolete relative target`() {
        val f = Fixture()
        f.controller.add(-10_000)
        f.controller.cancel()
        assertTrue(f.seeks.isEmpty())
        assertTrue(f.backendPlaying)
        assertTrue(f.tasks.isEmpty())
        assertNull(f.previews.last())
    }

    @Test fun `release cancels without resuming or seeking a replaced source`() {
        val f = Fixture()
        f.controller.add(-10_000)
        val stale = f.tasks.keys.single()
        f.controller.cancel(restorePlayback = false)
        stale.run()
        assertTrue(f.seeks.isEmpty())
        assertTrue(f.restored.isEmpty())
        assertFalse(f.controller.isActive)
    }

    @Test fun `stale delayed callback cannot commit a newer burst`() {
        val f = Fixture()
        f.controller.add(-10_000)
        val old = f.tasks.keys.single()
        f.controller.cancel(false)
        f.position = 80_000
        f.controller.add(-5_000)
        old.run()
        assertTrue(f.seeks.isEmpty())
        assertTrue(f.controller.isActive)
        assertEquals(1, f.tasks.size)
        f.quiet()
        assertEquals(listOf(75_000L), f.seeks)
    }

    @Test fun `release from inside seek callback cannot resume the replacement`() {
        val f = Fixture()
        f.onSeek = {
            f.controller.cancel(false)
            f.intent.beginLoad(false)
        }
        f.controller.add(-10_000)
        f.quiet()
        assertTrue(f.restored.isEmpty())
        assertFalse(f.intent.value)
    }

    @Test fun `pause failure clears interaction and restores intent without scheduling stale work`() {
        val f = Fixture()
        f.failPause = true
        f.controller.add(-10_000)
        assertFalse(f.controller.isActive)
        assertTrue(f.tasks.isEmpty())
        assertTrue(f.backendPlaying)
        assertEquals(1, f.failures.size)
    }

    @Test fun `seek failure cannot strand playback in a temporary pause`() {
        val f = Fixture()
        f.failSeek = true
        f.controller.add(-10_000)
        f.quiet()
        assertTrue(f.backendPlaying)
        assertFalse(f.controller.isActive)
        assertEquals(1, f.failures.size)
    }
}
