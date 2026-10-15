package com.lagradost.cloudstream3.utils

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings

/** Opt-in window handler. Invokes the existing button, never duplicates its save logic. */
internal object DialogApplyShortcut {
    const val KEY = "dialog_apply_remote_key"

    fun load(context: Context): Int? = PreferenceManager.getDefaultSharedPreferences(context)
        .getInt(KEY, -1).takeIf { RemoteButtonMappings.isMappable(it) }

    fun save(context: Context, key: Int?) {
        if (key != null && !RemoteButtonMappings.isMappable(key)) return
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            if (key == null) remove(KEY) else putInt(KEY, key)
        }
    }

    fun shouldActivate(action: Int, repeats: Int, canceled: Boolean): Boolean =
        action == KeyEvent.ACTION_UP && repeats == 0 && !canceled

    fun handle(root: View, event: KeyEvent, explicit: View? = null): Boolean {
        if (event.keyCode != load(root.context)) return false
        val button = explicit ?: root.findViewById<TextView>(R.id.apply_btt)?.takeIf {
            // Several unrelated Login/Add/Play buttons share the apply_btt ID.
            val applyLabel = root.context.getString(R.string.sort_apply)
            it.text.toString() == applyLabel || it.getTag(R.id.text1)?.toString() == applyLabel
        } ?: return false
        if (!button.isShown) return false
        // Consume the full key gesture, including while disabled/loading. Saving is
        // performed only on release, so repeat events cannot submit repeatedly.
        if (shouldActivate(event.action, event.repeatCount, event.isCanceled) &&
            button.isEnabled && button.isClickable && !button.hasTransientState() &&
            (button !is com.google.android.material.button.MaterialButton ||
                button.icon !is com.google.android.material.progressindicator.IndeterminateDrawable<*>)
        ) button.performClick()
        return true
    }

    fun install(dialog: Dialog, explicit: View? = null) {
        val window = dialog.window ?: return
        val callback = window.callback ?: return
        if (callback is ApplyCallback) return
        window.callback = ApplyCallback(callback, window.decorView, explicit)
    }

    private class ApplyCallback(callback: android.view.Window.Callback, val root: View, val button: View?) :
        android.view.Window.Callback by callback {
        private val original = callback
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            handle(root, event, button) || original.dispatchKeyEvent(event)
    }
}
