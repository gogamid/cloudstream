package com.lagradost.cloudstream3.ui.player

import android.content.Context
import com.lagradost.cloudstream4.AppSettings

/** Same backed-up preference definitions used by the active Player settings UI. */
internal class PlayerSeekSettings(context: Context) {
    private val settings = AppSettings(context.applicationContext).player

    fun amountMs(isTv: Boolean, controlsVisible: Boolean): Long = SeekDurationPolicy.amountMs(
        isTv, controlsVisible, settings.doubleTapTime.get(),
        settings.tvSeekOnTime.get(), settings.tvSeekOffTime.get()
    )
}
