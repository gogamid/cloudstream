package com.lagradost.cloudstream3.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.DialogDiscoverPreviewBinding
import com.lagradost.cloudstream3.databinding.DiscoverCastItemBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.result.ActorFilmography
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object DiscoverPreview {
    fun show(fragment: Fragment, card: SearchResponse, onChanged: () -> Unit = {}) {
        val context = fragment.context ?: return
        val includeCast = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.show_cast_in_details_key), true)
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        val binding = DialogDiscoverPreviewBinding.inflate(LayoutInflater.from(builder.context))
        if (isLayout(PHONE)) binding.previewOverview.textSize = 15f
        val dialog = builder.setTitle(card.name).setView(binding.root).create()
        val tv = card.type == TvType.TvSeries
        val saved = DiscoverWatchlist.allEntries().firstOrNull { it.url == card.url }
        val metadata = listOfNotNull(
            (card.year ?: saved?.year)?.toString(),
            context.getString(if (tv) R.string.discover_series else R.string.discover_movies),
            card.score?.toDouble()?.let { "★ %.1f".format(it) },
            (card.genres ?: (card as? SyncAPI.LibraryItem)?.tags)?.joinToString(" • "),
        ).joinToString(" · ")
        binding.previewMetadata.text = metadata
        binding.previewPoster.loadImage(card.posterUrl)
        binding.previewOverview.setText(R.string.discover_preview_loading)
        fun label(res: Int, status: DiscoverWatchlist.Status) =
            (if (saved?.status == status) "✓ " else "") + context.getString(res)
        binding.previewWatchlist.text = label(R.string.type_plan_to_watch, DiscoverWatchlist.Status.WATCHLIST)
        fun change(status: DiscoverWatchlist.Status?) {
            DiscoverWatchlist.setStatus(card, status)
            dialog.dismiss()
            onChanged()
        }
        binding.previewWatchlist.setOnClickListener {
            change(if (DiscoverWatchlist.contains(card)) null else DiscoverWatchlist.Status.WATCHLIST)
        }
        binding.previewCompleted.text = label(R.string.type_completed, DiscoverWatchlist.Status.COMPLETED)
        binding.previewCompleted.setOnClickListener {
            change(if (DiscoverWatchlist.status(card) == DiscoverWatchlist.Status.COMPLETED)
                null else DiscoverWatchlist.Status.COMPLETED)
        }
        binding.previewIgnore.text = label(R.string.type_dropped, DiscoverWatchlist.Status.IGNORED)
        binding.previewIgnore.setOnClickListener {
            change(if (DiscoverWatchlist.status(card) == DiscoverWatchlist.Status.IGNORED)
                null else DiscoverWatchlist.Status.IGNORED)
        }
        binding.previewSearch.setOnClickListener {
            dialog.dismiss()
            QuickSearchFragment.pushSearch(fragment.activity, card.name)
        }
        dialog.show()
        binding.previewWatchlist.requestFocus()
        // Only metadata is fetched. No provider loading or playback side effects.
        val job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = card.url.substringAfterLast('/').toIntOrNull()
                    ?.takeIf { it > 0 } ?: error("Invalid TMDB ID")
                val repository = DiscoverPreviewRepository()
                val details = withContext(Dispatchers.IO) {
                    val type = if (tv) DiscoverMediaType.SERIES else DiscoverMediaType.MOVIES
                    repository.load(type, id, includeCast)
                }
                binding.previewOverview.text = details.overview?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.discover_preview_no_overview)
                val extra = if (tv) details.seasons?.let {
                    context.getString(R.string.discover_preview_seasons, it)
                } else details.runtime?.takeIf { it > 0 }?.let {
                    context.getString(R.string.discover_preview_runtime, it)
                }
                binding.previewMetadata.text = listOfNotNull(metadata, extra).joinToString(" · ")
                val cast = if (includeCast) repository.cast(details) else emptyList()
                if (cast.isNotEmpty()) {
                    binding.previewCast.apply {
                        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                        adapter = CastAdapter(cast) { member ->
                            val activity = fragment.activity ?: return@CastAdapter
                            dialog.dismiss()
                            ActorFilmography.show(activity, member.toActor(), member.id)
                        }
                        isVisible = true
                    }
                    binding.previewCastLabel.isVisible = true
                    binding.previewScroll.nextFocusDownId = R.id.preview_cast
                    binding.previewWatchlist.nextFocusUpId = R.id.preview_cast
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                binding.previewOverview.setText(R.string.discover_preview_error)
            }
        }
        dialog.setOnDismissListener { job.cancel() }
    }

    private class CastAdapter(
        private val cast: List<DiscoverPreviewRepository.CastMember>,
        private val select: (DiscoverPreviewRepository.CastMember) -> Unit,
    ) : RecyclerView.Adapter<CastAdapter.Holder>() {
        class Holder(val binding: DiscoverCastItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            DiscoverCastItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount(): Int = cast.size

        override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
            val member = cast[position]
            val actor = member.toActor()
            castPhoto.loadImage(actor.image) {
                placeholder(R.drawable.ic_baseline_people_24)
                fallback(R.drawable.ic_baseline_people_24)
                error(R.drawable.ic_baseline_people_24)
            }
            castName.text = actor.name
            castCharacter.text = member.character?.trim()
            castCharacter.isVisible = !member.character.isNullOrBlank()
            root.setOnClickListener { select(member) }
        }
    }
}
