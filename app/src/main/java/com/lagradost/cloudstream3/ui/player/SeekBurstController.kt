package com.lagradost.cloudstream3.ui.player

/** Main-thread, constant-space relative seeking. Preview only while buttons repeat;
 * one backend seek after quiet. Logical intent is owned by the caller, not by the
 * temporary pause. No coroutines, bitmaps or player instances are allocated here.
 */
internal class SeekBurstController(
    private val position: () -> Long,
    private val duration: () -> Long?,
    private val intent: () -> Boolean,
    private val pause: () -> Unit,
    private val seek: (Long) -> Unit,
    private val restore: (Boolean) -> Unit,
    private val preview: (Target?) -> Unit,
    private val schedule: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val changed: () -> Unit = {},
    private val failed: (Exception) -> Unit = {},
) {
    data class Target(val fromMs: Long, val toMs: Long)
    companion object { const val QUIET_MS = 350L }

    private var pending: Target? = null
    private var commit: Runnable? = null
    private var generation = 0L
    val isActive: Boolean get() = pending != null
    var isCommitting = false
        private set
    var isChangingPlayback = false
        private set

    private fun clamp(value: Long): Long {
        val upper = duration()?.takeIf { it > 0 }?.minus(1) ?: Long.MAX_VALUE
        return value.coerceIn(0, upper)
    }

    private inline fun changingPlayback(action: () -> Unit) {
        val previous = isChangingPlayback
        isChangingPlayback = true
        try { action() } finally { isChangingPlayback = previous }
    }

    fun add(deltaMs: Long) {
        if (deltaMs == 0L) return
        val first = pending == null
        if (first) {
            val token = ++generation
            commit = Runnable { finish(token) }
            val start = clamp(position())
            pending = Target(start, start)
        }
        val previous = pending ?: return
        val token = generation
        val sum = if (deltaMs > 0 && previous.toMs > Long.MAX_VALUE - deltaMs)
            Long.MAX_VALUE else previous.toMs + deltaMs
        pending = previous.copy(toMs = clamp(sum))
        val task = commit ?: return
        remove(task)
        if (first) try {
            changingPlayback(pause)
        } catch (error: Exception) {
            failed(error)
            cancel()
            return
        }
        if (token != generation) return // A pause callback may release/replace the backend.
        preview(pending)
        if (token == generation) schedule(task, QUIET_MS)
    }

    fun finish() = finish(generation)

    private fun finish(expected: Long) {
        if (expected != generation) return
        val target = pending ?: return
        commit?.let(remove)
        commit = null
        pending = null
        val token = ++generation
        preview(null)
        val previous = isCommitting
        isCommitting = true
        try {
            val destination = clamp(target.toMs)
            if (destination != position().coerceAtLeast(0)) seek(destination)
        } catch (error: Exception) {
            failed(error)
        } finally {
            isCommitting = previous
            // A seek callback can replace/release the source. Never resume a new
            // backend or erase its new interaction from the old transaction.
            if (generation == token) {
                restoreIntent()
                if (generation == token) changed()
            }
        }
    }

    fun cancel(restorePlayback: Boolean = true) {
        val active = pending != null
        val token = ++generation
        commit?.let(remove)
        commit = null
        pending = null
        if (!active) return
        preview(null)
        if (restorePlayback && generation == token) {
            restoreIntent()
            if (generation == token) changed()
        }
    }

    private fun restoreIntent() {
        try { changingPlayback { restore(intent()) } }
        catch (error: Exception) { failed(error) }
    }
}
