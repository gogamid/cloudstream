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
    val rating: TmdbRatingFilter = TmdbRatingFilter.SEVEN,
    val genreIds: Set<Int> = emptySet(),
    val genres: List<TmdbGenre> = emptyList(),
    val year: Int? = null,
    val sort: DiscoverSort = DiscoverSort.POPULAR,
    val results: List<SearchResponse> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
) {
    val isDefault: Boolean
        get() = type == DiscoverMediaType.MOVIES &&
            rating == TmdbRatingFilter.SEVEN &&
            genreIds.isEmpty() &&
            year == null &&
            sort == DiscoverSort.POPULAR
}

internal class DiscoverViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val repository = DiscoverRepository()
    private val mutableState = MutableLiveData(
        DiscoverState(
            type = DiscoverMediaType.entries.firstOrNull {
                it.name == savedState.get<String>("type")
            } ?: DiscoverMediaType.MOVIES,
            rating = TmdbRatingFilter.entries.firstOrNull {
                it.minimum == (savedState.get<Int>("rating") ?: TmdbRatingFilter.SEVEN.minimum)
            } ?: TmdbRatingFilter.SEVEN,
            genreIds = savedState.get<IntArray>("genre_ids")?.filter { it > 0 }?.toSet().orEmpty(),
            year = savedState.get<Int>("year")?.takeIf { it in 1900..2100 },
            sort = DiscoverSort.entries.firstOrNull {
                it.name == savedState.get<String>("sort")
            } ?: DiscoverSort.POPULAR,
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
        savedState.remove<IntArray>("genre_ids")
        // Genre catalogues differ between movies and series.
        mutableState.value = current.copy(type = type, genreIds = emptySet(), genres = emptyList())
        load(reset = true)
    }

    fun setRating(rating: TmdbRatingFilter) {
        val current = mutableState.value ?: return
        if (current.rating == rating) return
        savedState["rating"] = rating.minimum
        mutableState.value = current.copy(rating = rating)
        load(reset = true)
    }

    fun setGenreIds(ids: Set<Int>) {
        val current = mutableState.value ?: return
        val valid = ids.filter { it > 0 }.toSet()
        if (current.genreIds == valid) return
        if (valid.isNotEmpty()) savedState["genre_ids"] = valid.sorted().toIntArray()
        else savedState.remove<IntArray>("genre_ids")
        mutableState.value = current.copy(genreIds = valid)
        load(reset = true)
    }

    fun setYear(year: Int?) {
        val current = mutableState.value ?: return
        require(year == null || year in 1900..2100)
        if (current.year == year) return
        if (year != null) savedState["year"] = year else savedState.remove<Int>("year")
        mutableState.value = current.copy(year = year)
        load(reset = true)
    }

    fun setSort(sort: DiscoverSort) {
        val current = mutableState.value ?: return
        if (current.sort == sort) return
        savedState["sort"] = sort.name
        mutableState.value = current.copy(sort = sort)
        load(reset = true)
    }

    fun resetFilters() {
        val current = mutableState.value ?: return
        if (current.isDefault) return
        savedState["type"] = DiscoverMediaType.MOVIES.name
        savedState["rating"] = TmdbRatingFilter.SEVEN.minimum
        savedState.remove<IntArray>("genre_ids")
        savedState.remove<Int>("year")
        savedState["sort"] = DiscoverSort.POPULAR.name
        mutableState.value = current.copy(
            type = DiscoverMediaType.MOVIES,
            rating = TmdbRatingFilter.SEVEN,
            genreIds = emptySet(),
            genres = if (current.type == DiscoverMediaType.MOVIES) current.genres else emptyList(),
            year = null,
            sort = DiscoverSort.POPULAR,
        )
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
                    genres to repository.discover(
                        snapshot.type, snapshot.rating, snapshot.genreIds,
                        snapshot.year, snapshot.sort, page,
                    )
                }
                // A cancelled request must never overwrite newer filter results.
                if (requestGeneration != generation) return@launch
                mutableState.value = snapshot.copy(
                    genres = genres,
                    // Selections made for the other catalogue never leak across types.
                    genreIds = snapshot.genreIds.filter { id -> genres.any { it.id == id } }.toSet(),
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
