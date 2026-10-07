package com.lagradost.cloudstream3.ui.player

import kotlinx.serialization.Serializable

/** Source identities deliberately exclude episode URLs and embedded track IDs. */
@Serializable
internal data class SeriesSourcePreferences(
    val video: Video? = null,
    val subtitle: Subtitle? = null,
    val subtitlesOff: Boolean = false,
) {
    @Serializable
    data class Video(val source: String, val name: String, val quality: Int, val type: String)

    @Serializable
    data class Subtitle(val name: String, val suffix: String, val language: String?, val origin: SubtitleOrigin) {
        fun matches(track: SubtitleData) = this == from(track)
        companion object {
            fun from(track: SubtitleData) = Subtitle(track.originalName, track.nameSuffix,
                track.getIETF_tag() ?: track.languageCode, track.origin)
        }
    }
}
