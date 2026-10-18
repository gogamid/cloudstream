package com.lagradost.cloudstream3.utils

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.core.view.allViews
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout

/** TV editors use vertical arrows for navigation, horizontal arrows for the caret.
 * Never intercept keys inside the IME: Back closes the keyboard normally first.
 */
internal object TvTextInputNavigation {
    fun prepare(root: View, dialog: Boolean = false) {
        if (!isLayout(TV or EMULATOR)) return
        root.allViews.filterIsInstance<EditText>().forEach { input ->
            // Focusing a field is not the same as choosing to edit it with OK.
            input.showSoftInputOnFocus = false
            if (dialog) input.setOnKeyListener { view, _, event -> handle(view, event) }
        }
    }

    fun handle(focused: View?, event: KeyEvent): Boolean {
        if (!isLayout(TV or EMULATOR) || focused !is EditText) return false
        val direction = direction(event.keyCode)
        val ime = focused.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_UP) {
                ime?.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT)
            }
            return true
        }
        if (direction == null || event.action != KeyEvent.ACTION_DOWN) return false
        // SearchView's focus links live on its container, not its internal editor.
        val search = generateSequence(focused.parent) { it.parent }.filterIsInstance<SearchView>().firstOrNull()
        val id = if (direction == View.FOCUS_DOWN) search?.nextFocusDownId else search?.nextFocusUpId
        val explicit = id?.takeIf { it != View.NO_ID }?.let { focused.rootView.findViewById<View>(it) }
        val target = explicit?.takeIf { it.isShown && it.isEnabled }
            ?: focused.focusSearch(direction)
        if (target == null || target === focused || !target.isShown || !target.isEnabled) return false
        if (target is EditText) target.showSoftInputOnFocus = false
        // Containers should yield to their actual buttons/cards, not become focus traps.
        val moved = if (target is ViewGroup) {
            val candidates = ArrayList<View>()
            target.addFocusables(candidates, direction)
            candidates.firstOrNull { it !== target && it !== focused && it.isShown && it.isEnabled }
                ?.requestFocus(direction) ?: target.requestFocus(direction)
        } else target.requestFocus(direction)
        if (moved) ime?.hideSoftInputFromWindow(focused.windowToken, 0)
        return moved
    }

    internal fun direction(key: Int): Int? = when (key) {
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }
}
