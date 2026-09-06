package com.lagradost.cloudstream3.ui.result

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.discover.TmdbMetadata
import com.lagradost.cloudstream3.ui.discover.TmdbTitle
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
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

    suspend fun load(actor: Actor): List<SearchResponse> {
        val actorName = actor.name.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
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
        } ?: people.firstOrNull() ?: return emptyList()

        val credits = parseJson<TmdbCombinedCredits>(
            request("/person/${person.id}/combined_credits", mapOf("language" to "en-US"))
        ).cast.orEmpty()

        return credits.asSequence()
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .filter { it.usable }
            .distinctBy { it.mediaType to it.id }
            .sortedWith(
                compareByDescending<TmdbTitle> { it.popularity ?: 0.0 }
                    .thenByDescending { it.year ?: 0 }
            )
            .map { it.toSearchResponse() }
            .toList()
    }

    private fun String?.imageFileName(): String? = this
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
}
