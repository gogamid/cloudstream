package com.lagradost.cloudstream3.ui.player

/** Playback intent is not actual isPlaying: buffering must never become a pause request. */
internal class PlaybackIntent {
    var value: Boolean = false
        private set
    var token: Long = 0L
        private set

    fun beginLoad(autoPlay: Boolean?, backendIntent: Boolean? = null,
                  startPaused: Boolean = false, sameEpisode: Boolean = false) {
        token += 1
        value = when {
            startPaused && !sameEpisode -> false
            // A source replacement must not undo a user's intentional pause.
            sameEpisode && backendIntent == false -> false
            else -> autoPlay ?: backendIntent ?: value
        }
    }

    fun request(play: Boolean) { value = play }

    /** Ignore events from a released/replaced player, even if it used the same URL. */
    fun observeBackend(playerToken: Long, playWhenReady: Boolean) {
        if (playerToken == token) value = playWhenReady
    }

    fun detachPlayer() { token += 1 }
}
