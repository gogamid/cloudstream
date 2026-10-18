package com.lagradost.cloudstream3.ui.player

/** Requested track advances immediately for rapid cycling; only current results may publish. */
internal class SubtitleSelectionSession {
    var requestedId: String? = null
        private set
    var activeId: String? = null
        private set
    private var revision = 0L

    fun request(id: String?): Long {
        revision += 1
        requestedId = id
        if (id == null) activeId = null
        return revision
    }

    fun accepts(token: Long) = token == revision && requestedId != null
    fun commit(token: Long): Boolean {
        if (!accepts(token)) return false
        activeId = requestedId
        revision += 1
        return true
    }

    fun fail(token: Long): Boolean {
        if (!accepts(token)) return false
        requestedId = activeId
        revision += 1
        return true
    }
}

internal object ExternalSubtitleLimits {
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    const val MAX_CACHE_TRACKS = 2
    const val MAX_CUES = 30_000

    fun read(input: java.io.InputStream, checkActive: () -> Unit = {}): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() <= MAX_FILE_BYTES - count) { "Subtitle file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    /** Saturating clock conversion, including a negative offset before the first cue. */
    fun positionUs(positionMs: Long, offsetMs: Long): Long {
        val sum = when {
            offsetMs > 0 && positionMs > Long.MAX_VALUE - offsetMs -> Long.MAX_VALUE
            offsetMs < 0 && positionMs < Long.MIN_VALUE - offsetMs -> Long.MIN_VALUE
            else -> positionMs + offsetMs
        }
        return sum.coerceIn(Long.MIN_VALUE / 1000, Long.MAX_VALUE / 1000) * 1000
    }
}
