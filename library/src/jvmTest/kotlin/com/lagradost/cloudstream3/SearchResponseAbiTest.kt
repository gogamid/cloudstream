package com.lagradost.cloudstream3

import java.lang.invoke.MethodType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotSame

class SearchResponseAbiTest {
    // Erased primary-constructor parameter descriptors from MainAPI.kt at
    // eb962b4fb7b1387b8de42ec58000cee0a01fe741. Do not derive these from the
    // current constructors: recompiling Kotlin calls alone cannot catch ABI breaks.
    private val baseline = listOf(
        AnimeSearchResponse::class.java to (
            "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
                "Lcom/lagradost/cloudstream3/TvType;Ljava/lang/String;Ljava/lang/Integer;" +
                "Ljava/util/Set;Ljava/lang/String;Ljava/util/Map;Ljava/lang/Integer;" +
                "Lcom/lagradost/cloudstream3/SearchQuality;Ljava/util/Map;" +
                "Lcom/lagradost/cloudstream3/Score;"
            ),
        MovieSearchResponse::class.java to (
            "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
                "Lcom/lagradost/cloudstream3/TvType;Ljava/lang/String;" +
                "Ljava/lang/Integer;Ljava/lang/Integer;" +
                "Lcom/lagradost/cloudstream3/SearchQuality;Ljava/util/Map;" +
                "Lcom/lagradost/cloudstream3/Score;"
            ),
        TvSeriesSearchResponse::class.java to (
            "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" +
                "Lcom/lagradost/cloudstream3/TvType;Ljava/lang/String;" +
                "Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/Integer;" +
                "Lcom/lagradost/cloudstream3/SearchQuality;Ljava/util/Map;" +
                "Lcom/lagradost/cloudstream3/Score;"
            ),
    )

    @Test
    fun previousConstructorsAndGeneratedCopiesRemainInvocable() {
        for ((type, parameters) in baseline) {
            val parameterTypes = MethodType.fromMethodDescriptorString(
                "($parameters)V", type.classLoader
            ).parameterArray()
            val arguments = arrayOfNulls<Any>(parameterTypes.size).apply {
                this[0] = "Title"
                this[1] = "https://example.com/title"
                this[2] = "Provider"
                if (type == AnimeSearchResponse::class.java) this[8] = mutableMapOf<DubStatus, Int>()
            }
            val original = type.getConstructor(*parameterTypes).newInstance(*arguments)
            assertEquals("Title", original.name)

            // Kotlin's default-argument constructor has the original parameters,
            // one mask int, and DefaultConstructorMarker (not Object).
            val defaultTypes = MethodType.fromMethodDescriptorString(
                "(${parameters}ILkotlin/jvm/internal/DefaultConstructorMarker;)V", type.classLoader
            ).parameterArray()
            val defaults = type.getConstructor(*defaultTypes).newInstance(
                *arguments, -8, null
            )
            assertEquals(original, defaults)

            val copy = type.getMethod("copy", *parameterTypes)
            assertEquals(type, copy.returnType)
            val copied = copy.invoke(original, *arguments)
            assertEquals(original, copied)
            assertNotSame(original, copied)

            val defaultCopy = type.getMethod(
                "copy\$default", type, *parameterTypes, Int::class.javaPrimitiveType!!, Any::class.java
            )
            assertEquals(type, defaultCopy.returnType)
            val defaultCopied = defaultCopy.invoke(null, original, *arguments, -1, null)
            assertEquals(original, defaultCopied)
            assertNotSame(original, defaultCopied)

            // Movie/TV also exposed score-less secondary constructors, including
            // their own default-argument bridges. Keep those entry points too.
            if (type != AnimeSearchResponse::class.java) {
                val legacyTypes = parameterTypes.dropLast(1).toTypedArray()
                val legacyArguments = arguments.dropLast(1).toTypedArray()
                assertEquals(original, type.getConstructor(*legacyTypes).newInstance(*legacyArguments))
                assertEquals(
                    original,
                    type.getConstructor(*legacyTypes, *defaultTypes.takeLast(2).toTypedArray())
                        .newInstance(*legacyArguments, -32, null),
                )
            }
        }
    }

    @Test
    fun factoriesStillSetDiscoveryMetadataThroughSearchResponse() {
        val api = object : MainAPI() {}
        val responses = listOf(
            api.newAnimeSearchResponse("Anime", "/anime", fix = false) {
                genres = listOf("Animation")
                originalLanguage = "ja"
            },
            api.newMovieSearchResponse("Movie", "/movie", fix = false) {
                genres = listOf("Animation")
                originalLanguage = "ja"
            },
            api.newTvSeriesSearchResponse("TV", "/tv", fix = false) {
                genres = listOf("Animation")
                originalLanguage = "ja"
            },
        )
        for (response in responses) {
            assertEquals(listOf("Animation"), response.genres)
            assertEquals("ja", response.originalLanguage)
            response.genres = null
            response.originalLanguage = null
            assertNull(response.genres)
            assertNull(response.originalLanguage)
        }
    }
}
