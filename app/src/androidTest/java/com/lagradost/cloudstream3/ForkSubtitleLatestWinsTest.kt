package com.lagradost.cloudstream3

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.ui.player.ExternalSubtitleController
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import com.lagradost.cloudstream3.ui.player.SubtitleRequestFactory
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real controller + Media3 DataSource, with HTTP calls blocked until explicitly canceled. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ForkSubtitleLatestWinsTest {
    private fun main(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try { block() } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
    }

    private fun await(latch: CountDownLatch) =
        assertTrue("Subtitle work did not finish without a network timeout", latch.await(5, TimeUnit.SECONDS))

    private fun track(name: String) = SubtitleData(
        name, "", "https://subtitle.test/$name", SubtitleOrigin.URL,
        MimeTypes.APPLICATION_SUBRIP, mapOf("X-Subtitle" to name), "en",
    )

    // Delegate tag storage (including computed tags) to the current OkHttp API.
    // execute/enqueue remain deterministic below; the delegate never sends a request.
    private class FakeCall(private val req: Request, private val blocked: Boolean) :
        Call by OkHttpClient().newCall(req) {
        val entered = CountDownLatch(1)
        private val cancelledSignal = CountDownLatch(1)
        private var callback: Callback? = null
        @Volatile private var cancelled = false
        @Volatile private var executed = false

        private fun response(): Response = Response.Builder().request(req)
            .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("1\n00:00:01,000 --> 00:00:03,000\n${req.url.encodedPath}\n".toResponseBody())
            .build()

        override fun request() = req
        override fun timeout() = Timeout.NONE
        override fun isExecuted() = executed
        override fun isCanceled() = cancelled
        override fun clone(): Call = FakeCall(req, blocked)
        override fun execute(): Response {
            executed = true
            entered.countDown()
            if (blocked) {
                if (!cancelledSignal.await(20, TimeUnit.SECONDS)) throw IOException("Fixture timeout")
                throw IOException("Canceled")
            }
            if (cancelled) throw IOException("Canceled")
            return response()
        }

        @Synchronized override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
            entered.countDown()
            if (cancelled) responseCallback.onFailure(this, IOException("Canceled"))
            else if (!blocked) responseCallback.onResponse(this, response())
        }

        @Synchronized override fun cancel() {
            if (cancelled) return
            cancelled = true
            cancelledSignal.countDown()
            if (blocked) callback?.onFailure(this, IOException("Canceled"))
            callback = null
        }
    }

    @Test fun blockedOldSelectionsAreCanceledNewestLoadsAndCurrentFailureKeepsFallback() {
        val calls = ConcurrentHashMap<String, FakeCall>()
        val created = ConcurrentHashMap<String, CountDownLatch>()
        listOf("A", "B", "C").forEach { created[it] = CountDownLatch(1) }
        val client = object : Call.Factory {
            override fun newCall(request: Request): Call {
                val name = request.url.encodedPath.removePrefix("/")
                return FakeCall(request, blocked = name != "C").also {
                    calls[name] = it
                    created[name]?.countDown()
                }
            }
        }
        fun factory(name: String) = SubtitleRequestFactory(client, track(name).headers, "subtitle-test")
        val loaded = CountDownLatch(1)
        val errors = mutableListOf<Pair<String, Boolean>>()
        val renders = mutableListOf<String>()
        lateinit var controller: ExternalSubtitleController
        main {
            controller = ExternalSubtitleController(
                positionMs = { 1500L },
                render = { cues -> renders.add(cues.joinToString { it.text.toString() }) },
                failed = { subtitle, _, notify -> errors.add(subtitle.originalName to notify) },
                loaded = { loaded.countDown() },
            )
        }
        try {
            for (name in listOf("A", "B")) {
                main { assertTrue(controller.select(track(name), factory(name), retry = true)) }
                await(created.getValue(name))
                await(calls.getValue(name).entered)
            }
            main { assertTrue(controller.select(track("C"), factory("C"), retry = true)) }
            await(loaded)
            main {
                assertTrue(calls.getValue("A").isCanceled())
                assertTrue(calls.getValue("B").isCanceled())
                assertEquals(track("C"), controller.currentTrack)
                assertTrue(renders.any { it.contains("/C") })
                assertTrue(errors.isEmpty())
                assertFalse(controller.hasFailed(track("A").getId()))
                assertFalse(controller.hasFailed(track("B").getId()))
                assertEquals("C", calls.getValue("C").request().header("X-Subtitle"))
                assertEquals("subtitle.test", calls.getValue("C").request().url.host)
            }

            // A real current error must still report once, preserving the loaded track.
            for (explicit in listOf(true, false)) {
                val failed = CountDownLatch(1)
                val badClient = object : Call.Factory {
                    override fun newCall(request: Request): Call {
                        failed.countDown()
                        throw IOException("Current selection failed")
                    }
                }
                val name = if (explicit) "broken-manual" else "broken-auto"
                main {
                    assertTrue(controller.select(track(name),
                        SubtitleRequestFactory(badClient, emptyMap(), "subtitle-test"), retry = explicit))
                }
                await(failed)
                // Drain main after the worker has returned; no arbitrary network sleeps.
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (true) {
                    var done = false
                    main { done = errors.any { it.first == name } }
                    if (done) break
                    assertTrue("Current failure was lost", System.nanoTime() < end)
                    Thread.yield()
                }
                main {
                    assertEquals(name to explicit, errors.last())
                    assertEquals(track("C"), controller.currentTrack)
                    assertTrue(controller.hasFailed(track(name).getId()))
                    controller.redraw(force = true)
                    assertTrue(renders.last().contains("/C"))
                }
            }
            main { assertEquals(2, errors.size) }
        } finally {
            main { controller.stop(clearCache = true) }
            calls.values.forEach { it.cancel() }
        }
    }
}
