package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.Score
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbRatingFilterTest {
    @Test
    fun `rating thresholds are inclusive and never round a lower score up`() {
        assertFalse(TmdbRatingFilter.SIX.matches(Score.from10(5.999)))
        assertTrue(TmdbRatingFilter.SIX.matches(Score.from10(6.0)))
        assertFalse(TmdbRatingFilter.SEVEN.matches(Score.from10(6.999)))
        assertTrue(TmdbRatingFilter.SEVEN.matches(Score.from10(7.0)))
        assertTrue(TmdbRatingFilter.SEVEN.matches(Score.from10(10.0)))
    }

    @Test
    fun `only all ratings includes unrated cards`() {
        assertTrue(TmdbRatingFilter.ALL.matches(null))
        assertFalse(TmdbRatingFilter.SIX.matches(null))
        assertFalse(TmdbRatingFilter.SEVEN.matches(null))
    }

    @Test
    fun `zero votes and invalid TMDB averages are unrated`() {
        for (average in listOf(null, 0.0, -1.0, 10.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(TmdbTitle(id = 1, title = "Movie", voteAverage = average).toSearchResponse().score)
        }
        assertNull(
            TmdbTitle(id = 1, title = "Movie", voteAverage = 8.0, voteCount = 0).toSearchResponse().score
        )
        // Combined credits may omit vote_count; a valid average remains usable.
        assertTrue(
            TmdbRatingFilter.SEVEN.matches(
                TmdbTitle(id = 1, title = "Movie", voteAverage = 8.0).toSearchResponse().score
            )
        )
    }
}
