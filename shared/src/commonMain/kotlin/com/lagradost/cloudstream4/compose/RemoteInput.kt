package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Android installs application-owned policy; desktop and previews remain no-ops. */
object RemoteInputBridge {
    var applyKey: (KeyEvent, () -> Unit) -> Boolean = { _, _ -> false }
    var textFocus: (Any, Boolean) -> Unit = { _, _ -> }
}

/** Call inside dialog content so Android binds the dialog's own window, even
 * before a child receives focus. Never opt arbitrary Login/Add/Delete actions in.
 */
@Composable
expect fun DialogApplyEffect(enabled: Boolean = true, apply: () -> Unit)

class TvTextInput internal constructor(
    val modifier: Modifier,
    val readOnly: Boolean,
    private val showKeyboardOnFocus: Boolean?,
) {
    fun options(original: KeyboardOptions): KeyboardOptions =
        if (showKeyboardOnFocus == null) original
        else original.copy(showKeyboardOnFocus = showKeyboardOnFocus)
}

/** Read-only focus avoids Compose starting an IME session just from TV D-pad focus.
 * OK explicitly enables editing; vertical arrows leave, horizontal arrows keep normal
 * TextField selection/caret handling. Phone input options are left untouched.
 */
@Composable
fun rememberTvTextInput(): TvTextInput {
    val tv = isLayout(TV or EMULATOR)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }
    val token = remember { Any() }
    DisposableEffect(token) {
        onDispose { RemoteInputBridge.textFocus(token, false) }
    }
    LaunchedEffect(editing) {
        if (tv && editing) {
            withFrameNanos { }
            keyboard?.show()
        }
    }
    val modifier = Modifier.onFocusChanged {
        RemoteInputBridge.textFocus(token, it.isFocused)
        if (!it.isFocused && editing) {
            editing = false
            if (tv) keyboard?.hide()
        }
    }.onPreviewKeyEvent { event ->
        if (!tv) false
        else when (event.key) {
            Key.DirectionUp, Key.DirectionDown -> {
                if (event.type == KeyEventType.KeyDown) {
                    editing = false
                    keyboard?.hide()
                    val direction = if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down
                    if (!focus.moveFocus(direction)) focus.moveFocus(FocusDirection.Next)
                }
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (event.type == KeyEventType.KeyUp) {
                    if (editing) keyboard?.show() else editing = true
                }
                true
            }
            else -> false
        }
    }
    return TvTextInput(modifier, tv && !editing, if (tv) editing else null)
}
