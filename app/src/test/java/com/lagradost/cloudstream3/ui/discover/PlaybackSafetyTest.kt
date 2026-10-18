package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.PlaybackOperationGuard
import com.lagradost.cloudstream3.ui.player.PlaybackResourcePolicy
import com.lagradost.cloudstream3.ui.player.SubtitleCueStore
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PlaybackSafetyTest {
    @Test fun `switching source invalidates queued work even when URL is unchanged`() {
        val guard = PlaybackOperationGuard()
        val first = guard.token()
        assertTrue(guard.isCurrent(first))
        val second = guard.advance()
        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
        guard.advance() // Same source chosen again, or player view detached.
        assertFalse(guard.isCurrent(second))
    }

    @Test fun `generation invalidation is safe across worker callbacks`() {
        val guard = PlaybackOperationGuard()
        val old = guard.token()
        val workers = List(4) { thread { repeat(1000) { guard.advance() } } }
        workers.forEach { it.join() }
        assertEquals(4000L, guard.token())
        assertFalse(guard.isCurrent(old))
    }

    @Test fun `TV default buffer is bounded without reducing explicit preferences`() {
        assertEquals(32 * 1024 * 1024, PlaybackResourcePolicy.targetBufferBytes(true, 0L, -1))
        assertEquals(-1, PlaybackResourcePolicy.targetBufferBytes(false, 0L, -1))
        assertEquals(64 * 1024 * 1024, PlaybackResourcePolicy.targetBufferBytes(true, 64L * 1024 * 1024, -1))
        assertEquals(Int.MAX_VALUE, PlaybackResourcePolicy.targetBufferBytes(false, Long.MAX_VALUE, -1))
        assertEquals(0, PlaybackResourcePolicy.backBufferMs(true))
        assertEquals(30000, PlaybackResourcePolicy.backBufferMs(false))
    }

    @Test fun `episode cache retains current plus three recent episodes`() {
        val times = (1..12).associateWith { it.toLong() }
        val removed = PlaybackResourcePolicy.episodeCacheEvictions(times, 1, 20)
        assertFalse(1 in removed)
        assertEquals(setOf(1, 10, 11, 12), times.keys - removed)
    }

    @Test fun `new cache slot and expired entries respect the same memory bound`() {
        val times = (1..12).associateWith { it.toLong() }
        val removed = PlaybackResourcePolicy.episodeCacheEvictions(times, 13, 20)
        assertEquals(setOf(10, 11, 12), times.keys - removed)
        assertEquals(setOf(2, 3), PlaybackResourcePolicy.episodeCacheEvictions(
            mapOf(1 to 0L, 2 to 0L, 3 to 50L), 1, 2000))
    }

    @Test fun `cue snapshots stay stable after decoder reset`() {
        val store = SubtitleCueStore<Int>()
        store.add(1)
        store.add(2)
        val snapshot = store.snapshot()
        store.clear()
        store.add(3)
        assertEquals(listOf(1, 2), snapshot)
        assertEquals(listOf(3), store.snapshot())
    }

    @Test fun `sync cue reads do not race decoder updates and resets`() {
        val store = SubtitleCueStore<Int>()
        val failure = AtomicReference<Throwable?>()
        val writer = thread {
            try {
                repeat(5000) { i -> if (i % 13 == 0) store.clear() else store.add(i) }
            } catch (error: Throwable) { failure.set(error) }
        }
        repeat(1000) { store.snapshot().forEach { assertTrue(it >= 0) } }
        writer.join()
        assertNull(failure.get())
    }
}
