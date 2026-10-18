package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ui.result.ActorDetails
import com.lagradost.cloudstream3.ui.result.ActorFilmographyRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ActorDetailsTest {
    @Test
    fun `age respects birthday and death date`() {
        val actor = ActorDetails(birthday = "1974-11-11")
        assertEquals(51, actor.age(LocalDate.of(2026, 11, 10)))
        assertEquals(52, actor.age(LocalDate.of(2026, 11, 11)))
        assertEquals(40, actor.copy(deathday = "2015-01-01").age(LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun `invalid or missing dates never invent an age`() {
        val today = LocalDate.of(2026, 1, 1)
        assertNull(ActorDetails().age(today))
        assertNull(ActorDetails(birthday = "bad").age(today))
        assertNull(ActorDetails(birthday = "2027-01-01").age(today))
        assertNull(ActorDetails(birthday = "1974-11-11", deathday = "bad").age(today))
        assertNull(ActorDetails(birthday = "1974-11-11", deathday = "1970-01-01").age(today))
    }

    @Test
    fun `details reuse portrait disambiguation and do not fetch credits`() = runBlocking {
        val paths = mutableListOf<String>()
        val repository = ActorFilmographyRepository { path, _ ->
            paths.add(path)
            when (path) {
                "/search/person" -> """{"results":[{"id":1,"name":"Same Name","profile_path":"/wrong.jpg"},{"id":2,"name":"Same Name","profile_path":"/right.jpg"}]}"""
                "/person/2" -> """{"name":"Same Name","birthday":"1974-11-11","place_of_birth":"Los Angeles","biography":"A biography","known_for_department":"Acting"}"""
                else -> error("Unexpected request $path")
            }
        }
        val result = repository.details(Actor("Same Name", "https://image.tmdb.org/t/p/w500/right.jpg"))
        assertEquals(listOf("/search/person", "/person/2"), paths)
        assertEquals("A biography", result?.biography)
        assertEquals("Los Angeles", result?.birthplace)
    }
}
