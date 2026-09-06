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

    suspend fun genres(type: DiscoverMediaType): List<TmdbGenre> =
        parseJson<GenresResponse>(
            request("/genre/${type.path}/list", mapOf("language" to "en-US"))
        ).genres.orEmpty().filter { it.id > 0 && it.name.isNotBlank() }.distinctBy { it.id }

    suspend fun discover(
        type: DiscoverMediaType,
        rating: TmdbRatingFilter,
        genreIds: Set<Int>,
        year: Int?,
        sort: DiscoverSort,
        page: Int,
    ): DiscoverPage {
        require(page in 1..500)
        require(genreIds.all { it > 0 })
        require(year == null || year in 1900..2100)
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
        year?.let {
            params[
                if (type == DiscoverMediaType.SERIES) "first_air_date_year" else "primary_release_year"
            ] = it.toString()
        }
        val response = parseJson<DiscoverResponse>(request("/discover/${type.path}", params))
        return DiscoverPage(
            results = response.results.orEmpty().asSequence()
                .filter { it.usable }
                .distinctBy { it.id }
                .map { it.toSearchResponse(type.path) }
                .filter { rating.matches(it.score) }
                .toList(),
            hasMore = page < response.totalPages.coerceAtMost(500),
        )
    }
}
