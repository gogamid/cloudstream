package com.lagradost.cloudstream3.ui.discover

import android.view.View
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.FragmentDiscoverBinding
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
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

class DiscoverFragment : BaseFragment<FragmentDiscoverBinding>(
    BaseFragment.BindingCreator.Inflate(FragmentDiscoverBinding::inflate)
) {
    private val viewModel: DiscoverViewModel by viewModels()
    private var renderingFilters = false
    private var renderedGenres: List<TmdbGenre>? = null

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
                    SEARCH_ACTION_LOAD,
                    SEARCH_ACTION_SHOW_METADATA,
                    SEARCH_ACTION_PLAY_FILE -> QuickSearchFragment.pushSearch(activity, callback.card.name)
                }
            }
        }
        binding.discoverMediaTypes.setOnCheckedStateChangeListener { _, ids ->
            if (!renderingFilters) {
                viewModel.setType(
                    if (R.id.discover_series in ids) DiscoverMediaType.SERIES else DiscoverMediaType.MOVIES
                )
            }
        }
        binding.discoverRatings.setOnCheckedStateChangeListener { _, ids ->
            if (!renderingFilters) {
                viewModel.setRating(
                    when (ids.firstOrNull()) {
                        R.id.discover_rating_six -> TmdbRatingFilter.SIX
                        R.id.discover_rating_seven -> TmdbRatingFilter.SEVEN
                        else -> TmdbRatingFilter.ALL
                    }
                )
            }
        }
        binding.discoverGenres.setOnCheckedStateChangeListener { group, ids ->
            if (!renderingFilters) {
                val selected = ids.firstOrNull()?.let { group.findViewById<Chip>(it) }
                viewModel.setGenre(selected?.tag as? Int)
            }
        }
        binding.discoverMore.setOnClickListener { viewModel.loadMoreOrRetry() }
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: DiscoverState) {
        val binding = binding ?: return
        renderingFilters = true
        try {
            binding.discoverMediaTypes.check(
                if (state.type == DiscoverMediaType.MOVIES) R.id.discover_movies else R.id.discover_series
            )
            binding.discoverRatings.check(
                when (state.rating) {
                    TmdbRatingFilter.ALL -> R.id.discover_rating_all
                    TmdbRatingFilter.SIX -> R.id.discover_rating_six
                    TmdbRatingFilter.SEVEN -> R.id.discover_rating_seven
                }
            )
            if (renderedGenres != state.genres) {
                binding.discoverGenres.removeAllViews()
                val options = listOf(TmdbGenre(0, getString(R.string.discover_all_genres))) + state.genres
                for (genre in options) {
                    val chip = layoutInflater.inflate(
                        R.layout.discover_genre_chip, binding.discoverGenres, false
                    ) as Chip
                    chip.id = if (genre.id == 0) R.id.discover_genre_all else View.generateViewId()
                    chip.text = genre.name
                    chip.tag = genre.id.takeIf { it > 0 }
                    chip.nextFocusDownId = R.id.discover_results
                    chip.nextFocusUpId = binding.discoverRatings.checkedChipId
                    binding.discoverGenres.addView(chip)
                }
                renderedGenres = state.genres
            }
            binding.discoverGenres.children.filterIsInstance<Chip>()
                .firstOrNull { it.tag == state.genreId }?.let { binding.discoverGenres.check(it.id) }
            binding.discoverGenres.children.forEach {
                it.nextFocusUpId = binding.discoverRatings.checkedChipId
            }
        } finally {
            renderingFilters = false
        }
        (binding.discoverResults.adapter as? SearchAdapter)?.submitList(state.results)
        binding.discoverResults.isVisible = state.results.isNotEmpty()
        binding.discoverLoading.isVisible = state.loading
        binding.discoverStatus.isVisible = !state.loading && (state.error || state.results.isEmpty())
        binding.discoverStatus.setText(
            if (state.error) R.string.discover_error else R.string.discover_empty
        )
        binding.discoverMore.isVisible = state.hasMore || state.error
        binding.discoverMore.isEnabled = !state.loading
        binding.discoverMore.setText(
            if (state.error) R.string.actor_filmography_retry else R.string.discover_load_more
        )
    }

    override fun onDestroyView() {
        binding?.discoverResults?.adapter = null
        renderedGenres = null
        super.onDestroyView()
    }
}
