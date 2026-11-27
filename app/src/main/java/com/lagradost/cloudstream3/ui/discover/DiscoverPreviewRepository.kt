package com.lagradost.cloudstream3.ui.discover

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal class DiscoverPreviewRepository(
    private val request: suspend (String, Map<String, String>) -> String = TmdbMetadata::request,
) {
    @Serializable
    data class Details(
        @JsonProperty("overview") @SerialName("overview") val overview: String? = null,
        @JsonProperty("runtime") @SerialName("runtime") val runtime: Int? = null,
        @JsonProperty("number_of_seasons") @SerialName("number_of_seasons") val seasons: Int? = null,
        @JsonProperty("credits") @SerialName("credits") val credits: Credits? = null,
    )

    @Serializable
    data class Credits(
        @JsonProperty("cast") @SerialName("cast") val cast: List<CastMember>? = null,
    )

    @Serializable
    data class CastMember(
        @JsonProperty("id") @SerialName("id") val id: Int = 0,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("character") @SerialName("character") val character: String? = null,
        @JsonProperty("profile_path") @SerialName("profile_path") val profilePath: String? = null,
    ) {
        fun toActor() = Actor(
            name.orEmpty().trim(),
            profilePath?.takeIf { it.startsWith("/") }?.let { TmdbMetadata.PROFILE_IMAGE_URL + it },
        )
    }

    suspend fun load(type: DiscoverMediaType, id: Int, includeCast: Boolean): Details {
        require(id > 0)
        val params = mutableMapOf("language" to "en-US")
        if (includeCast) params["append_to_response"] = "credits"
        return parseJson<Details>(request("/${type.path}/$id", params))
    }

    fun cast(details: Details): List<CastMember> = details.credits?.cast.orEmpty()
        .filter { it.id > 0 && !it.name.isNullOrBlank() }
        .distinctBy { it.id }
}
