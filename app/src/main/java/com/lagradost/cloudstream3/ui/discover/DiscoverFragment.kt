package com.lagradost.cloudstream3.ui.discover

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDiscoverBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_FOCUSED
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_PLAY_FILE
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_SHOW_METADATA
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import java.util.Calendar

class DiscoverFragment : BaseFragment<FragmentDiscoverBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentDiscoverBinding::inflate)
) {
    private val viewModel: DiscoverViewModel by viewModels()

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(view, padBottom = isLandscape(), padLeft = isLayout(TV or EMULATOR))
        binding?.discoverResults?.apply {
            val columns = context.getSpanCount()
            spanCount = columns
            // Keep D-pad scrolling to off-screen rows, as in actor filmography.
            val manager = layoutManager as? GridLayoutManager
            if (manager == null || manager::class != GridLayoutManager::class) {
                layoutManager = GridLayoutManager(context, columns)
            } else {
                manager.spanCount = columns
            }
        }
    }

    override fun onBindingCreated(binding: FragmentDiscoverBinding) {
        binding.discoverResults.apply {
            setRecycledViewPool(SearchAdapter.sharedPool)
            adapter = SearchAdapter(this) { callback ->
                when (callback.action) {
                    SEARCH_ACTION_FOCUSED -> autoLoadIfNearEnd(callback.position)
                    SEARCH_ACTION_SHOW_METADATA -> DiscoverPreview.show(this@DiscoverFragment, callback.card)
                    SEARCH_ACTION_LOAD,
                    SEARCH_ACTION_PLAY_FILE -> QuickSearchFragment.pushSearch(activity, callback.card.name)
                }
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0) return
                    val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val total = manager.itemCount
                    if (total == 0) return
                    if (manager.findLastVisibleItemPosition() >= total - manager.spanCount * 2) {
                        viewModel.loadMoreOrRetry()
                    }
                }
            })
        }
        binding.filterType.setOnClickListener { showTypeDialog() }
        binding.filterLanguage.setOnClickListener { showLanguageDialog() }
        binding.filterRating.setOnClickListener { showRatingDialog() }
        binding.filterGenres.setOnClickListener { showGenresDialog() }
        binding.filterYear.setOnClickListener { showYearDialog() }
        binding.filterSort.setOnClickListener { showSortDialog() }
        binding.filterReset.setOnClickListener { viewModel.resetFilters() }
        // No load-more/retry button: paging is automatic, errors retry by tapping
        // the status text.
        binding.discoverStatus.setOnClickListener {
            if (viewModel.state.value?.error == true) viewModel.loadMoreOrRetry()
        }
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
    }

    private fun setDropdown(chip: Chip, filterName: String, value: String) {
        chip.text = getString(R.string.discover_dropdown_value, value)
        chip.contentDescription = "$filterName: $value"
    }

    private fun showLanguageDialog() {
        val options = DiscoverLanguage.entries.toList()
        val current = viewModel.state.value?.language ?: DiscoverLanguage.ENGLISH
        val names = options.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_language)
            .setSingleChoiceItems(names, options.indexOf(current)) { dialog, which ->
                viewModel.setLanguage(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTypeDialog() {
        val options = DiscoverMediaType.entries.toList()
        val current = viewModel.state.value?.type ?: DiscoverMediaType.MOVIES
        val names = options.map {
            getString(if (it == DiscoverMediaType.MOVIES) R.string.discover_movies else R.string.discover_series)
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_type)
            .setSingleChoiceItems(names, options.indexOf(current)) { dialog, which ->
                viewModel.setType(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRatingDialog() {
        val options = TmdbRatingFilter.entries.sortedBy { it.minimum }
        val current = viewModel.state.value?.rating ?: TmdbRatingFilter.SEVEN
        val names = options.map { ratingName(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_rating)
            .setSingleChoiceItems(names, options.indexOf(current)) { dialog, which ->
                viewModel.setRating(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ratingName(rating: TmdbRatingFilter): String =
        if (rating == TmdbRatingFilter.ALL) getString(R.string.discover_all)
        else "${rating.minimum}+"

    private fun showGenresDialog() {
        val state = viewModel.state.value ?: return
        if (state.genres.isEmpty()) return
        val options = state.genres
        val pending = state.genreIds.toMutableSet()
        val checked = options.map { it.id in pending }.toBooleanArray()
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_genres)
            .setMultiChoiceItems(
                options.map { it.name }.toTypedArray(), checked
            ) { _, which, isChecked ->
                if (isChecked) pending.add(options[which].id)
                else pending.remove(options[which].id)
            }
            .setPositiveButton(R.string.discover_apply) { _, _ ->
                viewModel.setGenreIds(pending)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.discover_clear, null)
            .create()
        dialog.show()
        // Clearing previews inside the dialog; Cancel still aborts everything.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            pending.clear()
            for (i in options.indices) dialog.listView.setItemChecked(i, false)
        }
    }

    private fun yearRangeLabel(range: YearRange): String {
        if (range.isAll) return getString(R.string.discover_all)
        // 70s/80s/90s/2000s/2010s/2020s shorthand for decade presets
        if (range.from != null && range.to != null && range.to == range.from + 9 && range.from % 10 == 0) {
            return if (range.from >= 2000) "${range.from}s" else "${range.from % 100}s"
        }
        return range.label()
    }

    private fun yearPresets(): List<YearRange> {
        val y = Calendar.getInstance().get(Calendar.YEAR)
        return listOf(
            YearRange(null, null), // All
            YearRange(y, y),
            YearRange(y - 1, y - 1),
            YearRange(2020, 2029), // 2020s
            YearRange(2015, 2019),
            YearRange(2010, 2019),
            YearRange(2000, 2009),
            YearRange(1990, 1999),
            YearRange(1980, 1989),
            YearRange(1970, 1979),
        )
    }

    private fun showYearDialog() {
        val presets = yearPresets()
        val current = YearRange(viewModel.state.value?.yearFrom, viewModel.state.value?.yearTo)
        val presetLabels = presets.map { yearRangeLabel(it) }.toMutableList()
        presetLabels.add(getString(R.string.discover_year_custom))
        val checked = presets.indexOf(current).takeIf { it >= 0 } ?: -1
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_year)
            .setSingleChoiceItems(presetLabels.toTypedArray(), checked) { dialog, which ->
                if (which == presets.size) {
                    dialog.dismiss()
                    showCustomYearRangeDialog()
                } else {
                    val r = presets[which]
                    viewModel.setYearRange(r.from, r.to)
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCustomYearRangeDialog() {
        val years: List<Int?> = listOf(null) + (Calendar.getInstance().get(Calendar.YEAR) downTo 1960).toList()
        val names = years.map { it?.toString() ?: getString(R.string.discover_all) }.toTypedArray()
        var pendingFrom = viewModel.state.value?.yearFrom
        var pendingTo = viewModel.state.value?.yearTo
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_year_from)
            .setSingleChoiceItems(names, years.indexOf(pendingFrom).takeIf { it >= 0 } ?: 0) { dFrom, whichFrom ->
                pendingFrom = years[whichFrom]
                dFrom.dismiss()
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.discover_year_to)
                    .setSingleChoiceItems(names, years.indexOf(pendingTo).takeIf { it >= 0 } ?: 0) { dTo, whichTo ->
                        pendingTo = years[whichTo]
                        dTo.dismiss()
                        var f = pendingFrom
                        var t = pendingTo
                        if (f != null && t != null && f > t) {
                            val tmp = f; f = t; t = tmp
                        }
                        viewModel.setYearRange(f, t)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val options = DiscoverSort.entries.toList()
        val current = viewModel.state.value?.sort ?: DiscoverSort.POPULAR
        val names = options.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discover_filter_sort)
            .setSingleChoiceItems(names, options.indexOf(current)) { dialog, which ->
                viewModel.setSort(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun render(state: DiscoverState) {
        val binding = binding ?: return
        val typeName = getString(
            if (state.type == DiscoverMediaType.MOVIES) R.string.discover_movies else R.string.discover_series
        )
        setDropdown(binding.filterType, getString(R.string.discover_filter_type), typeName)
        setDropdown(binding.filterLanguage, getString(R.string.discover_filter_language), getString(state.language.labelRes))
        setDropdown(binding.filterRating, getString(R.string.discover_filter_rating), ratingName(state.rating))
        val genreValue = if (state.genreIds.isEmpty()) getString(R.string.discover_filter_genres)
        else getString(R.string.discover_genres_selected, state.genreIds.size)
        setDropdown(binding.filterGenres, getString(R.string.discover_filter_genres), genreValue)
        binding.filterGenres.isEnabled = state.genres.isNotEmpty()
        val yearLabel = yearRangeLabel(YearRange(state.yearFrom, state.yearTo))
        setDropdown(binding.filterYear, getString(R.string.discover_filter_year), yearLabel)
        setDropdown(binding.filterSort, getString(R.string.discover_filter_sort), getString(state.sort.labelRes))
        binding.filterReset.isVisible = !state.isDefault

        (binding.discoverResults.adapter as? SearchAdapter)?.submitList(state.results, Runnable {
            val views = this.binding ?: return@Runnable
            if (state.page <= 1) views.discoverResults.scrollToPosition(0)
            // A short list that does not fill the screen cannot scroll, so keep
            // paging until it does. Errors never auto-retry; use the retry button.
            views.discoverResults.post {
                val results = this.binding?.discoverResults ?: return@post
                val current = viewModel.state.value
                if (current != null && current.hasMore && !current.loading && !current.error &&
                    !results.canScrollVertically(1)
                ) {
                    viewModel.loadMoreOrRetry()
                }
            }
        })
        binding.discoverResults.isVisible = state.results.isNotEmpty()
        binding.discoverLoading.isVisible = state.loading
        binding.discoverStatus.isVisible = !state.loading && (state.error || state.results.isEmpty())
        binding.discoverStatus.setText(
            if (state.error) R.string.discover_error else R.string.discover_empty
        )
        binding.discoverStatus.isClickable = state.error
        binding.discoverStatus.isFocusable = state.error
    }

    /** TV D-pad focus can land near the end without scrolling first, so prefetch
    when a focused card is within ~2 rows of the last item. */
    private fun autoLoadIfNearEnd(position: Int) {
        val recycler = binding?.discoverResults ?: return
        val total = recycler.adapter?.itemCount ?: return
        if (total == 0) return
        val span = (recycler.layoutManager as? GridLayoutManager)?.spanCount ?: 1
        if (position >= total - span * 2) viewModel.loadMoreOrRetry()
    }

    override fun onDestroyView() {
        binding?.discoverResults?.adapter = null
        super.onDestroyView()
    }
}
