package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DiscoverRepositoryTest {
    @Test
    fun `movie discovery combines rating genre year sort and requested page on the server`() = runBlocking {
        val repository = DiscoverRepository { path, params ->
            assertEquals("/discover/movie", path)
            assertEquals("7", params["vote_average.gte"])
            assertEquals("1", params["vote_count.gte"])
            assertEquals("27", params["with_genres"])
            assertEquals("2024", params["primary_release_year"])
            assertEquals("popularity.desc", params["sort_by"])
            assertEquals("2", params["page"])
            assertEquals("false", params["include_adult"])
            assertEquals("popularity.desc", params["sort_by"])
            """{"total_pages":3,"results":[
                {"id":1,"title":"Exact threshold","vote_average":7.0,"vote_count":120},
                {"id":2,"title":"Below threshold","vote_average":6.999},
                {"id":3,"title":"Unrated"},
                {"id":4,"title":"No votes","vote_average":8.0,"vote_count":0}
            ]}"""
        }
        val page = repository.discover(
            DiscoverMediaType.MOVIES, TmdbRatingFilter.SEVEN, setOf(27), 2024,
            DiscoverSort.POPULAR, 2,
        )
        assertEquals(listOf("Exact threshold"), page.results.map { it.name })
        assertTrue(page.hasMore)
    }

    @Test
    fun `multiple genres use OR and default filters omit restrictions`() = runBlocking {
        val repository = DiscoverRepository { _, params ->
            assertEquals("27|35|80", params["with_genres"])
            assertFalse(params.containsKey("vote_average.gte"))
            assertFalse(params.containsKey("vote_count.gte"))
            assertFalse(params.containsKey("primary_release_year"))
            assertEquals("popularity.desc", params["sort_by"])
            """{"total_pages":1,"results":[{"id":1,"title":"Unrated"}]}"""
        }
        val page = repository.discover(
            DiscoverMediaType.MOVIES, TmdbRatingFilter.ALL, setOf(80, 27, 35), null,
            DiscoverSort.POPULAR, 1,
        )
        assertEquals("Unrated", page.results.single().name)
        assertNull(page.results.single().score)
        assertFalse(page.hasMore)
    }

    @Test
    fun `TV discovery uses TV endpoints year key genres dates and distinct card IDs`() = runBlocking {
        val paths = mutableListOf<String>()
        var discoverParams: Map<String, String> = emptyMap()
        val repository = DiscoverRepository { path, params ->
            paths += path
            when (path) {
                "/genre/tv/list" -> """{"genres":[{"id":35,"name":"Comedy"},{"id":9648,"name":"Mystery"}]}"""
                "/discover/tv" -> {
                    discoverParams = params
                    """{"total_pages":1,"results":[
                        {"id":12,"name":"Series","first_air_date":"2022-01-01","poster_path":"/tv.jpg"}
                    ]}"""
                }
                else -> error(path)
            }
        }
        assertEquals(listOf(35, 9648), repository.genres(DiscoverMediaType.SERIES).map { it.id })
        val card = repository.discover(
            DiscoverMediaType.SERIES, TmdbRatingFilter.ALL, setOf(35), 2022,
            DiscoverSort.NEWEST, 1,
        ).results.single() as TvSeriesSearchResponse
        assertEquals(listOf("/genre/tv/list", "/discover/tv"), paths)
        assertEquals("2022", discoverParams["first_air_date_year"])
        assertFalse(discoverParams.containsKey("primary_release_year"))
        assertEquals("first_air_date.desc", discoverParams["sort_by"])
        assertEquals("35", discoverParams["with_genres"])
        assertEquals(2022, card.year)
        assertEquals("https://www.themoviedb.org/tv/12", card.url)
        assertEquals("https://image.tmdb.org/t/p/w500/tv.jpg", card.posterUrl)
        assertNotEquals(TmdbTitle(id = 12, title = "Movie").toSearchResponse().id, card.id)
    }

    @Test
    fun `sort mapping covers both catalogues`() {
        assertEquals("popularity.desc", DiscoverSort.POPULAR.sortBy(DiscoverMediaType.MOVIES))
        assertEquals("popularity.desc", DiscoverSort.POPULAR.sortBy(DiscoverMediaType.SERIES))
        assertEquals("vote_average.desc", DiscoverSort.TOP_RATED.sortBy(DiscoverMediaType.SERIES))
        assertEquals(
            "primary_release_date.desc",
            DiscoverSort.NEWEST.sortBy(DiscoverMediaType.MOVIES),
        )
        assertEquals("first_air_date.desc", DiscoverSort.NEWEST.sortBy(DiscoverMediaType.SERIES))
        assertEquals("first_air_date.asc", DiscoverSort.OLDEST.sortBy(DiscoverMediaType.SERIES))
        assertEquals("original_title.asc", DiscoverSort.TITLE_AZ.sortBy(DiscoverMediaType.MOVIES))
    }

    @Test
    fun `top rated sorting requires a meaningful vote floor`() = runBlocking {
        val repository = DiscoverRepository { _, params ->
            assertEquals("vote_average.desc", params["sort_by"])
            assertEquals("25", params["vote_count.gte"])
            """{"total_pages":1,"results":[]}"""
        }
        repository.discover(
            DiscoverMediaType.MOVIES, TmdbRatingFilter.ALL, emptySet(), null,
            DiscoverSort.TOP_RATED, 1,
        )
        Unit
    }

    @Test
    fun `malformed adult and duplicate cards are excluded and titles fall back`() = runBlocking {
        val repository = DiscoverRepository { _, _ ->
            """{"total_pages":1,"results":[
                {"id":1,"title":" ","original_title":" Original ","release_date":"2020-01-01"},
                {"id":1,"title":"Duplicate"},
                {"id":2,"title":"Adult","adult":true},
                {"id":0,"title":"Bad ID"},
                {"title":"No ID"},
                {"id":3,"title":""}
            ]}"""
        }
        val card = repository.discover(
            DiscoverMediaType.MOVIES, TmdbRatingFilter.ALL, emptySet(), null,
            DiscoverSort.POPULAR, 1,
        ).results.single() as MovieSearchResponse
        assertEquals("Original", card.name)
        assertEquals(2020, card.year)
        assertEquals("TMDB", card.apiName)
        assertEquals("https://www.themoviedb.org/movie/1", card.url)
    }

    @Test
    fun `genre lists drop missing names invalid IDs and duplicates`() = runBlocking {
        val repository = DiscoverRepository { path, _ ->
            assertEquals("/genre/movie/list", path)
            """{"genres":[{"id":27,"name":"Horror"},{"id":27,"name":"Horror"},
                {"id":0,"name":"Unknown"},{"id":53,"name":""}]}"""
        }
        assertEquals(listOf(TmdbGenre(27, "Horror")), repository.genres(DiscoverMediaType.MOVIES))
    }

    @Test
    fun `pagination respects TMDB page limit and empty responses`() = runBlocking {
        val repository = DiscoverRepository { _, _ -> """{"total_pages":900,"results":[]}""" }
        assertFalse(
            repository.discover(
                DiscoverMediaType.MOVIES, TmdbRatingFilter.ALL, emptySet(), null,
                DiscoverSort.POPULAR, 500,
            ).hasMore
        )
        val empty = DiscoverRepository { _, _ -> "{}" }
            .discover(
                DiscoverMediaType.SERIES, TmdbRatingFilter.ALL, emptySet(), null,
                DiscoverSort.POPULAR, 1,
            )
        assertTrue(empty.results.isEmpty())
        assertFalse(empty.hasMore)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid genre and page filters are rejected`() = runBlocking {
        DiscoverRepository { _, _ -> "{}" }
            .discover(
                DiscoverMediaType.MOVIES, TmdbRatingFilter.ALL, setOf(-1), null,
                DiscoverSort.POPULAR, 1,
            )
        Unit
    }

    @Test(expected = IOException::class)
    fun `network errors propagate to the retry state`() = runBlocking {
        DiscoverRepository { _, _ -> throw IOException("Offline") }
            .discover(
                DiscoverMediaType.MOVIES, TmdbRatingFilter.ALL, emptySet(), null,
                DiscoverSort.POPULAR, 1,
            )
        Unit
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates when filters change`() = runBlocking {
        DiscoverRepository { _, _ -> throw CancellationException("New filter") }
            .discover(
                DiscoverMediaType.SERIES, TmdbRatingFilter.ALL, emptySet(), null,
                DiscoverSort.POPULAR, 1,
            )
        Unit
    }
}
