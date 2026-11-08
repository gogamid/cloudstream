package com.lagradost.cloudstream3.ui.player

import android.os.SystemClock
import android.util.Log
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RepoLinkGenerator(
    episodes: List<ResultEpisode>,
    val page: LoadResponse? = null,
) : VideoGenerator<ResultEpisode>(episodes) {
    companion object {
        const val TAG = "RepoLink"

        // Provider instance identity also separates plugin reloads; the opaque request separates
        // providers whose ID stays the same when their episode locator changes.
        private data class Key(
            val account: Int,
            val provider: MainAPI,
            val episode: Int,
            val data: String,
            val casting: Boolean,
        )

        private val collections = SourceCollectionStore<Key, ExtractorLink, SubtitleData>(
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            SystemClock::elapsedRealtime,
            { it.url },
            { it.url },
        )

        internal fun clearSourceCollections() = collections.clear()
        internal fun discardUnusedSourceCollections() = collections.discardUnused()

        internal suspend fun warm(
            target: SourceWarmupTarget,
            account: Int = DataStoreHelper.selectedAccountNumberFlow.value,
        ) {
            if (account != DataStoreHelper.selectedAccountNumberFlow.value) return
            val lease = collect(target.apiName, target.episodeId, target.data, false, false, true, account)
            try {
                lease.state.first { it.result != null }
            } finally {
                lease.close()
            }
        }

        private fun collect(
            apiName: String,
            episode: Int,
            data: String,
            casting: Boolean,
            refresh: Boolean,
            speculative: Boolean,
            account: Int = DataStoreHelper.selectedAccountNumberFlow.value,
        ): SourceCollectionStore<Key, ExtractorLink, SubtitleData>.Lease {
            val provider = getApiFromNameNull(apiName) ?: error("This provider does not exist")
            val key = Key(account, provider, episode, data, casting)
            return collections.acquire(key, speculative, refresh) { sink ->
                if (account != DataStoreHelper.selectedAccountNumberFlow.value) return@acquire false
                val started = SystemClock.elapsedRealtime()
                Log.i(TAG, "Source extraction: started; speculative=$speculative; episode=$episode")
                val suffixes = ConcurrentHashMap<String, AtomicInteger>()
                val seenSubtitles = ConcurrentHashMap.newKeySet<String>()
                val hasVideo = AtomicBoolean(sink.initial.links.isNotEmpty())
                sink.initial.subtitles.forEach {
                    seenSubtitles.add(it.url)
                    suffixes.getOrPut(it.originalName) { AtomicInteger() }.incrementAndGet()
                }
                val result = APIRepository(provider).loadLinks(
                    data,
                    isCasting = casting,
                    subtitleCallback = { file ->
                        val subtitle = PlayerSubtitleHelper.getSubtitleData(file)
                        if (subtitle.url.isNotBlank() && seenSubtitles.add(subtitle.url)) {
                            val name = subtitle.originalName.html().toString().trim()
                            val suffix = suffixes.getOrPut(name) { AtomicInteger() }.incrementAndGet()
                            sink.subtitle(subtitle.copy(originalName = name, nameSuffix = "$suffix"))
                        }
                    },
                    callback = { link ->
                        if (link.url.isNotBlank()) {
                            hasVideo.set(true)
                            sink.link(link)
                        }
                    },
                )
                Log.i(TAG, "Source extraction: finished after ${SystemClock.elapsedRealtime() - started}ms; success=$result")
                result && hasVideo.get()
            }
        }
    }

    override val hasCache = true
    override val canSkipLoading = true
    override fun getId(index: Int): Int? = videos.getOrNull(index)?.id

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val current = videos.getOrNull(offset) ?: return false
        val lease = collect(current.apiName, current.id, current.data, isCasting, clearCache, false)
        Log.i(TAG, "Source collection: player attached; cached=${lease.state.value.links.size}; " +
            "collecting=${lease.state.value.result == null}")
        val links = HashSet<String>()
        val subtitles = HashSet<String>()
        try {
            // Snapshots contain the entire collection, so StateFlow conflation cannot lose a source.
            val completed = lease.state.first { snapshot ->
                snapshot.links.forEach { link ->
                    if (link.type in sourceTypes && links.add(link.url)) callback(link to null)
                }
                snapshot.subtitles.forEach { subtitle ->
                    if (subtitles.add(subtitle.url)) subtitleCallback(subtitle)
                }
                snapshot.result != null
            }
            return completed.result == true
        } finally {
            // Cancelling next-episode/title interest must not cancel a player using this job.
            lease.close()
        }
    }
}
