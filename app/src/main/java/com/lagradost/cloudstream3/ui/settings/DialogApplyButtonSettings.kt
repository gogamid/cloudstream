package com.lagradost.cloudstream3.ui.settings

import android.content.Context
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings
import com.lagradost.cloudstream3.utils.DialogApplyShortcut

internal object DialogApplyButtonSettings {
    fun show(context: Context, changed: () -> Unit) {
        val key = DialogApplyShortcut.load(context)
        val current = key?.let(RemoteButtonMappings::keyName) ?: context.getString(R.string.remote_disabled)
        AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.dialog_apply_button)
            .setItems(arrayOf(context.getString(R.string.dialog_apply_current, current),
                context.getString(R.string.dialog_apply_assign), context.getString(R.string.remote_disabled))) { _, index ->
                when (index) {
                    1 -> capture(context, changed)
                    2 -> { DialogApplyShortcut.save(context, null); changed() }
                    else -> show(context, changed)
                }
            }.show()
    }

    private fun capture(context: Context, changed: () -> Unit) {
        val dialog = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setTitle(R.string.remote_press_button)
            .setMessage(R.string.dialog_apply_capture)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnKeyListener { _, key, event ->
            if (!RemoteButtonMappings.isMappable(key)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                DialogApplyShortcut.save(context, key)
                dialog.dismiss()
                changed()
            }
            true
        }
        dialog.show()
    }
}
