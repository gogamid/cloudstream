package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.settings.utils.withOptionalPersistableGrant
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.videoskip.SkipStamp
import com.lagradost.cloudstream3.utils.videoskip.SkipType
import com.lagradost.cloudstream3.utils.videoskip.TheIntroDBSkip
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

class UpstreamMaintenanceTest {
    @Test fun `cached language patterns preserve English Russian and noisy labels`() {
        assertEquals("en", SubtitleHelper.fromLanguageToTagIETF("English (original audio) 1"))
        assertEquals("ru", SubtitleHelper.fromLanguageToTagIETF("Русский 123"))
        assertEquals("ru", SubtitleHelper.fromLanguageToTagIETF("Russian (sub)"))
        assertEquals("en", SubtitleHelper.fromLanguageToTagIETF("en"))
        assertNull(SubtitleHelper.fromLanguageToTagIETF(null))
        assertNull(SubtitleHelper.fromLanguageToTagIETF(""))
    }

    @Test fun `cached tag validation preserves regional and private tags`() {
        for (tag in listOf("en", "ru", "en-US", "es-419", "pt-BR", "zh-hant-TW", "x-custom")) {
            assertTrue(tag, SubtitleHelper.isWellFormedTagIETF(tag))
        }
        for (tag in listOf(null, "", "e", "en_US", "ru--RU", "en-", "123")) {
            assertFalse(tag, SubtitleHelper.isWellFormedTagIETF(tag))
        }
    }

    @Test fun `cached flag patterns retain regional flags and unknown handling`() {
        assertEquals("🇷🇺", SubtitleHelper.getFlagFromIso("ru"))
        assertEquals("🇺🇸", SubtitleHelper.getFlagFromIso("en-US"))
        assertEquals("🇧🇷", SubtitleHelper.getFlagFromIso("pt-BR"))
        assertNull(SubtitleHelper.getFlagFromIso(null))
        assertNull(SubtitleHelper.getFlagFromIso("not-a-country"))
    }

    @Test fun `shared patterns can be used concurrently without mutable matcher state`() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..16).map {
                executor.submit {
                    repeat(100) {
                        assertEquals("ru", SubtitleHelper.fromLanguageToTagIETF("Russian (original audio)"))
                        assertTrue(SubtitleHelper.isWellFormedTagIETF("es-419"))
                        assertEquals("🇷🇺", SubtitleHelper.getFlagFromIso("ru"))
                    }
                }
            }
            jobs.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `persistable folder grant leaves normal success unchanged`() {
        val events = mutableListOf<String>()
        withOptionalPersistableGrant(onDenied = { fail("Unexpected denial") }) {
            events += "permission"
        }
        events += "deliver URI"
        assertEquals(listOf("permission", "deliver URI"), events)
    }

    @Test fun `temporary-only folder grant logs denial and still delivers URI`() {
        val error = SecurityException("Provider only grants temporary access")
        val events = mutableListOf<String>()
        withOptionalPersistableGrant(onDenied = {
            assertSame(error, it)
            events += "log denial"
        }) { throw error }
        events += "deliver URI"
        assertEquals(listOf("log denial", "deliver URI"), events)
    }

    @Test fun `folder permission guard does not swallow unrelated failures`() {
        val error = IOException("Provider failed")
        try {
            withOptionalPersistableGrant(onDenied = { fail("Not a security error") }) { throw error }
            fail("Expected provider error")
        } catch (actual: IOException) {
            assertSame(error, actual)
        }
    }

    @Test fun `v4 skip response needs no tmdb field and flattens all stamp categories`() {
        val root = parseJson<TheIntroDBSkip.Root>("""{
            "type":"episode",
            "intro":[{"start_ms":1000,"end_ms":9000}],
            "credits":[{"start_ms":90000,"end_ms":100000}],
            "recap":[{"start_ms":0,"end_ms":1000}],
            "preview":[{"start_ms":80000,"end_ms":85000}]
        }""")
        assertEquals(listOf(
            SkipStamp(SkipType.Intro, 1000, 9000),
            SkipStamp(SkipType.Credits, 90000, 100000),
            SkipStamp(SkipType.Recap, 0, 1000),
            SkipStamp(SkipType.Preview, 80000, 85000),
        ), root.toStamps(100000))
    }

    @Test fun `empty v4 categories stay empty`() {
        val root = parseJson<TheIntroDBSkip.Root>("""{"type":"movie"}""")
        assertTrue(root.toStamps(100000).isEmpty())
    }

    @Test fun `nullable skip boundaries retain start and duration fallbacks`() {
        val root = parseJson<TheIntroDBSkip.Root>("""{
            "type":"movie", "credits":[{"start_ms":null,"end_ms":null}]
        }""")
        assertEquals(listOf(SkipStamp(SkipType.Credits, 0, 100000)), root.toStamps(100000))
    }
}
