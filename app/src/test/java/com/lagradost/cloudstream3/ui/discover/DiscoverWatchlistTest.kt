package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class DiscoverWatchlistTest {
    private val entry = DiscoverWatchlist.Entry("Movie", "https://www.themoviedb.org/movie/12",
        false, null, 2020, 8.0, listOf("Drama"), 123L)

    @Test
    fun `legacy bookmarks without status stay on watchlist`() {
        val legacy = """[{"name":"Movie","url":"https://www.themoviedb.org/movie/12","tv":false,"poster":null,"year":2020,"rating":8.0,"genres":["Drama"],"added":123}]"""
        assertEquals(DiscoverWatchlist.Status.WATCHLIST,
            parseJson<List<DiscoverWatchlist.Entry>>(legacy).single().status)
    }

    @Test
    fun `status changes move one title without duplicates and can be undone`() {
        val card = entry.toLibraryItem()
        var entries = listOf(entry)
        for (status in DiscoverWatchlist.Status.entries) {
            entries = DiscoverWatchlist.updated(entries, card, status)
            assertEquals(1, entries.size)
            assertEquals(status, entries.single().status)
            assertEquals(2020, entries.single().year)
        }
        assertTrue(DiscoverWatchlist.updated(entries, card, null).isEmpty())
    }

    @Test
    fun `backup retains all three statuses across serialization for multiple profiles`() {
        val entries = DiscoverWatchlist.Status.entries.mapIndexed { index, status ->
            entry.copy(url = "https://www.themoviedb.org/movie/${index + 1}", status = status)
        }
        val keys = listOf("0/discover_watchlist", "1/discover_watchlist")
        assertTrue(keys.all { it.isTransferable() })
        val vars = BackupUtils.BackupVars(null, null, keys.associateWith { entries.toJson() }, null, null, null)
        val backup = BackupUtils.BackupFile(vars, BackupUtils.BackupVars(null, null, null, null, null, null))
        val restored = parseJson<BackupUtils.BackupFile>(backup.toJson())
        keys.forEach { key ->
            assertEquals(entries, parseJson<List<DiscoverWatchlist.Entry>>(restored.datastore.string!![key]!!))
        }
    }

    @Test
    fun `movie and series bookmarks keep distinct identities`() {
        val movie = DiscoverWatchlist.Entry("Movie", "https://www.themoviedb.org/movie/12",
            false, null, 2020, 8.0, listOf("Drama"), 123L).toLibraryItem()
        val series = DiscoverWatchlist.Entry("Series", "https://www.themoviedb.org/tv/12",
            true, null, 2021, null, null, 124L).toLibraryItem()
        assertNotEquals(movie.syncId, series.syncId)
        assertNotEquals(movie.id, series.id)
        assertEquals(TvType.Movie, movie.type)
        assertEquals(TvType.TvSeries, series.type)
        assertEquals(listOf("Drama"), movie.tags)
        assertEquals(8.0, movie.score!!.toDouble(), 0.01)
        assertTrue(DiscoverWatchlist.isItem(movie))
    }

    @Test
    fun `preview parses movie runtime and television seasons`() {
        val movie = parseJson<DiscoverPreview.Details>("""{"overview":"A story","runtime":120}""")
        assertEquals("A story", movie.overview)
        assertEquals(120, movie.runtime)
        assertNull(movie.seasons)
        val series = parseJson<DiscoverPreview.Details>("""{"number_of_seasons":3,"overview":null}""")
        assertEquals(3, series.seasons)
        assertNull(series.overview)
    }
}
