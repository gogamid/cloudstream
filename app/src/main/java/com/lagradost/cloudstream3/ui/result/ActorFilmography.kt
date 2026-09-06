package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.ActorFilmographyBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseBottomSheetDialogFragment
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.discover.DiscoverLanguage
import com.lagradost.cloudstream3.ui.discover.DiscoverSort
import com.lagradost.cloudstream3.ui.discover.TmdbRatingFilter
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Arguments and view-scoped work allow dismissal and Activity recreation during a lookup. */
class ActorFilmography : BaseBottomSheetDialogFragment<ActorFilmographyBinding>(
    BaseFragment.BindingCreator.Inflate(ActorFilmographyBinding::inflate)
) {
    companion object {
        private const val TAG = "actor_filmography"
        private const val ACTOR_NAME = "actor_name"
        private const val ACTOR_IMAGE = "actor_image"

        fun show(context: Context, actor: Actor) {
            val manager = (context.getActivity() as? FragmentActivity)?.supportFragmentManager
                ?: return
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return

            ActorFilmography().apply {
                arguments = Bundle().apply {
                    putString(ACTOR_NAME, actor.name)
                    putString(ACTOR_IMAGE, actor.image)
                }
            }.showNow(manager, TAG)
        }
    }

    private enum class FilmographyFilter {
        ALL,
        MOVIES,
        SERIES,
    }

    private var loadJob: Job? = null
    private val repository = ActorFilmographyRepository()
    private var allCredits: List<SearchResponse> = emptyList()
    private var activeFilter = FilmographyFilter.ALL
    private var languageFilter = DiscoverLanguage.ALL
    private var ratingFilter = TmdbRatingFilter.ALL
    private var selectedGenres: Set<String> = emptySet()
    private var yearFilter: Int? = null
    private var sortFilter = DiscoverSort.POPULAR
    private var availableGenres: List<String> = emptyList()
    private var hasLoaded = false

    override fun onStart() {
        super.onStart()
        view?.let { fixLayout(it) }
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun configureFilmographyGrid(context: Context) {
        val results = binding?.filmographyResults ?: return
        val columns = context.getSpanCount()
        results.spanCount = columns
        val manager = results.layoutManager as? GridLayoutManager
        if (manager == null || manager::class != GridLayoutManager::class) {
            if (manager == null || manager.spanCount != columns) {
                results.layoutManager = GridLayoutManager(context, columns)
            }
        } else {
            results.layoutManager = GridLayoutManager(context, columns)
        }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view)
        view.layoutParams?.let {
            it.height = (resources.displayMetrics.heightPixels * 0.85).toInt()
            view.layoutParams = it
        }
        configureFilmographyGrid(view.context)
    }

    override fun onBindingCreated(binding: ActorFilmographyBinding, savedInstanceState: Bundle?) {
        activeFilter = FilmographyFilter.entries.firstOrNull {
            it.name == savedInstanceState?.getString("filmography_type")
        } ?: activeFilter
        languageFilter = DiscoverLanguage.entries.firstOrNull {
            it.name == savedInstanceState?.getString("filmography_language")
        } ?: languageFilter
        ratingFilter = TmdbRatingFilter.entries.firstOrNull {
            it.name == savedInstanceState?.getString("filmography_rating")
        } ?: ratingFilter
        selectedGenres = savedInstanceState?.getStringArray("filmography_genres")?.toSet().orEmpty()
        yearFilter = savedInstanceState?.getInt("filmography_year")?.takeIf { it > 0 }
        sortFilter = DiscoverSort.entries.firstOrNull {
            it.name == savedInstanceState?.getString("filmography_sort")
        } ?: sortFilter

        binding.filmographyActor.text = arguments?.getString(ACTOR_NAME)
        binding.filmographyClose.setOnClickListener { dismiss() }
        binding.filmographyResults.apply {
            configureFilmographyGrid(context)
            setRecycledViewPool(SearchAdapter.sharedPool)
            adapter = SearchAdapter(this) { callback ->
                when (callback.action) {
                    SEARCH_ACTION_LOAD,
                    SEARCH_ACTION_SHOW_METADATA,
                    SEARCH_ACTION_PLAY_FILE -> {
                        QuickSearchFragment.pushSearch(activity, callback.card.name)
                        dismiss()
                    }
                }
            }
        }
        binding.filmographyFilterType.setOnClickListener { showTypeDialog() }
        binding.filmographyFilterLanguage.setOnClickListener { showLanguageDialog() }
        binding.filmographyFilterRating.setOnClickListener { showRatingDialog() }
        binding.filmographyFilterGenres.setOnClickListener { showGenresDialog() }
        binding.filmographyFilterYear.setOnClickListener { showYearDialog() }
        binding.filmographyFilterSort.setOnClickListener { showSortDialog() }
        binding.filmographyFilterReset.setOnClickListener { resetFilters() }
        binding.filmographyRetry.setOnClickListener { loadFilmography() }
        updateChipBar()
        loadFilmography()
    }

    private fun setChip(chip: Chip, filterName: String, value: String) {
        chip.text = getString(R.string.discover_dropdown_value, value)
        chip.contentDescription = "$filterName: $value"
    }

    private fun showTypeDialog() {
        val options = FilmographyFilter.entries.toList()
        val names = options.map {
            when (it) {
                FilmographyFilter.ALL -> getString(R.string.discover_all_types)
                FilmographyFilter.MOVIES -> getString(R.string.discover_movies)
                FilmographyFilter.SERIES -> getString(R.string.discover_series)
            }
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_type)
            .setSingleChoiceItems(names, options.indexOf(activeFilter)) { dialog, which ->
                activeFilter = options[which]
                dialog.dismiss()
                updateChipBar()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val options = DiscoverLanguage.entries.toList()
        val names = options.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_language)
            .setSingleChoiceItems(names, options.indexOf(languageFilter)) { dialog, which ->
                languageFilter = options[which]
                dialog.dismiss()
                updateChipBar()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ratingName(rating: TmdbRatingFilter): String =
        if (rating == TmdbRatingFilter.ALL) getString(R.string.discover_all)
        else "${rating.minimum}+"

    private fun showRatingDialog() {
        val options = TmdbRatingFilter.entries.sortedBy { it.minimum }
        val names = options.map { ratingName(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_rating)
            .setSingleChoiceItems(names, options.indexOf(ratingFilter)) { dialog, which ->
                ratingFilter = options[which]
                dialog.dismiss()
                updateChipBar()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGenresDialog() {
        if (availableGenres.isEmpty()) return
        val options = availableGenres
        val pending = selectedGenres.toMutableSet()
        val checked = options.map { it in pending }.toBooleanArray()
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_genres)
            .setMultiChoiceItems(options.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) pending.add(options[which]) else pending.remove(options[which])
            }
            .setPositiveButton(R.string.discover_apply) { _, _ ->
                selectedGenres = pending
                updateChipBar()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.discover_clear, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            pending.clear()
            for (i in options.indices) dialog.listView.setItemChecked(i, false)
        }
    }

    private fun yearOptions(): List<Int?> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val distinct = allCredits.mapNotNull { it.year }.distinct().sortedDescending()
        return listOf(null) + distinct.ifEmpty { (currentYear downTo 1960).toList().take(40) }
    }

    private fun showYearDialog() {
        val options = yearOptions()
        val names = options.map { it?.toString() ?: getString(R.string.discover_all) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_year)
            .setSingleChoiceItems(names, options.indexOf(yearFilter).takeIf { it >= 0 } ?: 0) { dialog, which ->
                yearFilter = options[which]
                dialog.dismiss()
                updateChipBar()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val options = DiscoverSort.entries.toList()
        val names = options.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_sort)
            .setSingleChoiceItems(names, options.indexOf(sortFilter)) { dialog, which ->
                sortFilter = options[which]
                dialog.dismiss()
                updateChipBar()
                applyFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isDefault(): Boolean =
        activeFilter == FilmographyFilter.ALL &&
            languageFilter == DiscoverLanguage.ALL &&
            ratingFilter == TmdbRatingFilter.ALL &&
            selectedGenres.isEmpty() &&
            yearFilter == null &&
            sortFilter == DiscoverSort.POPULAR

    private fun resetFilters() {
        if (isDefault()) return
        activeFilter = FilmographyFilter.ALL
        languageFilter = DiscoverLanguage.ALL
        ratingFilter = TmdbRatingFilter.ALL
        selectedGenres = emptySet()
        yearFilter = null
        sortFilter = DiscoverSort.POPULAR
        updateChipBar()
        applyFilter()
    }

    private fun updateChipBar() {
        val binding = binding ?: return
        val typeName = when (activeFilter) {
            FilmographyFilter.ALL -> getString(R.string.discover_all_types)
            FilmographyFilter.MOVIES -> getString(R.string.discover_movies)
            FilmographyFilter.SERIES -> getString(R.string.discover_series)
        }
        setChip(binding.filmographyFilterType, getString(R.string.discover_filter_type), typeName)
        setChip(binding.filmographyFilterLanguage, getString(R.string.discover_filter_language), getString(languageFilter.labelRes))
        setChip(binding.filmographyFilterRating, getString(R.string.discover_filter_rating), ratingName(ratingFilter))
        val genreValue = if (selectedGenres.isEmpty()) getString(R.string.discover_filter_genres)
        else getString(R.string.discover_genres_selected, selectedGenres.size)
        setChip(binding.filmographyFilterGenres, getString(R.string.discover_filter_genres), genreValue)
        binding.filmographyFilterGenres.isEnabled = availableGenres.isNotEmpty()
        setChip(binding.filmographyFilterYear, getString(R.string.discover_filter_year), yearFilter?.toString() ?: getString(R.string.discover_all))
        setChip(binding.filmographyFilterSort, getString(R.string.discover_filter_sort), getString(sortFilter.labelRes))
        binding.filmographyFilterReset.isVisible = !isDefault()
    }

    private fun applyFilter() {
        val binding = binding ?: return
        if (!hasLoaded) return
        var filtered = when (activeFilter) {
            FilmographyFilter.ALL -> allCredits
            FilmographyFilter.MOVIES -> allCredits.filter { it.type == TvType.Movie }
            FilmographyFilter.SERIES -> allCredits.filter { it.type == TvType.TvSeries }
        }.filter { ratingFilter.matches(it.score) }
            .filter { languageFilter.code == null || it.originalLanguage == languageFilter.code }
            .filter { yearFilter == null || it.year == yearFilter }
            .filter { selectedGenres.isEmpty() || it.genres?.any { g -> g in selectedGenres } == true }

        filtered = when (sortFilter) {
            DiscoverSort.POPULAR -> filtered
            DiscoverSort.TOP_RATED -> filtered.sortedWith(compareByDescending<SearchResponse> { it.score?.toDouble() ?: -1.0 }.thenBy { it.name })
            DiscoverSort.NEWEST -> filtered.sortedWith(compareByDescending<SearchResponse> { it.year ?: 0 }.thenBy { it.name })
            DiscoverSort.OLDEST -> filtered.sortedWith(compareBy<SearchResponse> { it.year ?: Int.MAX_VALUE }.thenBy { it.name })
            DiscoverSort.TITLE_AZ -> filtered.sortedBy { it.name.lowercase() }
        }

        (binding.filmographyResults.adapter as? SearchAdapter)?.submitList(filtered)
        binding.filmographyResults.isVisible = filtered.isNotEmpty()
        binding.filmographyStatus.setText(
            if (allCredits.isEmpty()) R.string.actor_filmography_empty
            else R.string.actor_filmography_no_matches
        )
        binding.filmographyStatus.isVisible = filtered.isEmpty() && !binding.filmographyLoading.isVisible
        updateChipBar()
    }

    private fun loadFilmography() {
        val binding = binding ?: return
        val actor = Actor(
            name = arguments?.getString(ACTOR_NAME).orEmpty(),
            image = arguments?.getString(ACTOR_IMAGE),
        )
        loadJob?.cancel()
        hasLoaded = false
        allCredits = emptyList()
        availableGenres = emptyList()
        binding.filmographyLoading.isVisible = true
        binding.filmographyStatus.isVisible = false
        binding.filmographyRetry.isVisible = false
        binding.filmographyResults.isVisible = false
        updateChipBar()

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val credits = withContext(Dispatchers.IO) { repository.load(actor) }
                allCredits = credits
                availableGenres = credits.flatMap { it.genres.orEmpty() }.distinct().sorted()
                // Keep only still-valid genre selections
                selectedGenres = selectedGenres.filter { it in availableGenres }.toSet()
                hasLoaded = true
                binding.filmographyLoading.isVisible = false
                applyFilter()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logError(error)
                binding.filmographyStatus.setText(R.string.actor_filmography_error)
                binding.filmographyStatus.isVisible = true
                binding.filmographyRetry.isVisible = true
            } finally {
                if (isActive) binding.filmographyLoading.isVisible = false
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        loadJob?.cancel()
        super.onDismiss(dialog)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("filmography_type", activeFilter.name)
        outState.putString("filmography_language", languageFilter.name)
        outState.putString("filmography_rating", ratingFilter.name)
        outState.putStringArray("filmography_genres", selectedGenres.toTypedArray())
        yearFilter?.let { outState.putInt("filmography_year", it) }
        outState.putString("filmography_sort", sortFilter.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        allCredits = emptyList()
        availableGenres = emptyList()
        hasLoaded = false
        binding?.filmographyResults?.adapter = null
        super.onDestroyView()
    }
}
