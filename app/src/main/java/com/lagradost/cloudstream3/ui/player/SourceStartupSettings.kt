package com.lagradost.cloudstream3.ui.player

import android.content.Context
import com.lagradost.cloudstream4.AppSettings

/** Same backed-up preference as the active Player settings slider. */
internal fun sourceStartupWaitMs(context: Context): Long =
    SourceStartupTimeout.milliseconds(AppSettings(context).player.sourceStartupWaitSeconds.get())

/** Initial collection threshold, independent of each attempted video's startup wait. */
internal fun sourceAutoStartCount(context: Context): Int =
    AppSettings(context).player.sourceAutoStartCount.get().let {
        if (it >= 0) it else SourcePlaybackPlan.DEFAULT_AUTO_START_COUNT
    }
