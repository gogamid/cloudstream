package com.lagradost.cloudstream3.utils

import android.content.Context
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
        root.allViews.filterIsInstance<SearchView>().forEach { search ->
            // SearchAutoComplete queues its own IME request on window focus, ignoring
            // showSoftInputOnFocus. This observer runs after that callback and before
            // input-connection setup. clearFocus cancels the request; restore focus so
            // D-pad navigation and explicit OK-to-edit still work normally.
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasWindowFocus ->
                if (hasWindowFocus && search.hasFocus()) {
                    val focused = search.findFocus()
                    search.clearFocus()
                    focused?.requestFocus()
                }
            }
            search.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
                }
                override fun onViewDetachedFromWindow(view: View) {
                    view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
                }
            })
            if (search.isAttachedToWindow) search.viewTreeObserver.addOnWindowFocusChangeListener(listener)
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
        // A footer can sit beside its editor, leaving no geometric Up/Down target.
        // Try traversal order, then its opposite, rather than handing arrows to the caret.
        val traversal = if (direction == View.FOCUS_DOWN) View.FOCUS_FORWARD else View.FOCUS_BACKWARD
        val root = focused.rootView as? ViewGroup
        val targets = sequenceOf(
            explicit,
            focused.focusSearch(direction),
            root?.let { FocusFinder.getInstance().findNextFocus(it, focused, traversal) },
            root?.let {
                FocusFinder.getInstance().findNextFocus(it, focused,
                    if (traversal == View.FOCUS_FORWARD) View.FOCUS_BACKWARD else View.FOCUS_FORWARD)
            },
        )
        for (target in targets.filterNotNull().distinct()) {
            if (target === focused || !target.isShown || !target.isEnabled) continue
            if (target is EditText) target.showSoftInputOnFocus = false
            // Containers should yield to their actual buttons/cards, not become focus traps.
            val moved = if (target is ViewGroup) {
                val candidates = ArrayList<View>()
                target.addFocusables(candidates, direction)
                candidates.firstOrNull { it !== target && it !== focused && it.isShown && it.isEnabled }
                    ?.requestFocus(direction) ?: target.requestFocus(direction)
            } else target.requestFocus(direction)
            // SearchView can accept focus by delegating straight back to this editor.
            // That is not an escape; continue to the next traversal candidate.
            if (moved && !focused.isFocused) {
                ime?.hideSoftInputFromWindow(focused.windowToken, 0)
                return true
            }
        }
        return false
    }

    internal fun direction(key: Int): Int? = when (key) {
        KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
        else -> null
    }
}
