package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.SubtitleHelper
import java.util.Locale

internal object SubtitleLanguageSelection {
    private fun language(value: String?): String? {
        val normalized = value?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
        val base = normalized.substringBefore('-')
        return when (base) {
            "ru", "rus" -> "ru"
            "en", "eng" -> "en"
            else -> SubtitleHelper.fromLanguageToTagIETF(
                SubtitleHelper.fromTagToEnglishLanguageName(normalized)
                    ?: SubtitleHelper.fromTagToEnglishLanguageName(base)
                    ?: normalized
            )?.substringBefore('-')?.lowercase(Locale.ROOT)
        }
    }

    /** Preserve the player's displayed subtitle order; do not fetch anything. */
    fun first(subtitles: List<SubtitleData>, wanted: String): SubtitleData? = subtitles.firstOrNull {
        val resolved = language(it.languageCode)
            ?: language(it.originalName)
            ?: language(it.getIETF_tag())
        resolved == wanted
    }
}
