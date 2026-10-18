package com.lagradost.cloudstream3.utils

import android.content.Context
import com.lagradost.cloudstream4.compose.RemoteInputBridge

/** Main-thread focus ownership. A disposed old editor must not clear a newer one. */
internal object ComposeTextFocus {
    private val owners = mutableSetOf<Any>()
    val isTyping: Boolean get() = owners.isNotEmpty()
    fun update(owner: Any, focused: Boolean) {
        if (focused) owners.add(owner) else owners.remove(owner)
    }
}

internal fun installComposeRemoteInput(context: Context) {
    val appContext = context.applicationContext
    RemoteInputBridge.textFocus = ComposeTextFocus::update
    RemoteInputBridge.applyKey = { event, apply ->
        val native = event.nativeKeyEvent
        if (native.keyCode != DialogApplyShortcut.load(appContext)) false
        else {
            if (DialogApplyShortcut.shouldActivate(native.action, native.repeatCount, native.isCanceled)) apply()
            true
        }
    }
}
