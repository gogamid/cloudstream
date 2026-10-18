package com.lagradost.cloudstream3

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.utils.ComposeTextFocus
import com.lagradost.cloudstream3.utils.DialogApplyShortcut
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** Emulator integration tests use the real Compose editor, Android IME and dialog window. */
@RunWith(AndroidJUnit4::class)
class ForkComposeInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var originalKey: Int? = null
    private val applyKey = KeyEvent.KEYCODE_PROG_RED

    @Before fun setup() {
        originalKey = DialogApplyShortcut.load(context)
        DialogApplyShortcut.save(context, applyKey)
    }

    @After fun restorePreference() {
        DialogApplyShortcut.save(context, originalKey)
    }

    private fun launch(mode: String): ActivityScenario<ForkComposeInputTestActivity> =
        ActivityScenario.launch(Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", mode))

    private fun read(scenario: ActivityScenario<ForkComposeInputTestActivity>, check: (ForkComposeInputTestActivity) -> Boolean): Boolean {
        val result = AtomicBoolean(false)
        scenario.onActivity { result.set(check(it)) }
        return result.get()
    }

    private fun await(message: String, check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }

    private fun press(key: Int) = instrumentation.sendKeyDownUpSync(key)

    @Test fun tvFocusDoesNotOpenImeAndVerticalArrowsEscapeEditor() {
        launch("tv").use { scenario ->
            await("TV editor did not receive focus") { read(scenario) { it.ready.get() && it.typing } }
            SystemClock.sleep(600)
            assertFalse("D-pad focus opened the IME", read(scenario) { it.keyboardVisible })
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            await("Down did not leave the TV editor") { read(scenario) { it.leaveFocused.get() && !it.typing } }
            press(KeyEvent.KEYCODE_DPAD_UP)
            await("Up did not return focus to the editor") { read(scenario) { it.typing } }
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            await("OK did not explicitly open the IME") { read(scenario) { it.keyboardVisible } }
            press(KeyEvent.KEYCODE_BACK)
            await("Back did not close the IME") { !read(scenario) { it.keyboardVisible } }
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            await("OK did not reopen the IME") { read(scenario) { it.keyboardVisible } }
            press(KeyEvent.KEYCODE_BACK)
            await("Back did not close the reopened IME") { !read(scenario) { it.keyboardVisible } }
            press(KeyEvent.KEYCODE_DPAD_UP)
            await("Up did not escape the first editor") { read(scenario) { it.leaveFocused.get() && !it.typing } }
        }
        instrumentation.waitForIdleSync()
        val typing = AtomicBoolean(true)
        instrumentation.runOnMainSync { typing.set(ComposeTextFocus.isTyping) }
        assertFalse("Disposed editor retained the playback typing guard", typing.get())
    }

    @Test fun phoneFocusStillOpensImeNormally() {
        launch("phone").use { scenario ->
            await("Phone editor did not open its normal IME session") { read(scenario) { it.ready.get() && it.keyboardVisible } }
        }
    }

    @Test fun composeApplyOwnsFullGestureAndRejectsCanceledRelease() {
        launch("apply").use { scenario ->
            await("Compose settings dialog did not open") { read(scenario) { it.ready.get() && it.dialogView?.hasWindowFocus() == true } }
            val time = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(time, time, KeyEvent.ACTION_DOWN, applyKey, 0))
            assertFalse("Apply fired on key-down", read(scenario) { it.applied.get() != 0 })
            instrumentation.sendKeySync(KeyEvent(time, time + 1, KeyEvent.ACTION_DOWN, applyKey, 3))
            instrumentation.sendKeySync(KeyEvent(time, time + 2, KeyEvent.ACTION_UP, applyKey, 0, 0, -1, 0, KeyEvent.FLAG_CANCELED))
            assertFalse("Canceled release fired Apply", read(scenario) { it.applied.get() != 0 })
            // No D-pad navigation or child focus is required before this shortcut.
            press(applyKey)
            await("Key-up did not invoke the existing settings Apply action") { read(scenario) { it.applied.get() == 1 } }
            press(applyKey)
            assertTrue("Disposed dialog callback fired again", read(scenario) { it.applied.get() == 1 })
        }
    }

    @Test fun disabledApplyCannotSubmitOrInvokeOtherButtons() {
        launch("disabled").use { scenario ->
            await("Disabled Apply dialog did not open") { read(scenario) { it.ready.get() && it.dialogView?.hasWindowFocus() == true } }
            press(applyKey)
            assertTrue(read(scenario) { it.applied.get() == 0 && it.unrelated.get() == 0 })
        }
    }

    @Test fun genericLoginConfirmationNeverBecomesApply() {
        launch("unrelated").use { scenario ->
            await("Generic dialog did not open") { read(scenario) { it.ready.get() && it.dialogView?.hasWindowFocus() == true } }
            press(applyKey)
            assertTrue(read(scenario) { it.applied.get() == 0 && it.unrelated.get() == 0 })
        }
    }
}
