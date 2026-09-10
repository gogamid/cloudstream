package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.junit.Assert.*
import org.junit.Test

class DiscoverWatchlistTest {
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
