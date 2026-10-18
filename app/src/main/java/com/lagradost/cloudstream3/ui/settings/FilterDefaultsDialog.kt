package com.lagradost.cloudstream3.ui.settings

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.discover.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object FilterDefaultsDialog {
    fun show(fragment: Fragment, actor: Boolean) {
        val ctx = fragment.context ?: return
        val value = FilterDefaults.load(actor)
        val title = if (actor) R.string.actor_filter_defaults else R.string.discover_filter_defaults
        fun save(next: FilterDefaults) {
            FilterDefaults.save(actor, next)
            if (fragment.isAdded) show(fragment, actor)
        }
        fun choose(titleRes: Int, labels: List<String>, selected: Int, chosen: (Int) -> Unit) {
            AlertDialog.Builder(ctx, R.style.AlertDialogCustom).setTitle(titleRes)
                .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                    dialog.dismiss(); chosen(which)
                }.show()
        }
        val typeLabel = ctx.getString(when (value.type) {
            "ALL" -> R.string.discover_all_types
            "SERIES" -> R.string.discover_series
            else -> R.string.discover_movies
        })
        fun ratingLabel(rating: TmdbRatingFilter) = if (rating == TmdbRatingFilter.ALL)
            ctx.getString(R.string.filter_defaults_any) else "${rating.minimum}+"
        val rows = listOf(
            "${ctx.getString(R.string.discover_filter_type)}: $typeLabel",
            "${ctx.getString(R.string.discover_filter_language)}: ${ctx.getString(value.languageValue().labelRes)}",
            "${ctx.getString(R.string.discover_filter_rating)}: ${ratingLabel(value.ratingValue())}",
            "${ctx.getString(R.string.discover_filter_genres)}: ${value.genreNames.joinToString().ifEmpty { ctx.getString(R.string.filter_defaults_all_genres) }}",
            "${ctx.getString(R.string.discover_filter_year)}: ${YearRange(value.yearFrom, value.yearTo).label()}",
            "${ctx.getString(R.string.discover_filter_sort)}: ${ctx.getString(value.sortValue().labelRes)}",
            ctx.getString(R.string.filter_defaults_factory),
        )
        AlertDialog.Builder(ctx, R.style.AlertDialogCustom).setTitle(title)
            .setItems(rows.toTypedArray()) { _, index ->
                when (index) {
                    0 -> {
                        val types = if (actor) listOf("ALL", "MOVIES", "SERIES") else listOf("MOVIES", "SERIES")
                        val names = types.map { ctx.getString(when (it) {
                            "ALL" -> R.string.discover_all_types
                            "SERIES" -> R.string.discover_series
                            else -> R.string.discover_movies
                        }) }
                        choose(R.string.discover_filter_type, names, types.indexOf(value.type)) {
                            save(value.copy(type = types[it], genreIds = emptySet(), genreNames = emptySet()))
                        }
                    }
                    1 -> choose(R.string.discover_filter_language, DiscoverLanguage.entries.map { ctx.getString(it.labelRes) },
                        DiscoverLanguage.entries.indexOf(value.languageValue())) {
                        save(value.copy(language = DiscoverLanguage.entries[it].name))
                    }
                    2 -> choose(R.string.discover_filter_rating, TmdbRatingFilter.entries.map(::ratingLabel),
                        TmdbRatingFilter.entries.indexOf(value.ratingValue())) {
                        save(value.copy(rating = TmdbRatingFilter.entries[it].minimum))
                    }
                    3 -> {
                        val loading = AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
                            .setMessage(R.string.filter_defaults_loading).setNegativeButton(android.R.string.cancel, null).show()
                        val job = fragment.lifecycleScope.launch {
                            try {
                                val genres = withContext(Dispatchers.IO) {
                                    val repository = DiscoverRepository()
                                    val types = if (actor && value.type == "ALL") DiscoverMediaType.entries.toList()
                                        else listOf(value.typeValue())
                                    types.flatMap { repository.genres(it) }.distinctBy { it.id }.sortedBy { it.name }
                                }
                                if (!loading.isShowing || !fragment.isAdded) return@launch
                                loading.dismiss()
                                val selected = value.genreIds.toMutableSet()
                                AlertDialog.Builder(ctx, R.style.AlertDialogCustom).setTitle(R.string.discover_filter_genres)
                                    .setMultiChoiceItems(genres.map { it.name }.toTypedArray(),
                                        genres.map { it.id in selected }.toBooleanArray()) { _, which, checked ->
                                        if (checked) selected.add(genres[which].id) else selected.remove(genres[which].id)
                                    }
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        save(value.copy(genreIds = selected, genreNames = genres.filter { it.id in selected }.map { it.name }.toSet()))
                                    }.setNeutralButton(R.string.filter_defaults_all_genres) { _, _ ->
                                        save(value.copy(genreIds = emptySet(), genreNames = emptySet()))
                                    }.setNegativeButton(android.R.string.cancel, null).show().also { dialog ->
                                        com.lagradost.cloudstream3.utils.DialogApplyShortcut.install(
                                            dialog, dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                        )
                                    }
                            } catch (cancel: CancellationException) {
                                loading.dismiss()
                                throw cancel
                            } catch (_: Exception) {
                                if (loading.isShowing && fragment.isAdded) {
                                    loading.dismiss()
                                    Toast.makeText(ctx, R.string.discover_error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        loading.setOnCancelListener { job.cancel() }
                    }
                    4 -> {
                        val years: List<Int?> = listOf(null) + (2100 downTo 1900).toList()
                        val labels = years.map { it?.toString() ?: ctx.getString(R.string.filter_defaults_unbounded) }
                        choose(R.string.filter_defaults_year_from, labels, years.indexOf(value.yearFrom)) { from ->
                            choose(R.string.filter_defaults_year_to, labels, years.indexOf(value.yearTo)) { to ->
                                save(value.copy(yearFrom = years[from], yearTo = years[to]))
                            }
                        }
                    }
                    5 -> choose(R.string.discover_filter_sort, DiscoverSort.entries.map { ctx.getString(it.labelRes) },
                        DiscoverSort.entries.indexOf(value.sortValue())) { save(value.copy(sort = DiscoverSort.entries[it].name)) }
                    6 -> AlertDialog.Builder(ctx, R.style.AlertDialogCustom).setMessage(R.string.filter_defaults_factory_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ -> save(FilterDefaults()) }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }.show()
    }
}
