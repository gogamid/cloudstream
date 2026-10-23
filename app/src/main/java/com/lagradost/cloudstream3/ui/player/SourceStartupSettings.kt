package com.lagradost.cloudstream3.ui.player

import android.content.Context
import com.lagradost.cloudstream4.AppSettings

/** Same backed-up preference as the active Player settings slider. */
internal fun sourceStartupWaitMs(context: Context): Long =
    SourceStartupTimeout.milliseconds(AppSettings(context).player.sourceStartupWaitSeconds.get())
