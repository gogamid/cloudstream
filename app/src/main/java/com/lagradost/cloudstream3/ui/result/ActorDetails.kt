package com.lagradost.cloudstream3.ui.result

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.Period

@Serializable
internal data class ActorDetails(
    @JsonProperty("name") @SerialName("name") val name: String? = null,
    @JsonProperty("birthday") @SerialName("birthday") val birthday: String? = null,
    @JsonProperty("deathday") @SerialName("deathday") val deathday: String? = null,
    @JsonProperty("place_of_birth") @SerialName("place_of_birth") val birthplace: String? = null,
    @JsonProperty("biography") @SerialName("biography") val biography: String? = null,
    @JsonProperty("profile_path") @SerialName("profile_path") val profilePath: String? = null,
    @JsonProperty("known_for_department") @SerialName("known_for_department") val department: String? = null,
) {
    fun age(today: LocalDate = LocalDate.now()): Int? = runCatching {
        val born = LocalDate.parse(birthday ?: return null)
        val end = deathday?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: today
        if (born > end || end > today) return null
        Period.between(born, end).years
    }.getOrNull()
}
