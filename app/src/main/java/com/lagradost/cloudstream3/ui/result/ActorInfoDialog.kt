package com.lagradost.cloudstream3.ui.result

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.DialogActorInfoBinding
import com.lagradost.cloudstream3.ui.discover.TmdbMetadata
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActorInfoDialog : DialogFragment() {
    companion object {
        private const val TAG = "actor_info"
        fun show(context: Context, actor: Actor) {
            val manager = (context.getActivity() as? FragmentActivity)?.supportFragmentManager ?: return
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            ActorInfoDialog().apply {
                arguments = Bundle().apply {
                    putString("name", actor.name)
                    putString("image", actor.image)
                }
            }.show(manager, TAG)
        }
    }

    private var job: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val name = arguments?.getString("name").orEmpty()
        val image = arguments?.getString("image")
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
        val binding = DialogActorInfoBinding.inflate(LayoutInflater.from(builder.context))
        if (isLayout(PHONE)) binding.actorInfoText.textSize = 15f
        binding.actorInfoPortrait.loadImage(image)
        binding.actorInfoText.setText(R.string.actor_info_loading)
        val dialog = builder.setTitle(name).setView(binding.root).create()
        dialog.setOnShowListener { binding.actorInfoScroll.requestFocus() }
        job = lifecycleScope.launch {
            try {
                val details = withContext(Dispatchers.IO) {
                    ActorFilmographyRepository().details(Actor(name, image))
                }
                if (details == null) {
                    binding.actorInfoText.setText(R.string.actor_info_missing)
                    return@launch
                }
                details.name?.takeIf { it.isNotBlank() }?.let(dialog::setTitle)
                details.profilePath?.takeIf { it.isNotBlank() }?.let {
                    binding.actorInfoPortrait.loadImage(TmdbMetadata.IMAGE_URL + it)
                }
                val lines = mutableListOf<String>()
                details.department?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
                details.birthday?.takeIf { it.isNotBlank() }?.let {
                    lines.add(getString(R.string.actor_info_born, it))
                }
                details.deathday?.takeIf { it.isNotBlank() }?.let {
                    lines.add(getString(R.string.actor_info_died, it))
                }
                details.age()?.let {
                    lines.add(getString(if (details.deathday.isNullOrBlank())
                        R.string.actor_info_age else R.string.actor_info_age_at_death, it))
                }
                details.birthplace?.takeIf { it.isNotBlank() }?.let {
                    lines.add(getString(R.string.actor_info_birthplace, it))
                }
                lines.add("\n" + (details.biography?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.actor_info_no_biography)))
                binding.actorInfoText.text = lines.joinToString("\n")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                binding.actorInfoText.setText(R.string.actor_info_error)
            }
        }
        return dialog
    }

    override fun onDestroyView() {
        job?.cancel()
        job = null
        super.onDestroyView()
    }
}
