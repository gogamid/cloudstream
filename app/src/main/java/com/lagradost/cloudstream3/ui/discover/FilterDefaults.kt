package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import kotlinx.serialization.Serializable

/** Independent, backup-compatible presets. Browsing filters never write these keys. */
@Serializable
internal data class FilterDefaults(
    val type: String = "MOVIES",
    val language: String = "ENGLISH",
    val rating: Int = 7,
    val genreIds: Set<Int> = emptySet(),
    val genreNames: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val sort: String = "POPULAR",
) {
    fun languageValue() = DiscoverLanguage.entries.firstOrNull { it.name == language } ?: DiscoverLanguage.ENGLISH
    fun ratingValue() = TmdbRatingFilter.entries.firstOrNull { it.minimum == rating } ?: TmdbRatingFilter.SEVEN
    fun sortValue() = DiscoverSort.entries.firstOrNull { it.name == sort } ?: DiscoverSort.POPULAR
    fun typeValue() = DiscoverMediaType.entries.firstOrNull { it.name == type } ?: DiscoverMediaType.MOVIES

    fun normalized(actor: Boolean): FilterDefaults {
        val from = yearFrom?.takeIf { it in 1900..2100 }
        val to = yearTo?.takeIf { it in 1900..2100 }
        return copy(
            type = if (actor && type == "ALL") type else typeValue().name,
            language = languageValue().name, rating = ratingValue().minimum,
            sort = sortValue().name,
            genreIds = genreIds.filter { it > 0 }.toSet(),
            genreNames = genreNames.filter { it.isNotBlank() }.toSet(),
            yearFrom = if (from != null && to != null) minOf(from, to) else from,
            yearTo = if (from != null && to != null) maxOf(from, to) else to,
        )
    }

    companion object {
        fun key(actor: Boolean) = if (actor) "actor_filter_defaults" else "discover_filter_defaults"
        fun load(actor: Boolean) = (getKey<FilterDefaults>(key(actor)) ?: FilterDefaults()).normalized(actor)
        fun save(actor: Boolean, value: FilterDefaults) = setKey(key(actor), value.normalized(actor))
    }
}
