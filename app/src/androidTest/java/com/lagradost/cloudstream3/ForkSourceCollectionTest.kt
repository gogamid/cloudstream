package com.lagradost.cloudstream3

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.ui.player.PlayerGeneratorViewModel
import com.lagradost.cloudstream3.ui.player.SourcePlaybackPlan
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.VideoGenerator
import com.lagradost.cloudstream3.ui.player.VideoLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Delayed provider contract: metadata collection only, not three buffered videos. */
@RunWith(AndroidJUnit4::class)
class ForkSourceCollectionTest {
    private fun main(block: () -> Unit) {
        val error = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try { block() } catch (t: Throwable) { error.set(t) }
        }
        error.get()?.let { throw it }
    }

    private class DelayedGenerator : VideoGenerator<Unit>(listOf(Unit)) {
        override val hasCache = false
        override val canSkipLoading = true
        override fun getId(index: Int) = 1
        val finishCollection = CompletableDeferred<Unit>()
        override suspend fun generateLinks(
            clearCache: Boolean, sourceTypes: Set<ExtractorLinkType>,
            callback: (VideoLink) -> Unit, subtitleCallback: (SubtitleData) -> Unit,
            offset: Int, isCasting: Boolean
        ): Boolean {
            fun link(index: Int): VideoLink = null to ExtractorUri(Uri.parse("file:///source-$index.mp4"), "Source $index")
            callback(link(1))
            finishCollection.await()
            callback(link(2))
            callback(link(3))
            return true
        }
    }

    private fun collection(skip: Boolean) {
        val store = ViewModelStore()
        val model = PlayerGeneratorViewModel()
        val generator = DelayedGenerator()
        val plan = SourcePlaybackPlan<VideoLink>()
        fun candidates() = model.state.links.map { SourcePlaybackPlan.Candidate(it, null, priority = 100) }
        fun await(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < end) {
                var done = false
                main { done = condition() }
                if (done) return
                Thread.sleep(25)
            }
            fail(message)
        }
        try {
            main {
                store.put("collection-fixture", model)
                model.attachGenerator(generator, 0)
                model.loadLinks()
            }
            await("First source never arrived") { model.state.links.size == 1 }
            main {
                assertTrue(model.state.loading is Resource.Loading)
                assertFalse(plan.ready(candidates(), collecting = true, canSkip = true))
                if (skip) {
                    assertTrue(plan.requestSkip(canSkip = true))
                    assertTrue(plan.ready(candidates(), collecting = true, canSkip = true))
                    // Readiness/Skip must not fake completion or cancel the provider.
                    assertTrue(model.state.loading is Resource.Loading)
                }
                generator.finishCollection.complete(Unit)
            }
            await("Provider never completed all sources") { model.state.loading is Resource.Success }
            main {
                assertEquals(3, model.state.links.size)
                assertTrue(plan.ready(candidates(), collecting = false, canSkip = true))
                assertEquals(skip, plan.skipRequested)
            }
        } finally {
            main { store.clear() }
        }
    }

    @Test fun noSkipWaitsForCompleteCollectionDespiteHighPriorityFirstSource() = collection(skip = false)
    @Test fun explicitSkipLeavesCollectionRunningAndLateSourcesAvailable() = collection(skip = true)
}
