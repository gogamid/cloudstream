package com.lagradost.cloudstream3.ui.discover

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal enum class DiscoverMediaType(val path: String) {
    MOVIES("movie"), SERIES("tv")
}

internal enum class DiscoverLanguage(val code: String?, val labelRes: Int) {
    ALL(null, R.string.discover_lang_all),
    ENGLISH("en", R.string.discover_lang_en),
    RUSSIAN("ru", R.string.discover_lang_ru),
    GERMAN("de", R.string.discover_lang_de),
    TURKISH("tr", R.string.discover_lang_tr),
    AZERBAIJANI("az", R.string.discover_lang_az);
}

@Serializable
internal data class TmdbGenre(
    @JsonProperty("id") @SerialName("id") val id: Int = 0,
    @JsonProperty("name") @SerialName("name") val name: String = "",
)

/** Every option maps to a single TMDB `sort_by` value so paging stays server-side. */
internal enum class DiscoverSort(val labelRes: Int) {
    POPULAR(R.string.discover_sort_popular),
    TOP_RATED(R.string.discover_sort_top_rated),
    NEWEST(R.string.discover_sort_newest),
    OLDEST(R.string.discover_sort_oldest),
    TITLE_AZ(R.string.discover_sort_title_az);

    fun sortBy(type: DiscoverMediaType): String = when (this) {
        POPULAR -> "popularity.desc"
        TOP_RATED -> "vote_average.desc"
        NEWEST -> if (type == DiscoverMediaType.SERIES) "first_air_date.desc" else "primary_release_date.desc"
        OLDEST -> if (type == DiscoverMediaType.SERIES) "first_air_date.asc" else "primary_release_date.asc"
        TITLE_AZ -> "original_title.asc"
    }
}

internal data class YearRange(val from: Int? = null, val to: Int? = null) {
    val isAll: Boolean get() = from == null && to == null
    fun label(): String = when {
        isAll -> "All"
        from != null && to != null && from == to -> from.toString()
        from != null && to != null -> "$from — $to"
        from != null -> "$from+"
        else -> "≤$to"
    }
}

internal data class DiscoverPage(val results: List<SearchResponse>, val hasMore: Boolean)

/** TMDB supplies the catalogue; selecting a card always searches installed providers. */
internal class DiscoverRepository(
    private val request: suspend (String, Map<String, String>) -> String = TmdbMetadata::request,
) {
    @Serializable
    private data class GenresResponse(
        @JsonProperty("genres") @SerialName("genres") val genres: List<TmdbGenre>? = null,
    )

    @Serializable
    private data class DiscoverResponse(
        @JsonProperty("results") @SerialName("results") val results: List<TmdbTitle>? = null,
        @JsonProperty("total_pages") @SerialName("total_pages") val totalPages: Int = 0,
    )

    private var genreNames: Map<Int, String> = emptyMap()

    suspend fun genres(type: DiscoverMediaType): List<TmdbGenre> =
        parseJson<GenresResponse>(
            request("/genre/${type.path}/list", mapOf("language" to "en-US"))
        ).genres.orEmpty().filter { it.id > 0 && it.name.isNotBlank() }.distinctBy { it.id }
            .also { genreNames = genreNames + it.associate { genre -> genre.id to genre.name } }

    suspend fun discover(
        type: DiscoverMediaType,
        language: DiscoverLanguage,
        rating: TmdbRatingFilter,
        genreIds: Set<Int>,
        yearFrom: Int?,
        yearTo: Int?,
        sort: DiscoverSort,
        page: Int,
    ): DiscoverPage {
        require(page in 1..500)
        require(genreIds.all { it > 0 })
        require(yearFrom == null || yearFrom in 1900..2100)
        require(yearTo == null || yearTo in 1900..2100)
        require(yearFrom == null || yearTo == null || yearFrom <= yearTo)
        val params = mutableMapOf(
            "language" to "en-US",
            "include_adult" to "false",
            "sort_by" to sort.sortBy(type),
            "page" to page.toString(),
        )
        if (rating != TmdbRatingFilter.ALL) {
            params["vote_average.gte"] = rating.minimum.toString()
            params["vote_count.gte"] = "1"
        }
        // Top-rated sorting needs a meaningful vote floor, otherwise single-vote
        // 10.0 titles dominate the list.
        if (sort == DiscoverSort.TOP_RATED) {
            params["vote_count.gte"] = "25"
        }
        if (genreIds.isNotEmpty()) {
            // Pipe = OR: titles matching any selected genre.
            params["with_genres"] = genreIds.sorted().joinToString("|")
        }
        language.code?.let { params["with_original_language"] = it }
        if (yearFrom != null || yearTo != null) {
            val isSeries = type == DiscoverMediaType.SERIES
            val gteKey = if (isSeries) "first_air_date.gte" else "primary_release_date.gte"
            val lteKey = if (isSeries) "first_air_date.lte" else "primary_release_date.lte"
            yearFrom?.let { params[gteKey] = "%04d-01-01".format(it) }
            yearTo?.let { params[lteKey] = "%04d-12-31".format(it) }
        }
        val response = parseJson<DiscoverResponse>(request("/discover/${type.path}", params))
        return DiscoverPage(
            results = response.results.orEmpty().asSequence()
                .filter { it.usable }
                .distinctBy { it.id }
                .map { it.toSearchResponse(type.path, genreNames) }
                .filter { rating.matches(it.score) }
                .toList(),
            hasMore = page < response.totalPages.coerceAtMost(500),
        )
    }
}
