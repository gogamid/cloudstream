package com.lagradost.cloudstream3.ui.discover

import android.view.View
import androidx.core.view.children
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
                    SEARCH_ACTION_FOCUSED -> autoLoadIfNearEnd(callback.position)
                    SEARCH_ACTION_LOAD,
                    SEARCH_ACTION_SHOW_METADATA,
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
        // No load-more/retry button: paging is automatic, errors retry by tapping
        // the status text.
        binding.discoverStatus.setOnClickListener {
            if (viewModel.state.value?.error == true) viewModel.loadMoreOrRetry()
        }
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
        renderedGenres = null
        super.onDestroyView()
    }
}
