package com.lagradost.cloudstream3.ui.result

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.discover.DiscoverMediaType
import com.lagradost.cloudstream3.ui.discover.DiscoverRepository
import com.lagradost.cloudstream3.ui.discover.TmdbMetadata
import com.lagradost.cloudstream3.ui.discover.TmdbTitle
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** TMDB supplies metadata only. Selected titles are searched through installed providers. */
internal class ActorFilmographyRepository(
    private val request: suspend (String, Map<String, String>) -> String = TmdbMetadata::request,
) {
    @Serializable
    private data class TmdbPersonSearchResponse(
        @JsonProperty("results")
        @SerialName("results")
        val results: List<TmdbPerson>? = null,
    )

    @Serializable
    private data class TmdbPerson(
        @JsonProperty("id")
        @SerialName("id")
        val id: Int? = null,
        @JsonProperty("name")
        @SerialName("name")
        val name: String? = null,
        @JsonProperty("profile_path")
        @SerialName("profile_path")
        val profilePath: String? = null,
    )

    @Serializable
    private data class TmdbCombinedCredits(
        @JsonProperty("cast")
        @SerialName("cast")
        val cast: List<TmdbTitle>? = null,
    )

    private suspend fun resolvePersonId(actor: Actor): Int? {
        val actorName = actor.name.trim().takeIf { it.isNotEmpty() } ?: return null
        val people = parseJson<TmdbPersonSearchResponse>(
            request(
                "/search/person",
                mapOf("query" to actorName, "language" to "en-US", "include_adult" to "false"),
            )
        ).results.orEmpty().filter { (it.id ?: 0) > 0 }

        // Image paths survive TMDB's image-size variations and help disambiguate names.
        val imageFile = actor.image.imageFileName()
        val person = people.firstOrNull {
            imageFile != null && it.profilePath.imageFileName() == imageFile
        } ?: people.firstOrNull {
            it.name.equals(actorName, ignoreCase = true)
        } ?: people.firstOrNull()
        return person?.id
    }

    suspend fun details(actor: Actor): ActorDetails? {
        val id = resolvePersonId(actor) ?: return null
        return parseJson<ActorDetails>(request("/person/$id", mapOf("language" to "en-US")))
    }

    suspend fun load(actor: Actor): List<SearchResponse> {
        val id = resolvePersonId(actor) ?: return emptyList()
        val credits = parseJson<TmdbCombinedCredits>(
            request("/person/$id/combined_credits", mapOf("language" to "en-US"))
        ).cast.orEmpty()

        val filtered = credits.asSequence()
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .filter { it.usable }
            .distinctBy { it.mediaType to it.id }
            .sortedWith(
                compareByDescending<TmdbTitle> { it.popularity ?: 0.0 }
                    .thenByDescending { it.year ?: 0 }
            )
            .toList()
        if (filtered.isEmpty()) return emptyList()

        // Combined credits mix movies and series, so both catalogues are needed
        // for the poster genre strip. Fetch is skipped for empty results to keep
        // fast paths and existing tests network-light.
        val genreRepo = DiscoverRepository(request)
        val catalogue = coroutineScope {
            val movies = async(Dispatchers.IO) { genreRepo.genres(DiscoverMediaType.MOVIES) }
            val series = async(Dispatchers.IO) { genreRepo.genres(DiscoverMediaType.SERIES) }
            (movies.await() + series.await()).associate { it.id to it.name }
        }

        return filtered.map { it.toSearchResponse(genreNames = catalogue) }
    }

    private fun String?.imageFileName(): String? = this
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
}
