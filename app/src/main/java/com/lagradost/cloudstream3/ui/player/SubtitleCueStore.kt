package com.lagradost.cloudstream3.ui.player

/** The decoder writes on its worker thread; sync dialogs read immutable snapshots on main. */
internal class SubtitleCueStore<T> {
    private val cues = ArrayList<T>()
    @Synchronized fun add(cue: T) { cues.add(cue) }
    @Synchronized fun clear() { cues.clear() }
    @Synchronized fun snapshot(): List<T> = cues.toList()
}
