package com.lagradost.cloudstream3.ui.discover

import android.view.KeyEvent
import com.lagradost.cloudstream3.utils.DialogApplyShortcut
import com.lagradost.cloudstream3.utils.BackupUtils.isTransferable
import org.junit.Assert.*
import org.junit.Test

class DialogApplyShortcutTest {
    @Test fun `only uncancelled key release activates apply`() {
        assertTrue(DialogApplyShortcut.shouldActivate(KeyEvent.ACTION_UP, 0, false))
        assertFalse(DialogApplyShortcut.shouldActivate(KeyEvent.ACTION_DOWN, 0, false))
        assertFalse(DialogApplyShortcut.shouldActivate(KeyEvent.ACTION_DOWN, 5, false))
        assertFalse(DialogApplyShortcut.shouldActivate(KeyEvent.ACTION_UP, 0, true))
        assertFalse(DialogApplyShortcut.shouldActivate(KeyEvent.ACTION_MULTIPLE, 0, false))
    }

    @Test fun `apply preference remains part of settings backups`() {
        assertTrue(DialogApplyShortcut.KEY.isTransferable())
    }
}
