package com.lagradost.cloudstream3.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.fixSubtitleAlignment
import com.lagradost.cloudstream3.ui.subtitles.SaveCaptionStyle
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.applyStyle

/** Shared styling for native embedded tracks and independently loaded external tracks. */
@OptIn(UnstableApi::class)
internal object SubtitleCueStyling {
    fun apply(cues: List<Cue>, style: SaveCaptionStyle): List<Cue> {
        val (bitmaps, text) = cues.partition { it.bitmap != null }
        val styledBitmaps = bitmaps.map { it.buildUpon().fixSubtitleAlignment().applyStyle(style).build() }
        val seen = HashSet<CharSequence>()
        val buffer = StringBuilder()
        val styledText = text.groupBy { it.lineAnchor to it.position.times(1000f).toInt() }
            .mapNotNull { (_, entries) ->
                seen.clear()
                buffer.clear()
                for (entry in entries) {
                    val value = entry.text ?: continue
                    if (!seen.add(value)) continue
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(value.trim())
                }
                entries.firstOrNull()?.buildUpon()?.setText(buffer.toString())
                    ?.fixSubtitleAlignment()?.applyStyle(style)?.build()
            }
        return styledBitmaps + styledText
    }
}
