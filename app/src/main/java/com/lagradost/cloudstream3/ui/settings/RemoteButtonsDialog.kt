package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings

/** Single-press capture is completed on key-up so it cannot select the next dialog. */
internal object RemoteButtonsDialog {
    fun show(context: Context) {
        val mappings = RemoteButtonMappings.load(context).toSortedMap()
        val keys = mappings.keys.toList()
        val labels = listOf(context.getString(R.string.remote_add)) + mappings.map { (key, action) ->
            "${RemoteButtonMappings.keyName(key)} → ${context.getString(action.label)}"
        } + context.getString(R.string.remote_reset)
        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.remote_buttons)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> capture(context)
                    labels.lastIndex -> AlertDialog.Builder(context, R.style.AlertDialogCustom)
                        .setMessage(R.string.remote_reset_confirm)
                        .setPositiveButton(R.string.remote_reset) { _, _ ->
                            RemoteButtonMappings.reset(context)
                            show(context)
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                    else -> choose(context, keys[which - 1])
                }
            }.show()
    }

    private fun capture(context: Context) {
        val dialog = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.remote_press_button)
            .setMessage(R.string.remote_capture_hint)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnKeyListener { _, key, event ->
            if (!RemoteButtonMappings.isMappable(key)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                dialog.dismiss()
                choose(context, key)
            }
            true
        }
        dialog.show()
    }

    private fun choose(context: Context, key: Int) {
        val actions = RemoteButtonMappings.Action.entries
        val labels = listOf(context.getString(R.string.remote_default)) + actions.map { context.getString(it.label) }
        val current = RemoteButtonMappings.load(context)[key]
        val selected = if (current == null) 0 else actions.indexOf(current) + 1
        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(RemoteButtonMappings.keyName(key))
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, index ->
                RemoteButtonMappings.save(context, key, actions.getOrNull(index - 1))
                dialog.dismiss()
                show(context)
            }.show()
    }
}
