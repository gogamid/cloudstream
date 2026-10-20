package com.lagradost.cloudstream3.ui.player

/** One episode's last choice. Resolve only against currently available descriptors;
 * never return an old episode's URL or perform a subtitle-provider search.
 */
internal class SubtitleToggleSelection {
    private var last: SubtitleData? = null

    fun remember(subtitle: SubtitleData?) {
        if (subtitle != null) last = subtitle
    }

    fun clear() { last = null }

    /** Extraction may still contain embedded descriptors from an earlier mirror. */
    fun available(tracks: List<SubtitleData>, embeddedIds: Set<String>): List<SubtitleData> = tracks.filter {
        it.origin != SubtitleOrigin.EMBEDDED_IN_VIDEO || it.getId() in embeddedIds
    }

    /** Null means Off when current is non-null, otherwise no track is available. */
    fun target(current: SubtitleData?, available: List<SubtitleData>, preferred: SubtitleData?): SubtitleData? {
        if (current != null) {
            remember(current)
            return null
        }
        val remembered = last
        if (remembered != null) {
            available.firstOrNull { it.getId() == remembered.getId() }?.let { return it }
            val identity = SeriesSourcePreferences.Subtitle.from(remembered)
            available.firstOrNull(identity::matches)?.let { return it }
        }
        return available.firstOrNull { it.getId() == preferred?.getId() } ?: available.firstOrNull()
    }
}
