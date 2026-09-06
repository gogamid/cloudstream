package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.ActorFilmographyBinding
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BaseBottomSheetDialogFragment
import com.lagradost.cloudstream3.ui.BaseFragment
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    private var ratingFilter = TmdbRatingFilter.ALL
    private var hasLoaded = false

    override fun onStart() {
        super.onStart()
        // Dialog views gain layout parameters only after being attached to their window.
        view?.let { fixLayout(it) }
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun configureFilmographyGrid(context: Context) {
        val results = binding?.filmographyResults ?: return
        val columns = context.getSpanCount()

        // AutofitRecyclerView uses its internal span count to size SearchAdapter cards.
        results.spanCount = columns

        // Its custom GrdLayoutManager only returns already-attached views during D-pad focus
        // searches. On TV this traps focus on the initially visible rows (often ~12 cards).
        // A regular GridLayoutManager can lay out and scroll to off-screen rows as focus moves.
        val manager = results.layoutManager as? GridLayoutManager
        if (manager == null || manager::class == GridLayoutManager::class) {
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
        ratingFilter = TmdbRatingFilter.entries.firstOrNull {
            it.name == savedInstanceState?.getString("filmography_rating")
        } ?: ratingFilter
        binding.filmographyFilters.check(
            when (activeFilter) {
                FilmographyFilter.ALL -> R.id.filmography_filter_all
                FilmographyFilter.MOVIES -> R.id.filmography_filter_movies
                FilmographyFilter.SERIES -> R.id.filmography_filter_series
            }
        )
        binding.filmographyRatings.check(
            when (ratingFilter) {
                TmdbRatingFilter.ALL -> R.id.filmography_rating_all
                TmdbRatingFilter.SIX -> R.id.filmography_rating_six
                TmdbRatingFilter.SEVEN -> R.id.filmography_rating_seven
            }
        )
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
                        // Never send a TMDB URL to a streaming provider's load/play handlers.
                        QuickSearchFragment.pushSearch(activity, callback.card.name)
                        dismiss()
                    }
                }
            }
        }
        binding.filmographyFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            activeFilter = when (checkedIds.firstOrNull()) {
                R.id.filmography_filter_movies -> FilmographyFilter.MOVIES
                R.id.filmography_filter_series -> FilmographyFilter.SERIES
                else -> FilmographyFilter.ALL
            }
            applyFilter()
        }
        binding.filmographyRatings.setOnCheckedStateChangeListener { _, checkedIds ->
            ratingFilter = when (checkedIds.firstOrNull()) {
                R.id.filmography_rating_six -> TmdbRatingFilter.SIX
                R.id.filmography_rating_seven -> TmdbRatingFilter.SEVEN
                else -> TmdbRatingFilter.ALL
            }
            applyFilter()
        }
        binding.filmographyRetry.setOnClickListener { loadFilmography() }
        loadFilmography()
    }

    private fun applyFilter() {
        val binding = binding ?: return
        // Keep network errors and loading states intact when chips change.
        if (!hasLoaded) return
        val filtered = when (activeFilter) {
            FilmographyFilter.ALL -> allCredits
            FilmographyFilter.MOVIES -> allCredits.filter { it.type == TvType.Movie }
            FilmographyFilter.SERIES -> allCredits.filter { it.type == TvType.TvSeries }
        }.filter { ratingFilter.matches(it.score) }

        (binding.filmographyResults.adapter as? SearchAdapter)?.submitList(filtered)
        binding.filmographyResults.isVisible = filtered.isNotEmpty()
        binding.filmographyStatus.setText(
            if (allCredits.isEmpty()) R.string.actor_filmography_empty
            else R.string.actor_filmography_no_matches
        )
        binding.filmographyStatus.isVisible = filtered.isEmpty() && !binding.filmographyLoading.isVisible
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
        binding.filmographyLoading.isVisible = true
        binding.filmographyStatus.isVisible = false
        binding.filmographyRetry.isVisible = false
        binding.filmographyResults.isVisible = false

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                allCredits = withContext(Dispatchers.IO) { repository.load(actor) }
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
        outState.putString("filmography_rating", ratingFilter.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        allCredits = emptyList()
        hasLoaded = false
        binding?.filmographyResults?.adapter = null
        super.onDestroyView()
    }
}
