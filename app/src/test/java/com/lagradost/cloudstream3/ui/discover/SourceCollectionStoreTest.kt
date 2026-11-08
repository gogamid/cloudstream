package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SourceCollectionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SourceCollectionStoreTest {
    private data class Descriptor(val id: String, val label: String = id)

    private class Fixture(scope: CoroutineScope) {
        var time = 0L
        val store = SourceCollectionStore<String, Descriptor, Descriptor>(
            scope, { time }, { it.id }, { it.id },
        )
    }

    private fun withStore(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture(this)
        try {
            fixture.block()
        } finally {
            fixture.store.clear()
        }
    }

    @Test fun `warm and player share extraction and immediately replay partial descriptors`() = withStore {
        var starts = 0
        val finish = CompletableDeferred<Unit>()
        lateinit var link: (Descriptor) -> Unit
        lateinit var subtitle: (Descriptor) -> Unit
        val warm = store.acquire("episode", speculative = true) { sink ->
            starts++
            link = sink::link
            subtitle = sink::subtitle
            sink.link(Descriptor("first"))
            sink.subtitle(Descriptor("en"))
            finish.await()
            true
        }
        yield()
        val player = store.acquire("episode") { error("Must join warm extraction") }
        assertEquals(1, starts)
        assertEquals(listOf(Descriptor("first")), player.state.value.links)
        assertEquals(listOf(Descriptor("en")), player.state.value.subtitles)
        assertNull(player.state.value.result)

        warm.close()
        warm.close() // A repeated lifecycle release must not detach the player.
        link(Descriptor("second"))
        subtitle(Descriptor("fr"))
        finish.complete(Unit)
        yield()
        assertEquals(true, player.state.value.result)
        assertEquals(listOf("first", "second"), player.state.value.links.map { it.id })
        assertEquals(listOf("en", "fr"), player.state.value.subtitles.map { it.id })
        player.close()

        val replay = store.acquire("episode") { error("Successful collection must be reused") }
        assertEquals(player.state.value, replay.state.value)
        assertEquals(1, starts)
        replay.close()
    }

    @Test fun `simultaneous acquisitions before provider starts use only one load`() = withStore {
        var starts = 0
        val finish = CompletableDeferred<Unit>()
        val warm = store.acquire("episode", speculative = true) {
            starts++
            finish.await()
            true
        }
        val player = store.acquire("episode") { error("Duplicate extraction") }
        yield()
        assertEquals(1, starts)
        finish.complete(Unit)
        yield()
        assertEquals(true, warm.state.value.result)
        assertEquals(warm.state.value, player.state.value)
        warm.close()
        player.close()
    }

    @Test fun `last detach cancels and swallowed cancellation cannot publish into retry`() = withStore {
        var cancelled = false
        lateinit var lateLink: (Descriptor) -> Unit
        lateinit var lateSubtitle: (Descriptor) -> Unit
        val first = store.acquire("episode") { sink ->
            lateLink = sink::link
            lateSubtitle = sink::subtitle
            sink.link(Descriptor("partial"))
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                cancelled = true
                sink.link(Descriptor("swallowed"))
                sink.subtitle(Descriptor("swallowed"))
                true
            }
        }
        yield()
        first.close()
        val retryFinish = CompletableDeferred<Unit>()
        val retry = store.acquire("episode") { sink ->
            assertEquals(listOf(Descriptor("partial")), sink.initial.links)
            sink.link(Descriptor("retry"))
            retryFinish.await()
            true
        }
        yield()
        assertTrue(cancelled)
        lateLink(Descriptor("late"))
        lateSubtitle(Descriptor("late"))
        assertEquals(listOf("partial", "retry"), retry.state.value.links.map { it.id })
        assertTrue(retry.state.value.subtitles.isEmpty())
        assertNull(retry.state.value.result)
        retryFinish.complete(Unit)
        yield()
        assertEquals(true, retry.state.value.result)
        retry.close()
    }

    @Test fun `false result and exception preserve partial data and retry deduplicates by identity`() = withStore {
        for (throws in listOf(false, true)) {
            val key = "episode-$throws"
            val first = store.acquire(key) { sink ->
                sink.link(Descriptor("video", "original video"))
                sink.subtitle(Descriptor("en", "original subtitle"))
                if (throws) throw IllegalStateException("Provider failed after partial result")
                false
            }
            yield()
            assertEquals(false, first.state.value.result)
            first.close()
            val retry = store.acquire(key) { sink ->
                assertNull(sink.initial.result)
                assertEquals(first.state.value.links, sink.initial.links)
                assertEquals(first.state.value.subtitles, sink.initial.subtitles)
                sink.link(Descriptor("video", "duplicate video"))
                sink.subtitle(Descriptor("en", "duplicate subtitle"))
                sink.link(Descriptor("other"))
                sink.subtitle(Descriptor("fr"))
                true
            }
            yield()
            assertEquals(true, retry.state.value.result)
            assertEquals(listOf(Descriptor("video", "original video"), Descriptor("other")), retry.state.value.links)
            assertEquals(listOf(Descriptor("en", "original subtitle"), Descriptor("fr")), retry.state.value.subtitles)
            retry.close()
        }
    }

    @Test fun `refresh replaces active collection and rejects old callbacks and completion`() = withStore {
        var cancelled = false
        lateinit var oldLink: (Descriptor) -> Unit
        lateinit var oldSubtitle: (Descriptor) -> Unit
        val old = store.acquire("episode") { sink ->
            oldLink = sink::link
            oldSubtitle = sink::subtitle
            sink.link(Descriptor("old"))
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                cancelled = true
                sink.link(Descriptor("old-after-cancel"))
                true
            }
        }
        yield()
        val finish = CompletableDeferred<Unit>()
        val fresh = store.acquire("episode", refresh = true) { sink ->
            assertTrue(sink.initial.links.isEmpty())
            assertTrue(sink.initial.subtitles.isEmpty())
            sink.link(Descriptor("fresh"))
            finish.await()
            true
        }
        yield()
        assertTrue(cancelled)
        old.close() // Closing a replaced lease must not cancel its replacement.
        oldLink(Descriptor("late-old"))
        oldSubtitle(Descriptor("late-old"))
        assertEquals(listOf(Descriptor("fresh")), fresh.state.value.links)
        assertTrue(fresh.state.value.subtitles.isEmpty())
        assertNull(fresh.state.value.result)
        finish.complete(Unit)
        yield()
        assertEquals(true, fresh.state.value.result)
        fresh.close()
    }

    @Test fun `different keys never share descriptors cancellation or completion`() = withStore {
        val finish = CompletableDeferred<Unit>()
        val first = store.acquire("provider-a-episode") { sink ->
            sink.link(Descriptor("a"))
            awaitCancellation()
        }
        val second = store.acquire("provider-b-episode") { sink ->
            sink.link(Descriptor("b"))
            sink.subtitle(Descriptor("b-en"))
            finish.await()
            true
        }
        yield()
        first.close()
        yield()
        assertEquals(listOf(Descriptor("a")), first.state.value.links)
        assertEquals(listOf(Descriptor("b")), second.state.value.links)
        assertEquals(listOf(Descriptor("b-en")), second.state.value.subtitles)
        assertNull(second.state.value.result)
        finish.complete(Unit)
        yield()
        assertEquals(true, second.state.value.result)
        second.close()
    }

    @Test fun `speculative collection expires after two minutes but player promotion lasts twenty`() = withStore {
        for (promoted in listOf(false, true)) {
            time = 0L
            val key = "episode-$promoted"
            var starts = 0
            val warm = store.acquire(key, speculative = true) { sink ->
                starts++
                sink.link(Descriptor("cached"))
                true
            }
            yield()
            if (promoted) {
                val player = store.acquire(key) { error("Promotion must reuse extraction") }
                player.close()
            }
            warm.close()
            time = if (promoted) 20 * 60_000L - 1 else 2 * 60_000L - 1
            val cached = store.acquire(key, speculative = true) { error("Expired too soon") }
            assertEquals(listOf(Descriptor("cached")), cached.state.value.links)
            cached.close()
            // Reading a cached signed locator must not extend its freshness indefinitely.
            time += 2
            val expired = store.acquire(key, speculative = true) { sink ->
                starts++
                assertTrue(sink.initial.links.isEmpty())
                sink.link(Descriptor("new"))
                true
            }
            yield()
            assertEquals(2, starts)
            assertEquals(listOf(Descriptor("new")), expired.state.value.links)
            expired.close()
        }
    }

    @Test fun `plugin cache eviction removes idle descriptors but cannot terminate a player collection`() = withStore {
        val idle = store.acquire("idle", speculative = true) { true }
        val finish = CompletableDeferred<Unit>()
        val player = store.acquire("playing") { sink ->
            sink.link(Descriptor("partial"))
            finish.await()
            sink.link(Descriptor("late"))
            true
        }
        yield()
        idle.close()
        store.discardUnused()
        assertNull(player.state.value.result)
        finish.complete(Unit)
        yield()
        assertEquals(true, player.state.value.result)
        assertEquals(listOf("partial", "late"), player.state.value.links.map { it.id })
        var reloaded = false
        val replacement = store.acquire("idle") { reloaded = true; true }
        yield()
        assertTrue(reloaded)
        player.close()
        replacement.close()
    }

    @Test fun `four unpinned entries are retained and fifth evicts least recently touched`() = withStore {
        for (key in listOf("a", "b", "c", "d")) {
            time++
            val lease = store.acquire(key, speculative = true) { sink ->
                sink.link(Descriptor(key))
                true
            }
            yield()
            lease.close()
        }
        time++
        val touched = store.acquire("a", speculative = true) { error("Four entries must fit") }
        touched.close()
        time++
        val fifth = store.acquire("e", speculative = true) { true }
        yield()
        fifth.close()
        for (key in listOf("a", "c", "d", "e")) {
            val cached = store.acquire(key, speculative = true) { error("Wrong eviction: $key") }
            yield()
            assertEquals(true, cached.state.value.result)
            cached.close()
        }
        var reloaded = false
        val evicted = store.acquire("b", speculative = true) { sink ->
            reloaded = true
            assertTrue(sink.initial.links.isEmpty())
            true
        }
        yield()
        assertTrue(reloaded)
        evicted.close()
    }

    @Test fun `active leases remain pinned even when more than four keys exist and TTL passes`() = withStore {
        val active = (1..6).map { index ->
            store.acquire("episode-$index") { sink ->
                sink.link(Descriptor("video-$index"))
                true
            }
        }
        yield()
        time = 21 * 60_000L
        for (index in 1..6) {
            val joined = store.acquire("episode-$index") { error("Active entry was evicted or expired") }
            assertEquals(listOf(Descriptor("video-$index")), joined.state.value.links)
            assertEquals(true, joined.state.value.result)
            joined.close()
        }
        active.forEach { it.close() }
    }
}
