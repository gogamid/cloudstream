package com.lagradost.cloudstream3

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.utils.DialogApplyShortcut
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Use Compose semantics rather than guessing accessibility-node merging boundaries.
 * Kept separate so the physical focus/IME tests retain the real Android frame clock.
 */
@RunWith(AndroidJUnit4::class)
class ForkComposePreferenceRetryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun selectionDialogsNavigateWithOrWithoutOptionalButtons() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (multi in listOf(false, true)) {
            for (confirm in listOf(false, true)) {
                for (dismiss in listOf(false, true)) {
                    ActivityScenario.launch<ForkComposeInputTestActivity>(
                        Intent(instrumentation.targetContext, ForkComposeInputTestActivity::class.java)
                            .putExtra("mode", "selection").putExtra("multi", multi)
                            .putExtra("confirm", confirm).putExtra("dismiss", dismiss)
                    ).use { scenario ->
                        fun focusFirst() {
                            compose.onNodeWithText("First").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                        }
                        fun press(key: Int) {
                            instrumentation.sendKeyDownUpSync(key)
                            compose.waitForIdle()
                        }
                        focusFirst()
                        press(KeyEvent.KEYCODE_DPAD_LEFT)
                        if (dismiss) compose.onNodeWithText("Cancel").assertIsFocused()
                        focusFirst()
                        press(KeyEvent.KEYCODE_DPAD_RIGHT)
                        if (confirm) compose.onNodeWithText("Apply").assertIsFocused()
                        focusFirst()
                        press(KeyEvent.KEYCODE_DPAD_DOWN)
                        compose.onNodeWithText("Second").assertIsFocused()
                        press(KeyEvent.KEYCODE_DPAD_UP)
                        compose.onNodeWithText("First").assertIsFocused()
                        press(KeyEvent.KEYCODE_DPAD_CENTER)
                        if (confirm) {
                            compose.onNodeWithText("Apply").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                            press(KeyEvent.KEYCODE_DPAD_CENTER)
                        } else if (multi) {
                            press(KeyEvent.KEYCODE_BACK) // Immediate multi-select saves on dismissal.
                        }
                        scenario.onActivity { assertEquals(1, it.applied.get()) }
                    }
                }
            }
        }
    }

    @Test fun rejectedEditorApplyCanRetryAndSuccessfulSaveCannotSubmitAgain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalKey = DialogApplyShortcut.load(context)
        val key = KeyEvent.KEYCODE_PROG_RED
        DialogApplyShortcut.save(context, key)
        try {
            ActivityScenario.launch<ForkComposeInputTestActivity>(
                Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "retry")
            ).use { scenario ->
                fun attempts(): Int {
                    val result = AtomicInteger()
                    scenario.onActivity { result.set(it.applied.get()) }
                    return result.get()
                }
                compose.onNodeWithText("Retry preference").performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("Changed")
                compose.waitForIdle()
                instrumentation.sendKeyDownUpSync(key)
                compose.waitUntil(timeoutMillis = 15_000) { attempts() == 1 }
                compose.waitForIdle()
                assertEquals(1, attempts())
                instrumentation.sendKeyDownUpSync(key)
                compose.waitUntil(timeoutMillis = 15_000) { attempts() == 2 }
                // A third gesture cannot resubmit a successful, closing editor.
                instrumentation.sendKeyDownUpSync(key)
                compose.waitForIdle()
                assertEquals(2, attempts())
            }
        } finally {
            DialogApplyShortcut.save(context, originalKey)
        }
    }
}
