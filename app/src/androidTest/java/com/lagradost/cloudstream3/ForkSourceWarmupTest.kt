@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package com.lagradost.cloudstream3

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.player.LOADTYPE_INAPP
import com.lagradost.cloudstream3.ui.player.PlayerGeneratorViewModel
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SourceWarmup
import com.lagradost.cloudstream3.ui.player.SourceWarmupTarget
import com.lagradost.cloudstream3.ui.result.buildResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real repository/generator/viewmodel; only the external provider is a deterministic fixture. */
@RunWith(AndroidJUnit4::class)
class ForkSourceWarmupTest {
    private class Provider(private val delayed: Boolean) : MainAPI() {
        override var name = "Fork warmup fixture"
        val starts = AtomicInteger()
        val first = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        override suspend fun loadLinks(
            data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit,
        ): Boolean {
            starts.incrementAndGet()
            fun link(index: Int) = ExtractorLink(name, "Fixture $index",
                "https://fixture.invalid/$data/$isCasting/$index.mp4", "https://fixture.invalid/",
                if (index == 1) 720 else 2160, type = ExtractorLinkType.VIDEO,
                headers = mapOf("X-Fixture" to data))
            callback(link(1))
            first.complete(Unit)
            if (delayed) finish.await()
            callback(link(2))
            subtitleCallback(SubtitleFile("English", "https://fixture.invalid/$data/en.srt"))
            return true
        }
    }

    private fun episode(provider: Provider, id: Int = 991, data: String = "request") =
        buildResultEpisode("Fixture", episode = id, data = data, apiName = provider.name,
            id = id, index = 0, tvType = TvType.TvSeries, parentId = 990)

    private fun main(block: () -> Unit) {
        val error = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try { block() } catch (t: Throwable) { error.set(t) }
        }
        error.get()?.let { throw it }
    }

    private fun awaitState(message: String, condition: () -> Boolean) = runBlocking {
        withTimeout(15_000) {
            while (true) {
                var done = false
                main { done = condition() }
                if (done) break
                kotlinx.coroutines.delay(25)
            }
        }
        assertTrue(message, condition())
    }

    @Test fun playerAdoptsRunningWarmupWithoutLosingLateSourcesOrSubtitles() {
        val provider = Provider(delayed = true)
        APIHolder.addPluginMapping(provider)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val models = ViewModelStore()
        val model = PlayerGeneratorViewModel()
        try {
            val generator = RepoLinkGenerator(listOf(episode(provider)))
            val warm = scope.launch { RepoLinkGenerator.warm(SourceWarmupTarget(provider.name, 991, "request")) }
            runBlocking { withTimeout(15_000) { provider.first.await() } }
            main {
                models.put("warmup", model)
                model.attachGenerator(generator, 0)
                model.loadLinks()
            }
            awaitState("Player did not replay partial warmup") { model.state.links.size == 1 }
            warm.cancel()
            runBlocking { warm.join() }
            // Same handler used by plugin notifications: eviction must not fake completion.
            SourceWarmup.invalidate()
            assertEquals(1, provider.starts.get())
            main { assertTrue(model.state.loading is Resource.Loading) }
            provider.finish.complete(Unit)
            awaitState("Late source/subtitle lost after speculative consumer detached") {
                model.state.loading is Resource.Success && model.state.links.size == 2 && model.state.subtitles.size == 1
            }
            assertEquals(1, provider.starts.get())
            assertEquals(setOf(720, 2160), model.state.links.mapNotNull { it.first?.quality }.toSet())
            assertTrue(model.state.links.all { it.first?.headers?.get("X-Fixture") == "request" })
        } finally {
            main { models.clear() }
            scope.cancel()
            RepoLinkGenerator.clearSourceCollections()
            APIHolder.removePluginMapping(provider)
        }
    }

    @Test fun completedWarmupReusesDescriptorsButReloadRequestAndCastingStayIsolated() = runBlocking {
        val provider = Provider(delayed = false)
        APIHolder.addPluginMapping(provider)
        try {
            RepoLinkGenerator.warm(SourceWarmupTarget(provider.name, 991, "request"))
            val generator = RepoLinkGenerator(listOf(episode(provider)))
            suspend fun load(gen: RepoLinkGenerator = generator, refresh: Boolean = false, cast: Boolean = false) {
                val urls = mutableSetOf<String>()
                assertTrue(gen.generateLinks(refresh, LOADTYPE_INAPP, { it.first?.url?.let(urls::add) }, {}, 0, cast))
                assertEquals(2, urls.size)
            }
            load()
            assertEquals(1, provider.starts.get())
            load(refresh = true)
            assertEquals(2, provider.starts.get())
            load(RepoLinkGenerator(listOf(episode(provider, data = "different-request"))))
            assertEquals(3, provider.starts.get())
            load(cast = true)
            assertEquals(4, provider.starts.get())
        } finally {
            RepoLinkGenerator.clearSourceCollections()
            APIHolder.removePluginMapping(provider)
        }
    }

    @Test fun nextEpisodePlayJoinsItsStillRunningPreload() {
        val provider = Provider(delayed = true)
        APIHolder.addPluginMapping(provider)
        val models = ViewModelStore()
        val model = PlayerGeneratorViewModel()
        try {
            main {
                models.put("next", model)
                model.attachGenerator(RepoLinkGenerator(listOf(episode(provider, 990), episode(provider))), 0)
                model.preLoadNextLinks()
            }
            runBlocking { withTimeout(15_000) { provider.first.await() } }
            main { model.loadThisEpisode(1) }
            awaitState("Next episode did not adopt partial extraction") { model.state.links.size == 1 }
            provider.finish.complete(Unit)
            awaitState("Next episode did not finish") {
                model.state.loading is Resource.Success && model.state.links.size == 2
            }
            assertEquals(1, provider.starts.get())
        } finally {
            main { models.clear() }
            RepoLinkGenerator.clearSourceCollections()
            APIHolder.removePluginMapping(provider)
        }
    }
}
