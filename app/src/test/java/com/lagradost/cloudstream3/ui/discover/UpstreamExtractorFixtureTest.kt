@file:Suppress("DEPRECATION_ERROR", "OPT_IN_USAGE")

package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.extractors.FirestreamSite
import com.lagradost.cloudstream3.extractors.Hexload
import com.lagradost.cloudstream3.extractors.Vidsonic
import com.lagradost.cloudstream3.extractors.Vixeo
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.videoskip.SkipStamp
import com.lagradost.cloudstream3.utils.videoskip.SkipType
import com.lagradost.cloudstream3.utils.videoskip.TheIntroDBSkip
import com.lagradost.nicehttp.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Collections

/** Intercept every request: these fixtures never contact an extractor or intro service. */
class UpstreamExtractorFixtureTest {
    private fun withRequests(
        respond: (Request) -> String,
        block: suspend (List<Request>) -> Unit,
    ) = runBlocking {
        val original = app
        val calls = Collections.synchronizedList(mutableListOf<Request>())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            calls += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(respond(request).toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }.build()
        app = Requests(baseClient = client, responseParser = original.responseParser)
        try {
            block(calls)
        } finally {
            app = original
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun encoded(url: String): String = url.reversed().map {
        it.code.toString(16).padStart(2, '0')
    }.chunked(4).joinToString("|") { it.joinToString("") }

    private fun unexpected(request: Request): Nothing =
        throw IOException("Unexpected fixture request: ${request.method} ${request.url}")

    @Test fun `Vidsonic retains existing JavaScript stream decoding`() {
        val stream = "https://video.example/master.m3u8?token=fixture"
        withRequests(respond = { request ->
            if (request.url.host == "vidsonic.net") "<script>const _0x1 = '${encoded(stream)}';</script>"
            else unexpected(request)
        }) { calls ->
            val links = mutableListOf<ExtractorLink>()
            Vidsonic().getUrl("https://vidsonic.net/e/fixture", null, {}, { links += it })
            assertEquals(stream, links.single().url)
            assertEquals(ExtractorLinkType.M3U8, links.single().type)
            assertEquals(1, calls.size)
        }
    }

    @Test fun `Vixeo resolves base64 player configuration through shared decoder`() {
        val stream = "https://video.example/master.m3u8?server=2"
        val config = base64Encode("""{"source":"${encoded(stream)}"}""".toByteArray())
        withRequests(respond = { request ->
            if (request.url.host == "vixeo.io") """<div id="streamsonic-player-root" data-config="$config"></div>"""
            else unexpected(request)
        }) { calls ->
            val links = mutableListOf<ExtractorLink>()
            Vixeo().getUrl("https://vixeo.io/e/fixture", null, {}, { links += it })
            assertEquals(stream, links.single().url)
            assertEquals("Vixeo", links.single().source)
            assertEquals(1, calls.size)
        }
    }

    @Test fun `malformed Vixeo configuration cannot publish a broken stream`() {
        withRequests(respond = { """<div id="streamsonic-player-root" data-config="invalid!"></div>""" }) {
            val links = mutableListOf<ExtractorLink>()
            try {
                Vixeo().getUrl("https://vixeo.io/e/fixture", null, {}, { links += it })
                fail("Expected malformed configuration error")
            } catch (_: Exception) { }
            assertTrue(links.isEmpty())
        }
    }

    @Test fun `Firestream site mirror query is not included in the resolver video ID`() {
        withRequests(respond = { request ->
            when (request.method to request.url.encodedPath) {
                "GET" to "/e/fixture" -> """<script id="token-blob">fixture-blob</script>"""
                "POST" to "/api/videos/fixture/resolve" -> """{"signedVideoUrl":"https://video.example/file.mp4"}"""
                else -> unexpected(request)
            }
        }) { calls ->
            val links = mutableListOf<ExtractorLink>()
            FirestreamSite().getUrl("https://firestream.site/e/fixture?mirror=2", null, {}, { links += it })
            assertEquals("https://video.example/file.mp4", links.single().url)
            assertEquals(2, calls.size)
            assertTrue(calls.all { it.url.host == "firestream.site" && it.url.query == null })
            val body = Buffer().also { calls.last().body!!.writeTo(it) }.readUtf8()
            assertEquals("fixture-blob", parseJson<Map<String, String>>(body)["blob"])
        }
    }

    @Test fun `Hexload submits parsed form arguments and publishes the resolved file`() {
        withRequests(respond = { request ->
            when (request.method to request.url.encodedPath) {
                "GET" to "/fixture" -> """<script>data: {op:'download2', id:'fixture', token:'opaque_token'}, success: done</script>"""
                "POST" to "/download" -> """{
                    "status":200,"msg":"OK","server_time":"170000",
                    "result":{"size":"100","url":"https://video.example/file.mp4",
                    "content_type":"video/mp4","thumb_url":"","image_url":"","file_name":"fixture.mp4","md5":""}
                }"""
                else -> unexpected(request)
            }
        }) { calls ->
            val links = mutableListOf<ExtractorLink>()
            Hexload().getUrl("https://hexload.com/fixture", null, {}, { links += it })
            assertEquals("https://video.example/file.mp4", links.single().url)
            assertEquals("Hexload", links.single().source)
            val form = calls.last().body as FormBody
            val arguments = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertEquals(mapOf("op" to "download2", "id" to "fixture", "token" to "opaque_token"), arguments)
            assertEquals(2, calls.size)
        }
    }

    private fun movie() = MovieLoadResponse("Fixture", "https://fixture.example/movie", "Fixture", TvType.Movie, "data")
    private fun series() = TvSeriesLoadResponse("Fixture", "https://fixture.example/show", "Fixture", TvType.TvSeries, emptyList())
    private fun episode(type: TvType = TvType.Movie, season: Int? = null) = ResultEpisode(
        headerName = "Fixture", name = null, poster = null, episode = 3,
        seasonIndex = season, season = season, data = "data", apiName = "Fixture",
        id = -100, index = 0, position = 0, duration = 100000, score = null,
        description = null, isFiller = null, tvType = type, parentId = -100,
        videoWatchState = VideoWatchState.None,
    )

    @Test fun `movie skip requests use v4 and prefer TMDB over IMDB`() {
        withRequests(respond = { request ->
            if (request.url.host == "api.theintrodb.org") """{"type":"movie","intro":[{"start_ms":1000,"end_ms":9000}]}"""
            else unexpected(request)
        }) { calls ->
            val data = movie().apply { addTMDbId("42"); addImdbId("tt1234567") }
            assertEquals(listOf(SkipStamp(SkipType.Intro, 1000, 9000)), TheIntroDBSkip().stamps(data, episode(), 100000))
            val url = calls.single().url
            assertEquals("/v4/media", url.encodedPath)
            assertEquals("42", url.queryParameter("tmdb_id"))
            assertNull(url.queryParameter("imdb_id"))
            assertNull(url.queryParameter("season"))
        }
    }

    @Test fun `series skip requests use v4 season episode and IMDB fallback`() {
        withRequests(respond = { request ->
            if (request.url.host == "api.theintrodb.org") """{"type":"episode"}"""
            else unexpected(request)
        }) { calls ->
            val data = series().apply { addImdbId("tt1234567") }
            assertTrue(TheIntroDBSkip().stamps(data, episode(TvType.TvSeries, 2), 100000)!!.isEmpty())
            val url = calls.single().url
            assertEquals("/v4/media", url.encodedPath)
            assertEquals("tt1234567", url.queryParameter("imdb_id"))
            assertEquals("2", url.queryParameter("season"))
            assertEquals("3", url.queryParameter("episode"))
        }
    }

    @Test fun `missing ID or series season makes no skip service request`() {
        withRequests(respond = { unexpected(it) }) { calls ->
            val api = TheIntroDBSkip()
            assertNull(api.stamps(movie(), episode(), 100000))
            val data = series().apply { addTMDbId("42") }
            assertNull(api.stamps(data, episode(TvType.TvSeries), 100000))
            assertTrue(calls.isEmpty())
        }
    }
}
