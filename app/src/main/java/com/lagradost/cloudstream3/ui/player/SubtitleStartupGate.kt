package com.lagradost.cloudstream3.ui.player

/** Queue text work until this video backend is prepared, including start-paused.
 * Once ready, later buffering must not hold up independent subtitle changes.
 * No polling, networking or playback ownership belongs in this policy.
 */
internal class SubtitleStartupGate<T>(private val keyOf: (T) -> Any? = { it }) {
    data class Request<T>(val value: T, val userInitiated: Boolean)
    var token: Long = 0L
        private set
    private var ready = false
    private var pending: Request<T>? = null

    fun request(value: T, userInitiated: Boolean): Request<T>? {
        val explicit = userInitiated || (pending?.let {
            keyOf(it.value) == keyOf(value) && it.userInitiated
        } == true)
        val request = Request(value, explicit)
        if (ready) return request
        pending = request
        return null
    }

    fun videoReady(sourceToken: Long): Request<T>? {
        if (sourceToken != token) return null
        ready = true
        return pending.also { pending = null }
    }

    fun cancelPending() { pending = null }

    fun reset() {
        token++
        ready = false
        pending = null
    }
}
