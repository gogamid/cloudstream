package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.getId
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class SourceWarmupTarget(val apiName: String, val episodeId: Int, val data: String)

/** Load descriptors only; never select a replacement episode or start playback. */
internal suspend fun resolveSourceWarmupTarget(
    card: DataStoreHelper.ResumeWatchingResult,
): SourceWarmupTarget? {
    if (card.isFromDownload || card.id == null || card.parentId == null) return null
    val api = getApiFromNameNull(card.apiName) ?: return null
    val loaded = APIRepository(api).load(card.url)
    // APIRepository wraps provider failures; caller cancellation must still win.
    currentCoroutineContext().ensureActive()
    val response = (loaded as? Resource.Success)?.value ?: return null
    return matchSourceWarmupTarget(card, response)
}

/**
 * Mirrors ResultViewModel2.postEpisodes identity arithmetic, not its preferred dub/season selection.
 * Duplicate IDs are deliberately rejected rather than using postEpisodes' first-wins deduplication:
 * speculative work cannot establish which descriptor a saved ambiguous ID originally referred to.
 */
internal fun matchSourceWarmupTarget(
    card: DataStoreHelper.ResumeWatchingResult,
    response: LoadResponse,
): SourceWarmupTarget? {
    if (card.isFromDownload || response.apiName != card.apiName) return null
    val episodeId = card.id ?: return null
    val parentId = card.parentId ?: return null
    val mainId = response.getId()
    if (mainId != parentId) return null

    val matches = mutableListOf<String>()
    when (response) {
        is MovieLoadResponse -> if (episodeId == mainId) matches.add(response.dataUrl)
        is AnimeLoadResponse -> response.episodes.forEach { (dub, episodes) ->
            episodes.forEachIndexed { index, episode ->
                val number = episode.episode ?: (index + 1)
                val id = mainId + number + dub.id * 1_000_000 +
                    (episode.season?.times(10_000) ?: 0)
                if (id == episodeId) matches.add(episode.data)
            }
        }
        is TvSeriesLoadResponse -> response.episodes.sortedBy {
            (it.season?.times(10_000) ?: 0) + (it.episode ?: 0)
        }.forEachIndexed { index, episode ->
            val number = episode.episode ?: (index + 1)
            val id = mainId + (episode.season?.times(100_000) ?: 0) + number + 1
            if (id == episodeId) matches.add(episode.data)
        }
        // Live streams, torrents and search-only responses are not speculative episode targets.
        else -> return null
    }

    val data = matches.singleOrNull() ?: return null
    if (data.isBlank() || APIRepository.isInvalidData(data)) return null
    return SourceWarmupTarget(card.apiName, episodeId, data)
}
