package com.lagradost.cloudstream3.ui.discover

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal enum class DiscoverMediaType(val path: String) {
    MOVIES("movie"), SERIES("tv")
}

@Serializable
internal data class TmdbGenre(
    @JsonProperty("id") @SerialName("id") val id: Int = 0,
    @JsonProperty("name") @SerialName("name") val name: String = "",
)

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
        genreId: Int?,
        page: Int,
    ): DiscoverPage {
        require(page in 1..500)
        require(genreId == null || genreId > 0)
        val params = mutableMapOf(
            "language" to "en-US",
            "include_adult" to "false",
            "sort_by" to "popularity.desc",
            "page" to page.toString(),
        )
        if (rating != TmdbRatingFilter.ALL) {
            params["vote_average.gte"] = rating.minimum.toString()
            params["vote_count.gte"] = "1"
        }
        genreId?.let { params["with_genres"] = it.toString() }
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
