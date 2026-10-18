package com.lagradost.cloudstream3.ui.player

/** Main-thread LRU. Size is bounded by both track count and original file bytes. */
internal class ExternalSubtitleCache<T> {
    private data class Entry<T>(val value: T, val bytes: Int)
    private val entries = linkedMapOf<String, Entry<T>>()
    var bytes: Int = 0
        private set
    val size: Int get() = entries.size

    operator fun get(id: String): T? = entries.remove(id)?.also { entries[id] = it }?.value

    fun put(id: String, value: T, byteCount: Int) {
        require(byteCount in 0..ExternalSubtitleLimits.MAX_FILE_BYTES)
        entries.remove(id)?.let { bytes -= it.bytes }
        entries[id] = Entry(value, byteCount)
        bytes += byteCount
        while (entries.size > ExternalSubtitleLimits.MAX_CACHE_TRACKS || bytes > ExternalSubtitleLimits.MAX_CACHE_BYTES) {
            val first = entries.entries.first()
            bytes -= first.value.bytes
            entries.remove(first.key)
        }
    }

    fun clear() { entries.clear(); bytes = 0 }
}
