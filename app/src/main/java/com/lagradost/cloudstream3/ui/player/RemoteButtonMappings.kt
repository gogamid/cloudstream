package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R

/** Stable names, not enum ordinals: settings backup/restore preserves bindings. */
internal object RemoteButtonMappings {
    const val KEY = "player_remote_button_mappings"

    enum class Action(@StringRes val label: Int) {
        DISABLED(R.string.remote_disabled),
        PLAY_PAUSE(R.string.remote_play_pause), PLAY(R.string.remote_play), PAUSE(R.string.remote_pause),
        RESTART(R.string.remote_restart), MUTE(R.string.remote_mute),
        SEEK_FORWARD(R.string.remote_seek_forward), SEEK_BACK(R.string.remote_seek_back),
        SKIP_OPENING(R.string.remote_skip_opening), SKIP_CHAPTER(R.string.remote_skip_chapter),
        NEXT_EPISODE(R.string.remote_next_episode), PREVIOUS_EPISODE(R.string.remote_previous_episode),
        EPISODES(R.string.remote_episodes), SUBTITLE_SEARCH(R.string.remote_subtitle_search),
        SUBTITLE_TIMING(R.string.remote_subtitle_timing), SOURCES(R.string.remote_sources),
        TRACKS(R.string.remote_tracks), SPEED(R.string.remote_speed), RESIZE(R.string.remote_resize),
        CONTROLS(R.string.remote_controls), LOCK(R.string.remote_lock),
        AUDIO_ONLY(R.string.remote_audio_only), EXIT(R.string.remote_exit),
    }

    // Keep navigation, editing and OS controls outside the remapping surface.
    fun isMappable(key: Int): Boolean = key > KeyEvent.KEYCODE_UNKNOWN &&
        key !in reserved

    private val reserved = setOf(
        KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_STB_POWER, KeyEvent.KEYCODE_AVR_POWER,
        KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
    )

    fun decode(values: Set<String>): Map<Int, Action> = values.sorted().mapNotNull { value ->
        val key = value.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
        val action = Action.entries.firstOrNull { it.name == value.substringAfter(':', "") }
            ?: return@mapNotNull null
        if (isMappable(key)) key to action else null
    }.toMap()

    fun load(context: Context): Map<Int, Action> = decode(
        PreferenceManager.getDefaultSharedPreferences(context).getStringSet(KEY, emptySet()).orEmpty()
    )

    fun save(context: Context, key: Int, action: Action?) {
        if (!isMappable(key)) return
        val values = load(context).toMutableMap()
        if (action == null) values.remove(key) else values[key] = action
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putStringSet(KEY, values.map { "${it.key}:${it.value.name}" }.toSet())
        }
    }

    fun reset(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { remove(KEY) }
    }

    fun keyName(key: Int): String = KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")
}
