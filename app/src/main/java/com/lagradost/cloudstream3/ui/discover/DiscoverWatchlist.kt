package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import kotlinx.serialization.Serializable
import java.util.Date

/** Profile-scoped TMDB bookmarks, deliberately separate from provider watch states. */
internal object DiscoverWatchlist {
    private const val SOURCE = "Discover Watchlist"
    private val storageKey get() = "$currentAccount/discover_watchlist"

    @Serializable
    data class Entry(
        val name: String,
        val url: String,
        val tv: Boolean,
        val poster: String?,
        val year: Int?,
        val rating: Double?,
        val genres: List<String>?,
        val added: Long,
    ) {
        fun toLibraryItem() = SyncAPI.LibraryItem(
            name = name, url = url, syncId = url,
            episodesCompleted = null, episodesTotal = null, personalRating = null,
            lastUpdatedUnixTime = added, apiName = SOURCE,
            type = if (tv) TvType.TvSeries else TvType.Movie,
            posterUrl = poster, posterHeaders = null, quality = null,
            releaseDate = year?.let { java.util.Calendar.getInstance().apply {
                clear(); set(it, 0, 1)
            }.time },
            id = url.substringAfterLast('/').toIntOrNull()?.let { if (tv) -it else it },
            score = rating?.let { Score.from10(it) }, tags = genres,
        )
    }

    fun entries(): List<Entry> = getKey<List<Entry>>(storageKey).orEmpty()
    fun contains(card: SearchResponse) = entries().any { it.url == card.url }
    fun isItem(card: SearchResponse) = card.apiName == SOURCE

    fun toggle(card: SearchResponse) {
        val old = entries()
        val next = if (old.any { it.url == card.url }) old.filterNot { it.url == card.url }
        else listOf(Entry(card.name, card.url, card.type == TvType.TvSeries,
            card.posterUrl, card.year, card.score?.toDouble(), card.genres,
            System.currentTimeMillis() / 1000)) + old
        setKey(storageKey, next)
        MainActivity.reloadLibraryEvent.invoke(true)
    }
}
