package com.lagradost.cloudstream3.ui.discover

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DiscoverState(
    val type: DiscoverMediaType = DiscoverMediaType.MOVIES,
    val rating: TmdbRatingFilter = TmdbRatingFilter.ALL,
    val genreId: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val results: List<SearchResponse> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
)

internal class DiscoverViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val repository = DiscoverRepository()
    private val mutableState = MutableLiveData(
        DiscoverState(
            type = DiscoverMediaType.entries.firstOrNull {
                it.name == savedState.get<String>("type")
            } ?: DiscoverMediaType.MOVIES,
            rating = TmdbRatingFilter.entries.firstOrNull {
                it.name == savedState.get<String>("rating")
            } ?: TmdbRatingFilter.ALL,
            genreId = savedState.get<Int>("genre")?.takeIf { it > 0 },
        )
    )
    val state: LiveData<DiscoverState> = mutableState
    private var loadJob: Job? = null
    private var generation = 0

    init {
        load(reset = true)
    }

    fun setType(type: DiscoverMediaType) {
        val current = mutableState.value ?: return
        if (current.type == type) return
        savedState["type"] = type.name
        savedState.remove<Int>("genre")
        mutableState.value = current.copy(type = type, genreId = null, genres = emptyList())
        load(reset = true)
    }

    fun setRating(rating: TmdbRatingFilter) {
        val current = mutableState.value ?: return
        if (current.rating == rating) return
        savedState["rating"] = rating.name
        mutableState.value = current.copy(rating = rating)
        load(reset = true)
    }

    fun setGenre(id: Int?) {
        val current = mutableState.value ?: return
        if (current.genreId == id) return
        if (id != null && current.genres.none { it.id == id }) return
        savedState["genre"] = id
        mutableState.value = current.copy(genreId = id)
        load(reset = true)
    }

    fun loadMoreOrRetry() {
        val current = mutableState.value ?: return
        if (!current.loading && (current.page == 0 || current.hasMore || current.error)) {
            load(reset = current.page == 0)
        }
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        val requestGeneration = ++generation
        val snapshot = mutableState.value ?: return
        val page = if (reset) 1 else snapshot.page + 1
        val previous = if (reset) emptyList() else snapshot.results
        mutableState.value = snapshot.copy(
            results = previous, loading = true, error = false,
            page = if (reset) 0 else snapshot.page,
            hasMore = !reset && snapshot.hasMore,
        )
        loadJob = viewModelScope.launch {
            try {
                val (genres, result) = withContext(Dispatchers.IO) {
                    val genres = snapshot.genres.ifEmpty { repository.genres(snapshot.type) }
                    genres to repository.discover(snapshot.type, snapshot.rating, snapshot.genreId, page)
                }
                // A cancelled request must never overwrite newer filter results.
                if (requestGeneration != generation) return@launch
                mutableState.value = snapshot.copy(
                    genres = genres,
                    results = (previous + result.results).distinctBy { it.id },
                    page = page, hasMore = result.hasMore, loading = false, error = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logError(error)
                if (requestGeneration == generation) {
                    mutableState.value = mutableState.value?.copy(loading = false, error = true)
                }
            }
        }
    }
}
