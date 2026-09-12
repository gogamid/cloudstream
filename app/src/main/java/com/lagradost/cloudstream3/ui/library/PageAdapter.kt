package com.lagradost.cloudstream3.ui.library

import android.view.KeyEvent
import android.view.LayoutInflater
import com.google.android.material.tabs.TabLayout
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.search.SearchClickCallback
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import kotlin.math.roundToInt

class PageAdapter(
    private val resView: AutofitRecyclerView,
    val clickCallback: (SearchClickCallback) -> Unit
) :
    NoStateAdapter<SyncAPI.LibraryItem>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
        if (a.id != null || b.id != null) {
            a.id == b.id
        } else {
            a.name == b.name && a.url == b.url
        }
    })) {
    private val coverHeight: Int get() = (resView.itemWidth / 0.68).roundToInt()

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            SearchResultGridExpandedBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> {
                clearImage(binding.imageView)
            }
        }
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: SyncAPI.LibraryItem,
        position: Int
    ) {
        val binding = holder.view as? SearchResultGridExpandedBinding ?: return
        binding.imageView.alpha = 1f
        binding.imageView.clearColorFilter()

        /** https://stackoverflow.com/questions/8817522/how-to-get-color-code-of-image-view */
        SearchResultBuilder.bind(
            this@PageAdapter.clickCallback,
            item,
            position,
            holder.itemView,
        )

        // See searchAdaptor for this, it basically fixes the height
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            coverHeight
        )
        if (params.height != binding.imageView.layoutParams.height || params.width != binding.imageView.layoutParams.width) {
            binding.imageView.layoutParams = params
        }

        val showProgress = item.episodesCompleted?.let{ it>0 } ?: false && item.episodesTotal != null
        binding.watchProgress.isVisible = showProgress
        if (showProgress) {
            binding.watchProgress.max = item.episodesTotal
            binding.watchProgress.progress = item.episodesCompleted
        }

        if (isLayout(TV or EMULATOR)) {
            holder.itemView.setOnKeyListener { view, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_UP && position < resView.spanCount) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val tabs = view.rootView.findViewById<TabLayout>(R.id.library_tab_layout)
                        tabs?.getTabAt(tabs.selectedTabPosition)?.view?.requestFocus()
                    }
                    true
                } else false
            }
        }
        binding.imageText.text = item.name
    }
}