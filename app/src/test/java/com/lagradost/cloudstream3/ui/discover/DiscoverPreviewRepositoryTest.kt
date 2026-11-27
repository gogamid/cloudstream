package com.lagradost.cloudstream3.ui.discover

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverPreviewRepositoryTest {
    @Test
    fun `movie details include ordered and distinct cast without another request`() = runBlocking {
        val paths = mutableListOf<String>()
        val repository = DiscoverPreviewRepository { path, params ->
            paths += path
            assertEquals(mapOf("language" to "en-US", "append_to_response" to "credits"), params)
            """{"overview":"Synopsis","runtime":112,"credits":{"cast":[
                {"id":99,"name":" Alex Smith ","character":"Lead","profile_path":"/actor.jpg"},
                {"id":99,"name":"Alex Smith","character":"A second role"},
                {"id":22,"name":"Alex Smith"},
                {"id":6,"name":" "},
                {"name":"Missing ID"}
            ]}}"""
        }
        val details = repository.load(DiscoverMediaType.MOVIES, 13, includeCast = true)
        assertEquals(listOf("/movie/13"), paths)
        assertEquals("Synopsis", details.overview)
        assertEquals(112, details.runtime)
        val cast = repository.cast(details)
        assertEquals(listOf(99, 22), cast.map { it.id })
        assertEquals("Lead", cast.first().character)
        assertEquals("Alex Smith", cast.first().toActor().name)
        assertEquals("https://image.tmdb.org/t/p/w185/actor.jpg", cast.first().toActor().image)
        assertNull(cast.last().toActor().image)
    }

    @Test
    fun `series details respect the cast setting and tolerate missing credits`() = runBlocking {
        val repository = DiscoverPreviewRepository { path, params ->
            assertEquals("/tv/21", path)
            assertEquals(mapOf("language" to "en-US"), params)
            """{"number_of_seasons":3,"overview":"Synopsis"}"""
        }
        val details = repository.load(DiscoverMediaType.SERIES, 21, includeCast = false)
        assertEquals(3, details.seasons)
        assertTrue(repository.cast(details).isEmpty())
    }
}
