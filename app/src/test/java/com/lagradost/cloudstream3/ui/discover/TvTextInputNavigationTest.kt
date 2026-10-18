package com.lagradost.cloudstream3.ui.discover

import android.view.KeyEvent
import android.view.View
import com.lagradost.cloudstream3.utils.TvTextInputNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvTextInputNavigationTest {
    @Test
    fun `vertical arrows are navigation keys`() {
        assertEquals(View.FOCUS_UP, TvTextInputNavigation.direction(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(View.FOCUS_DOWN, TvTextInputNavigation.direction(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun `caret editing typing and back are not directional escape keys`() {
        listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER
        ).forEach { assertNull(TvTextInputNavigation.direction(it)) }
    }
}
