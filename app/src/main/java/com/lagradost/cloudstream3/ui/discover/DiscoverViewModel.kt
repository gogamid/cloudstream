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
    val language: DiscoverLanguage = DiscoverLanguage.ENGLISH,
    val rating: TmdbRatingFilter = TmdbRatingFilter.SEVEN,
    val genreIds: Set<Int> = emptySet(),
    val genres: List<TmdbGenre> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val sort: DiscoverSort = DiscoverSort.POPULAR,
    val results: List<SearchResponse> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
    val defaults: FilterDefaults = FilterDefaults(),
) {
    val isDefault: Boolean
        get() = type == defaults.typeValue() &&
            language == defaults.languageValue() && rating == defaults.ratingValue() &&
            genreIds == defaults.genreIds && yearFrom == defaults.yearFrom &&
            yearTo == defaults.yearTo && sort == defaults.sortValue()
}

internal class DiscoverViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val repository = DiscoverRepository()
    private var configuredDefaults = FilterDefaults.load(false).also { defaults ->
        if (savedState.keys().none { it in setOf("type", "language", "rating", "genre_ids", "yearFrom", "yearTo", "sort") }) {
            saveFilters(defaults)
        }
    }

    private fun saveFilters(defaults: FilterDefaults) {
        savedState["type"] = defaults.type
        savedState["language"] = defaults.language
        savedState["rating"] = defaults.rating
        savedState["genre_ids"] = defaults.genreIds.toIntArray()
        savedState["yearFrom"] = defaults.yearFrom
        savedState["yearTo"] = defaults.yearTo
        savedState["sort"] = defaults.sort
        savedState.remove<Int>("year")
    }
    private val mutableState = MutableLiveData(
        DiscoverState(
            defaults = configuredDefaults,
            type = DiscoverMediaType.entries.firstOrNull {
                it.name == savedState.get<String>("type")
            } ?: DiscoverMediaType.MOVIES,
            language = DiscoverLanguage.entries.firstOrNull {
                it.name == savedState.get<String>("language")
            } ?: DiscoverLanguage.ENGLISH,
            rating = TmdbRatingFilter.entries.firstOrNull {
                it.minimum == (savedState.get<Int>("rating") ?: TmdbRatingFilter.SEVEN.minimum)
            } ?: TmdbRatingFilter.SEVEN,
            genreIds = savedState.get<IntArray>("genre_ids")?.filter { it > 0 }?.toSet().orEmpty(),
            yearFrom = savedState.get<Int>("yearFrom")?.takeIf { it in 1900..2100 },
            yearTo = savedState.get<Int>("yearTo")?.takeIf { it in 1900..2100 },
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

    fun setLanguage(language: DiscoverLanguage) {
        val current = mutableState.value ?: return
        if (current.language == language) return
        savedState["language"] = language.name
        mutableState.value = current.copy(language = language)
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

    fun setYearRange(from: Int?, to: Int?) {
        val current = mutableState.value ?: return
        require(from == null || from in 1900..2100)
        require(to == null || to in 1900..2100)
        require(from == null || to == null || from <= to)
        if (current.yearFrom == from && current.yearTo == to) return
        if (from != null) savedState["yearFrom"] = from else savedState.remove<Int>("yearFrom")
        if (to != null) savedState["yearTo"] = to else savedState.remove<Int>("yearTo")
        // Legacy single-year key cleanup
        savedState.remove<Int>("year")
        mutableState.value = current.copy(yearFrom = from, yearTo = to)
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
        val defaults = FilterDefaults.load(false)
        configuredDefaults = defaults
        saveFilters(defaults)
        mutableState.value = current.copy(
            defaults = defaults,
            type = defaults.typeValue(), language = defaults.languageValue(),
            rating = defaults.ratingValue(), genreIds = defaults.genreIds,
            genres = if (current.type == defaults.typeValue()) current.genres else emptyList(),
            yearFrom = defaults.yearFrom, yearTo = defaults.yearTo, sort = defaults.sortValue(),
        )
        load(reset = true)
    }

    fun refreshDefaults() {
        if (configuredDefaults != FilterDefaults.load(false)) resetFilters()
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
                        snapshot.type, snapshot.language, snapshot.rating, snapshot.genreIds,
                        snapshot.yearFrom, snapshot.yearTo, snapshot.sort, page,
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
