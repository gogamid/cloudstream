package com.lagradost.cloudstream3.ui.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Native controls/media-session seeks share the UI's live setting and TV batcher. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SettingsSeekPlayer(
    private val backend: Player,
    private val amountMs: () -> Long,
    private val seekRelative: (Long) -> Unit,
) : ForwardingPlayer(backend) {
    // PlayerView's volume boost still needs ExoPlayer's current audio session.
    internal val audioSessionId: Int? get() = (backend as? ExoPlayer)?.audioSessionId

    override fun getSeekBackIncrement(): Long = amountMs()
    override fun getSeekForwardIncrement(): Long = amountMs()
    override fun seekBack() = seekRelative(-amountMs())
    override fun seekForward() = seekRelative(amountMs())
}
