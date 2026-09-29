package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class FilterDefaultsTest {
    @Test fun `defaults roundtrip and remain independent backup keys`() {
        val value = FilterDefaults("SERIES", "RUSSIAN", 8, setOf(18), setOf("Drama"), 2000, 2025, "NEWEST")
        assertEquals(value, parseJson<FilterDefaults>(value.toJson()))
        assertNotEquals(FilterDefaults.key(true), FilterDefaults.key(false))
        assertTrue(FilterDefaults.key(true).isTransferable())
        assertTrue(FilterDefaults.key(false).isTransferable())
    }

    @Test fun `invalid saved options fall back safely and reversed ranges normalize`() {
        val value = FilterDefaults("invalid", "invalid", 99, setOf(-1, 18), setOf(""), 2020, 1990, "invalid")
            .normalized(false)
        assertEquals(FilterDefaults(yearFrom = 1990, yearTo = 2020, genreIds = setOf(18)), value)
        assertEquals("ALL", FilterDefaults(type = "ALL").normalized(true).type)
        assertEquals("MOVIES", FilterDefaults(type = "ALL").normalized(false).type)
        assertNull(FilterDefaults(yearFrom = 1000).normalized(false).yearFrom)
    }

    @Test fun `reset visibility compares filters with configured defaults`() {
        val defaults = FilterDefaults(type = "SERIES", language = "ALL", rating = 0, yearFrom = 2010)
        val state = DiscoverState(type = defaults.typeValue(), language = defaults.languageValue(),
            rating = defaults.ratingValue(), yearFrom = 2010, defaults = defaults)
        assertTrue(state.isDefault)
        assertFalse(state.copy(yearFrom = null).isDefault)
        assertTrue(DiscoverState().isDefault)
    }

    @Test fun `completed and dropped are hidden but planned titles remain visible`() {
        fun entry(url: String, status: DiscoverWatchlist.Status) = DiscoverWatchlist.Entry(
            "Movie", url, false, null, null, null, null, 0L, status)
        val entries = listOf(entry("movie/1", DiscoverWatchlist.Status.COMPLETED),
            entry("movie/2", DiscoverWatchlist.Status.IGNORED),
            entry("movie/3", DiscoverWatchlist.Status.WATCHLIST))
        assertEquals(setOf("movie/1", "movie/2"), DiscoverWatchlist.hiddenUrls(entries))
        assertTrue(DiscoverWatchlist.hiddenUrls(emptyList()).isEmpty())
    }
}
