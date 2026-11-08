package com.lagradost.cloudstream3.ui.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Process-memory descriptors, with one extraction shared by speculative and real consumers. */
internal class SourceCollectionStore<K, L, S>(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val linkId: (L) -> String,
    private val subtitleId: (S) -> String,
) {
    data class Snapshot<L, S>(
        val links: List<L> = emptyList(),
        val subtitles: List<S> = emptyList(),
        // null means still collecting, NOT that the snapshot is empty.
        val result: Boolean? = null,
    )

    private inner class Entry {
        val state = MutableStateFlow(Snapshot<L, S>())
        var job: Job? = null
        var token: Any? = null
        var users = 0
        var promoted = false
        var touched = now()
        var cachedAt = now()
    }

    inner class Lease internal constructor(
        val state: StateFlow<Snapshot<L, S>>,
        private val release: () -> Unit,
    ) : AutoCloseable {
        private var closed = false
        override fun close() = synchronized(this) {
            if (!closed) {
                closed = true
                release()
            }
        }
    }

    inner class Sink internal constructor(
        val initial: Snapshot<L, S>,
        private val publish: (L?, S?) -> Unit,
    ) {
        fun link(link: L) = publish(link, null)
        fun subtitle(subtitle: S) = publish(null, subtitle)
    }

    private val entries = LinkedHashMap<K, Entry>()

    fun acquire(
        key: K,
        speculative: Boolean = false,
        refresh: Boolean = false,
        load: suspend (Sink) -> Boolean,
    ): Lease = synchronized(entries) {
        val previous = entries[key]
        val ttl = if (previous?.promoted == true) 20 * 60_000L else 2 * 60_000L
        if (previous != null && (refresh || previous.users == 0 && now() - previous.cachedAt > ttl)) {
            invalidate(previous)
            entries.remove(key)
        }
        val entry = entries.getOrPut(key) { Entry() }
        entry.users++
        entry.promoted = entry.promoted || !speculative
        entry.touched = now()
        if (entry.job == null && entry.state.value.result != true) {
            entry.state.value = entry.state.value.copy(result = null)
            val token = Any()
            entry.token = token
            val links = entry.state.value.links.associateByTo(LinkedHashMap(), linkId)
            val subtitles = entry.state.value.subtitles.associateByTo(LinkedHashMap(), subtitleId)
            val sink = Sink(entry.state.value) { link, subtitle ->
                synchronized(entries) {
                    // Providers sometimes swallow coroutine cancellation or deliver late callbacks.
                    if (entry.token !== token || entries[key] !== entry) return@synchronized
                    val added = when {
                        link != null -> links.putIfAbsent(linkId(link), link) == null
                        subtitle != null -> subtitles.putIfAbsent(subtitleId(subtitle), subtitle) == null
                        else -> false
                    }
                    if (added) entry.state.value = Snapshot(links.values.toList(), subtitles.values.toList())
                }
            }
            entry.job = scope.launch(start = CoroutineStart.LAZY) {
                var result = false
                try {
                    result = load(sink)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Preserve any partial descriptors; a later caller can retry extraction.
                } finally {
                    synchronized(entries) {
                        if (entry.token === token) {
                            entry.token = null
                            entry.job = null
                            entry.touched = now()
                            entry.cachedAt = now()
                            entry.state.value = entry.state.value.copy(result = result)
                        }
                    }
                }
            }
            entry.job?.start()
        }
        trim()
        Lease(entry.state) {
            synchronized(entries) {
                entry.users--
                if (entry.users == 0 && entry.job != null) invalidate(entry)
                trim()
            }
        }
    }

    fun clear() = synchronized(entries) {
        entries.values.forEach(::invalidate)
        entries.clear()
    }

    fun discardUnused() = synchronized(entries) {
        entries.entries.removeAll { it.value.users == 0 }
    }

    private fun invalidate(entry: Entry) {
        entry.token = null
        entry.job?.cancel()
        entry.job = null
        if (entry.state.value.result == null) {
            entry.state.value = entry.state.value.copy(result = false)
        }
    }

    private fun trim() {
        // Active players are pinned. Speculation is serialized separately and never pins a library.
        entries.entries.filter { it.value.users == 0 }
            .sortedBy { it.value.touched }
            .take((entries.size - 4).coerceAtLeast(0))
            .forEach { entries.remove(it.key) }
    }
}
