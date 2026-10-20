package com.lagradost.cloudstream3.ui.player

import kotlinx.serialization.Serializable

/** Source identities deliberately exclude episode URLs and embedded track IDs. */
@Serializable
internal data class SeriesSourcePreferences(
    val video: Video? = null,
    val subtitle: Subtitle? = null,
    val subtitlesOff: Boolean = false,
    // Automatic success is a hint, never a replacement for the manual choice.
    val lastWorkingVideo: Video? = null,
) {
    /** Off is a separate explicit choice, not a reason to forget the last track. */
    fun withSubtitleSelection(selected: Subtitle?) = copy(
        subtitle = selected ?: subtitle,
        subtitlesOff = selected == null,
    )

    @Serializable
    data class Video(val source: String, val name: String, val quality: Int, val type: String,
                     val host: String? = null) {
        companion object {
            private val episodeLabel = Regex("\\b(?:s\\d{1,3}\\s*e\\d{1,4}|\\d{1,2}x\\d{1,4}|(?:season|episode|ep)\\s*[:#.-]?\\s*\\d{1,4})\\b")
            private val qualityLabel = Regex("\\b(?:2160|1440|1080|720|480|360)p?\\b")
            private val separators = Regex("[\\s\\p{Punct}]+")
        }
        private fun normalized(value: String) = value.lowercase(java.util.Locale.ROOT).trim()
        private fun stableName(value: String) = normalized(value)
            .replace(episodeLabel, " ").replace(qualityLabel, " ")
            .replace(separators, " ").trim()

        /** Strong matches preserve host/name identity; source-only is a weaker fallback. */
        fun match(candidate: Video): Int {
            if (normalized(source).isBlank() || normalized(source) != normalized(candidate.source)) return 0
            if (this == candidate) return 7
            val sameName = stableName(name).isNotBlank() && stableName(name) == stableName(candidate.name)
            val sameHost = !host.isNullOrBlank() && host.equals(candidate.host, ignoreCase = true)
            val sameFormat = type == candidate.type
            val sameQuality = quality == candidate.quality
            return when {
                sameName && sameFormat && sameQuality -> 6
                sameHost && sameFormat && sameQuality -> 5
                sameName -> 4
                sameHost -> 3
                sameFormat && sameQuality -> 2 // Weak: retain quality, but wait for a real host/name match.
                else -> 1
            }
        }

        fun family(): String = normalized(source) + "|" +
            (host?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotBlank() } ?: stableName(name))
    }

    @Serializable
    data class Subtitle(val name: String, val suffix: String, val language: String?, val origin: SubtitleOrigin) {
        fun matches(track: SubtitleData) = this == from(track)
        companion object {
            fun from(track: SubtitleData) = Subtitle(track.originalName, track.nameSuffix,
                track.getIETF_tag() ?: track.languageCode, track.origin)
        }
    }
}
