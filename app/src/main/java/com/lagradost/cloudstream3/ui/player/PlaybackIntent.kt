package com.lagradost.cloudstream3.ui.player

/** Playback intent is not actual isPlaying: buffering must never become a pause request. */
internal class PlaybackIntent {
    var value: Boolean = false
        private set
    var token: Long = 0L
        private set

    private var hasLoaded = false

    fun beginLoad(autoPlay: Boolean?, backendIntent: Boolean? = null,
                  startPaused: Boolean = false, sameEpisode: Boolean = false) {
        token += 1
        // A chooser may mark the very first manual source as sameEpisode. Its
        // default false intent is not a user pause or an existing backend load.
        val replacement = sameEpisode && hasLoaded
        value = when {
            startPaused && !replacement -> false
            // A real source replacement must not undo an intentional pause.
            replacement && backendIntent == false -> false
            else -> autoPlay ?: backendIntent ?: value
        }
        hasLoaded = true
    }

    fun request(play: Boolean) { value = play }

    /** Ignore events from a released/replaced player, even if it used the same URL. */
    fun observeBackend(playerToken: Long, playWhenReady: Boolean) {
        if (playerToken == token) value = playWhenReady
    }

    fun detachPlayer() { token += 1 }
}
