package com.lagradost.cloudstream3.ui.discover

import android.view.KeyEvent
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings.Action
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class RemoteButtonMappingsTest {
    @Test fun `numbers numpad and colored buttons can be mapped`() {
        listOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        ).forEach { assertTrue(RemoteButtonMappings.isMappable(it)) }
    }

    @Test fun `navigation editing and system controls remain reserved`() {
        listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_UNKNOWN
        ).forEach { assertFalse(RemoteButtonMappings.isMappable(it)) }
    }

    @Test fun `stored overrides distinguish disabled from default and reject invalid data`() {
        val decoded = RemoteButtonMappings.decode(setOf(
            "${KeyEvent.KEYCODE_1}:NEXT_EPISODE",
            "${KeyEvent.KEYCODE_2}:DISABLED",
            "${KeyEvent.KEYCODE_BACK}:EXIT",
            "bad data", "-1:PLAY", "${KeyEvent.KEYCODE_3}:REMOVED_ACTION"
        ))
        assertEquals(mapOf(KeyEvent.KEYCODE_1 to Action.NEXT_EPISODE,
            KeyEvent.KEYCODE_2 to Action.DISABLED), decoded)
        assertNull(decoded[KeyEvent.KEYCODE_3])
        assertTrue(RemoteButtonMappings.decode(emptySet()).isEmpty())
    }

    @Test fun `button five can store next source without changing existing bindings`() {
        val decoded = RemoteButtonMappings.decode(setOf(
            "${KeyEvent.KEYCODE_5}:NEXT_SOURCE", "${KeyEvent.KEYCODE_9}:SOURCES"
        ))
        assertEquals(Action.NEXT_SOURCE, decoded[KeyEvent.KEYCODE_5])
        assertEquals(Action.SOURCES, decoded[KeyEvent.KEYCODE_9])
        assertNull(RemoteButtonMappings.decode(emptySet())[KeyEvent.KEYCODE_5])
    }

    @Test fun `subtitle toggle uses a stable action name without replacing other bindings`() {
        val decoded = RemoteButtonMappings.decode(setOf(
            "${KeyEvent.KEYCODE_6}:TOGGLE_SUBTITLES", "${KeyEvent.KEYCODE_5}:NEXT_SOURCE"
        ))
        assertEquals(Action.TOGGLE_SUBTITLES, decoded[KeyEvent.KEYCODE_6])
        assertEquals(Action.NEXT_SOURCE, decoded[KeyEvent.KEYCODE_5])
        assertTrue(RemoteButtonMappings.decode(emptySet()).isEmpty())
    }

    @Test fun `mappings remain eligible for settings backups`() {
        assertTrue(RemoteButtonMappings.KEY.isTransferable())
    }
}
