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


/** Profile-scoped TMDB bookmarks, deliberately separate from provider watch states. */
internal object DiscoverWatchlist {
    private const val SOURCE = "Discover Watchlist"
    private val storageKey get() = "$currentAccount/discover_watchlist"

    enum class Status { WATCHLIST, COMPLETED, IGNORED }

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
        val status: Status = Status.WATCHLIST,
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

    // Keep the original key: old bookmarks default to WATCHLIST and all statuses
    // travel together through the existing datastore backup/restore mechanism.
    fun allEntries(): List<Entry> = getKey<List<Entry>>(storageKey).orEmpty()
    fun entries(status: Status = Status.WATCHLIST) = allEntries().filter { it.status == status }
    fun status(card: SearchResponse) = allEntries().firstOrNull { it.url == card.url }?.status
    fun contains(card: SearchResponse) = status(card) == Status.WATCHLIST
    fun isItem(card: SearchResponse) = card.apiName == SOURCE

    internal fun updated(old: List<Entry>, card: SearchResponse, status: Status?): List<Entry> {
        val remaining = old.filterNot { it.url == card.url }
        if (status == null) return remaining
        val previous = old.firstOrNull { it.url == card.url }
        val entry = previous?.copy(status = status) ?: Entry(
            card.name, card.url, card.type == TvType.TvSeries,
            card.posterUrl, card.year, card.score?.toDouble(),
            card.genres ?: (card as? SyncAPI.LibraryItem)?.tags,
            System.currentTimeMillis() / 1000, status)
        return listOf(entry) + remaining
    }

    fun setStatus(card: SearchResponse, status: Status?) {
        setKey(storageKey, updated(allEntries(), card, status))
        MainActivity.reloadLibraryEvent.invoke(true)
    }

    fun toggle(card: SearchResponse) = setStatus(card, if (contains(card)) null else Status.WATCHLIST)
}
