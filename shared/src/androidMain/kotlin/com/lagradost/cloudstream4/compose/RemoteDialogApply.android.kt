package com.lagradost.cloudstream4.compose

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun isTvRemoteInput(): Boolean = isLayout(TV or EMULATOR)

@Composable
actual fun DialogApplyEffect(enabled: Boolean, oneShot: Boolean, apply: () -> Unit) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
        ?: (view as? DialogWindowProvider)?.window
    val latest = rememberUpdatedState(apply)
    val allowed = rememberUpdatedState(enabled)
    DisposableEffect(window, enabled, oneShot) {
        val original = window?.callback
        var active = true
        // Compose dismissal is asynchronous. Block a second physical gesture
        // even before the closing window's composition has been disposed.
        var submitted = false
        val callback = original?.let {
            object : Window.Callback by it {
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
                    (active && RemoteInputBridge.applyKey(KeyEvent(event)) {
                        if (allowed.value && (!oneShot || !submitted)) {
                            submitted = true
                            latest.value()
                        }
                    }) || it.dispatchKeyEvent(event)
            }
        }
        if (callback != null) window?.callback = callback
        onDispose {
            active = false
            if (window != null && window.callback === callback && original != null) window.callback = original
        }
    }
}
