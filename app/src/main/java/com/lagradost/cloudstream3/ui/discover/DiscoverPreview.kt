package com.lagradost.cloudstream3.ui.discover

import android.view.LayoutInflater
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.DialogDiscoverPreviewBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object DiscoverPreview {
    @Serializable
    internal data class Details(
        @JsonProperty("overview") @SerialName("overview") val overview: String? = null,
        @JsonProperty("runtime") @SerialName("runtime") val runtime: Int? = null,
        @JsonProperty("number_of_seasons") @SerialName("number_of_seasons") val seasons: Int? = null,
    )

    fun show(fragment: Fragment, card: SearchResponse) {
        val context = fragment.context ?: return
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        val binding = DialogDiscoverPreviewBinding.inflate(LayoutInflater.from(builder.context))
        if (isLayout(PHONE)) binding.previewOverview.textSize = 15f
        val dialog = builder.setTitle(card.name).setView(binding.root).create()
        val tv = card.type == TvType.TvSeries
        val saved = DiscoverWatchlist.entries().firstOrNull { it.url == card.url }
        val metadata = listOfNotNull(
            (card.year ?: saved?.year)?.toString(),
            context.getString(if (tv) R.string.discover_series else R.string.discover_movies),
            card.score?.toDouble()?.let { "★ %.1f".format(it) },
            (card.genres ?: (card as? SyncAPI.LibraryItem)?.tags)?.joinToString(" • "),
        ).joinToString(" · ")
        binding.previewMetadata.text = metadata
        binding.previewPoster.loadImage(card.posterUrl)
        binding.previewOverview.setText(R.string.discover_preview_loading)
        binding.previewWatchlist.setText(if (DiscoverWatchlist.contains(card))
            R.string.discover_watchlist_remove else R.string.discover_watchlist_add)
        binding.previewWatchlist.setOnClickListener {
            DiscoverWatchlist.toggle(card)
            dialog.dismiss()
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
                val details = withContext(Dispatchers.IO) {
                    parseJson<Details>(TmdbMetadata.request(
                        "/${if (tv) "tv" else "movie"}/$id", mapOf("language" to "en-US")))
                }
                binding.previewOverview.text = details.overview?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.discover_preview_no_overview)
                val extra = if (tv) details.seasons?.let {
                    context.getString(R.string.discover_preview_seasons, it)
                } else details.runtime?.takeIf { it > 0 }?.let {
                    context.getString(R.string.discover_preview_runtime, it)
                }
                binding.previewMetadata.text = listOfNotNull(metadata, extra).joinToString(" · ")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                binding.previewOverview.setText(R.string.discover_preview_error)
            }
        }
        dialog.setOnDismissListener { job.cancel() }
    }
}
