package com.lagradost.cloudstream3.ui.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Native controls/media-session seeks share the UI's live setting and TV batcher. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SettingsSeekPlayer(
    player: Player,
    private val amountMs: () -> Long,
    private val seekRelative: (Long) -> Unit,
) : ForwardingPlayer(player) {
    override fun getSeekBackIncrement(): Long = amountMs()
    override fun getSeekForwardIncrement(): Long = amountMs()
    override fun seekBack() = seekRelative(-amountMs())
    override fun seekForward() = seekRelative(amountMs())
}
