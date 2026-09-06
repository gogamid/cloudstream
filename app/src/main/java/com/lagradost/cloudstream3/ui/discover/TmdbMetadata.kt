package com.lagradost.cloudstream3.ui.discover

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shared metadata transport and cards for discovery and actor credits. */
internal object TmdbMetadata {
    // The application key already used by TmdbProvider and actor filmography.
    private const val API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
    private const val API_URL = "https://api.themoviedb.org/3"
    const val IMAGE_URL = "https://image.tmdb.org/t/p/w500"

    val cards = object : MainAPI() {
        override var name = "TMDB"
    }

    suspend fun request(path: String, params: Map<String, String>): String {
        val response = app.get(API_URL + path, params = params + ("api_key" to API_KEY))
        check(response.isSuccessful) { "TMDB request failed (${response.code})" }
        return response.text
    }
}

internal enum class TmdbRatingFilter(val minimum: Int) {
    ALL(0),
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10);

    fun matches(score: Score?): Boolean =
        this == ALL || (score?.toDouble()?.let { it >= minimum } == true)
}

@Serializable
internal data class TmdbTitle(
    @JsonProperty("id") @SerialName("id") val id: Int? = null,
    @JsonProperty("title") @SerialName("title") val title: String? = null,
    @JsonProperty("original_title") @SerialName("original_title") val originalTitle: String? = null,
    @JsonProperty("name") @SerialName("name") val name: String? = null,
    @JsonProperty("original_name") @SerialName("original_name") val originalName: String? = null,
    @JsonProperty("poster_path") @SerialName("poster_path") val posterPath: String? = null,
    @JsonProperty("vote_average") @SerialName("vote_average") val voteAverage: Double? = null,
    @JsonProperty("vote_count") @SerialName("vote_count") val voteCount: Int? = null,
    @JsonProperty("release_date") @SerialName("release_date") val releaseDate: String? = null,
    @JsonProperty("first_air_date") @SerialName("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("media_type") @SerialName("media_type") val mediaType: String? = null,
    @JsonProperty("popularity") @SerialName("popularity") val popularity: Double? = null,
    @JsonProperty("adult") @SerialName("adult") val adult: Boolean? = null,
    @JsonProperty("genre_ids") @SerialName("genre_ids") val genreIds: List<Int>? = null,
) {
    val displayTitle: String
        get() = listOf(title, name, originalTitle, originalName)
            .firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

    val year: Int?
        get() = (releaseDate?.takeIf { it.isNotBlank() } ?: firstAirDate)
            ?.take(4)?.toIntOrNull()

    val usable: Boolean
        get() = adult != true && (id ?: 0) > 0 && displayTitle.isNotBlank()

    /** First three catalogue names for the poster overlay; unknown IDs are dropped. */
    fun resolveGenres(catalogue: Map<Int, String>): List<String>? =
        genreIds?.asSequence()?.distinct()?.mapNotNull { catalogue[it] }?.take(3)?.toList()
            ?.takeIf { it.isNotEmpty() }

    fun toSearchResponse(
        type: String = mediaType.orEmpty(),
        genreNames: Map<Int, String> = emptyMap(),
    ): SearchResponse = with(TmdbMetadata.cards) {
        val isTv = type == "tv"
        // SearchAdapter compares IDs without the media type.
        val cardId = id?.let { if (isTv) -it else it }
        val rating = voteAverage?.takeIf {
            it.isFinite() && it > 0 && it <= 10 && (voteCount == null || voteCount > 0)
        }?.let { Score.from10(it) }
        val poster = posterPath?.takeIf { it.isNotBlank() }?.let { TmdbMetadata.IMAGE_URL + it }
        if (isTv) {
            newTvSeriesSearchResponse(
                name = displayTitle, url = "https://www.themoviedb.org/tv/$id",
                type = TvType.TvSeries, fix = false,
            ) {
                this.id = cardId
                posterUrl = poster
                score = rating
                year = this@TmdbTitle.year
                genres = resolveGenres(genreNames)
            }
        } else {
            newMovieSearchResponse(
                name = displayTitle, url = "https://www.themoviedb.org/movie/$id",
                type = TvType.Movie, fix = false,
            ) {
                this.id = cardId
                posterUrl = poster
                score = rating
                year = this@TmdbTitle.year
                genres = resolveGenres(genreNames)
            }
        }
    }
}
