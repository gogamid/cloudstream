@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.utils.DataStoreHelper.ResumeWatchingResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceWarmupResolverTest {
    private val apiName = "source-warmup-resolver-fixture"
    private val url = "https://warmup.example/title"

    private fun card(parentId: Int, episodeId: Int) = ResumeWatchingResult(
        name = "Saved title", url = url, apiName = apiName, posterUrl = null,
        watchPos = null, id = episodeId, parentId = parentId,
        // Display metadata must not override the saved identity.
        episode = 999, season = 999, isFromDownload = false,
    )

    private fun movie(data: String = "movie-data") = MovieLoadResponse(
        name = "Movie", url = url, apiName = apiName, type = TvType.Movie, dataUrl = data,
    )

    private fun tv(episodes: List<Episode>) = TvSeriesLoadResponse(
        name = "Series", url = url, apiName = apiName, type = TvType.TvSeries,
        episodes = episodes,
    )

    @Test fun `movie uses response unique identity and exact saved episode id`() {
        val response = movie().apply { uniqueUrl = "stable-provider-identity" }
        val parentId = response.getId()
        assertEquals(SourceWarmupTarget(apiName, parentId, "movie-data"),
            matchSourceWarmupTarget(card(parentId, parentId), response))
        assertNull(matchSourceWarmupTarget(card(parentId, parentId + 1), response))
        assertNull(matchSourceWarmupTarget(card(parentId + 1, parentId), response))
    }

    @Test fun `anime includes dub and season in identity without switching to available dub`() {
        val response = AnimeLoadResponse(
            name = "Anime", url = url, apiName = apiName, type = TvType.Anime,
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(Episode("sub", season = 2, episode = 7)),
                DubStatus.Dubbed to listOf(Episode("dub", season = 2, episode = 7)),
            ),
        )
        val parentId = response.getId()
        val subId = parentId + 7 + 20_000
        val dubId = subId + 1_000_000
        assertEquals("sub", matchSourceWarmupTarget(card(parentId, subId), response)?.data)
        assertEquals("dub", matchSourceWarmupTarget(card(parentId, dubId), response)?.data)
        response.episodes.remove(DubStatus.Dubbed)
        assertNull(matchSourceWarmupTarget(card(parentId, dubId), response))
    }

    @Test fun `anime missing numbers use index within each dub list and None retains negative offset`() {
        val response = AnimeLoadResponse(
            name = "Anime", url = url, apiName = apiName, type = TvType.Anime,
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(Episode("explicit", episode = 42), Episode("sub-second")),
                DubStatus.Dubbed to listOf(Episode("dub-first")),
                DubStatus.None to listOf(Episode("none-first")),
            ),
        )
        val parentId = response.getId()
        assertEquals("sub-second", matchSourceWarmupTarget(card(parentId, parentId + 2), response)?.data)
        assertEquals("dub-first", matchSourceWarmupTarget(card(parentId, parentId + 1_000_001), response)?.data)
        assertEquals("none-first", matchSourceWarmupTarget(card(parentId, parentId - 999_999), response)?.data)
    }

    @Test fun `TV irregular explicit numbers and season use original ID formula including plus one`() {
        val response = tv(listOf(
            Episode("season-two", season = 2, episode = 3),
            Episode("special-zero", season = 0, episode = 0),
            Episode("episode-forty-two", season = 1, episode = 42),
        ))
        val parentId = response.getId()
        assertEquals("special-zero", matchSourceWarmupTarget(card(parentId, parentId + 1), response)?.data)
        assertEquals("episode-forty-two", matchSourceWarmupTarget(card(parentId, parentId + 100_043), response)?.data)
        assertEquals("season-two", matchSourceWarmupTarget(card(parentId, parentId + 200_004), response)?.data)
        assertNull(matchSourceWarmupTarget(card(parentId, parentId + 100_042), response))
    }

    @Test fun `TV missing numbers use global sorted index not provider order or season local index`() {
        val response = tv(listOf(
            Episode("season-two-missing", season = 2),
            Episode("season-one-explicit", season = 1, episode = 42),
            Episode("season-one-missing", season = 1),
            Episode("no-season-missing"),
        ))
        val parentId = response.getId()
        assertEquals("no-season-missing", matchSourceWarmupTarget(card(parentId, parentId + 2), response)?.data)
        assertEquals("season-one-missing", matchSourceWarmupTarget(card(parentId, parentId + 100_003), response)?.data)
        assertEquals("season-two-missing", matchSourceWarmupTarget(card(parentId, parentId + 200_005), response)?.data)
    }

    @Test fun `duplicate IDs and arithmetic collisions are ambiguous even with identical data`() {
        val duplicate = tv(listOf(Episode("same", episode = 4), Episode("same", episode = 4)))
        val parentId = duplicate.getId()
        assertNull(matchSourceWarmupTarget(card(parentId, parentId + 5), duplicate))
        val collision = tv(listOf(
            Episode("first", season = 1, episode = 100_001),
            Episode("second", season = 2, episode = 1),
        ))
        assertNull(matchSourceWarmupTarget(card(parentId, parentId + 200_002), collision))
    }

    @Test fun `anime cross dub ID collision is rejected instead of first wins`() {
        val response = AnimeLoadResponse(
            name = "Anime", url = url, apiName = apiName, type = TvType.Anime,
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(Episode("sub", season = 100, episode = 1)),
                DubStatus.Dubbed to listOf(Episode("dub", episode = 1)),
            ),
        )
        val parentId = response.getId()
        assertNull(matchSourceWarmupTarget(card(parentId, parentId + 1_000_001), response))
    }

    @Test fun `missing saved identity downloads and mismatched providers never match`() {
        val response = movie()
        val parentId = response.getId()
        val saved = card(parentId, parentId)
        assertNull(matchSourceWarmupTarget(saved.copy(id = null), response))
        assertNull(matchSourceWarmupTarget(saved.copy(parentId = null), response))
        assertNull(matchSourceWarmupTarget(saved.copy(isFromDownload = true), response))
        assertNull(matchSourceWarmupTarget(saved.copy(apiName = "other-provider"), response))
    }

    @Test fun `empty and known invalid descriptor data are not warmup targets`() {
        for (data in listOf("", "  ", "[]", "about:blank")) {
            val response = movie(data)
            val parentId = response.getId()
            assertNull(matchSourceWarmupTarget(card(parentId, parentId), response))
        }
    }

    @Test fun `resolver skips downloads missing identity and unavailable provider`() = runBlocking {
        assertNull(resolveSourceWarmupTarget(card(1, 1).copy(isFromDownload = true)))
        assertNull(resolveSourceWarmupTarget(card(1, 1).copy(id = null)))
        assertNull(resolveSourceWarmupTarget(card(1, 1).copy(parentId = null)))
        assertNull(resolveSourceWarmupTarget(card(1, 1)))
    }
}
