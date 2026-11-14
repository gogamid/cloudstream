package com.lagradost.cloudstream3

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.core.view.allViews
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android windows/editors and hardware D-pad dispatch. No provider login or settings writes. */
@RunWith(AndroidJUnit4::class)
class ForkTvNativeInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val layoutKey get() = context.getString(R.string.app_layout_key)
    private var previousLayout: Int? = null

    @Before fun tvLayout() {
        previousLayout = preferences.all[layoutKey] as? Int
        assertTrue(preferences.edit().putInt(layoutKey, 2).commit())
        context.updateTv()
    }

    @After fun restoreLayout() {
        assertTrue(preferences.edit().apply {
            previousLayout?.let { putInt(layoutKey, it) } ?: remove(layoutKey)
        }.commit())
        context.updateTv()
    }

    private fun press(key: Int) {
        instrumentation.sendKeyDownUpSync(key)
        instrumentation.waitForIdleSync()
    }

    @Test fun loginVisitsEveryVisibleFieldAndReturnsFromFooter() {
        for (email in listOf(false, true)) {
            for (create in listOf(false, true)) {
                ActivityScenario.launch<ForkComposeInputTestActivity>(
                    Intent(context, ForkComposeInputTestActivity::class.java)
                        .putExtra("mode", "native-login").putExtra("email", email).putExtra("create", create)
                ).use {
                    val fields = listOfNotNull(R.id.login_username_input,
                        R.id.login_email_input.takeIf { email }, R.id.login_server_input, R.id.login_password_input)
                    onView(withId(fields.first())).perform(click())
                    for (id in fields.drop(1)) {
                        press(KeyEvent.KEYCODE_DPAD_DOWN)
                        onView(withId(id)).check(matches(hasFocus()))
                    }
                    press(KeyEvent.KEYCODE_DPAD_DOWN)
                    onView(withId(R.id.apply_btt)).check(matches(hasFocus()))
                    for (id in fields.reversed()) {
                        press(KeyEvent.KEYCODE_DPAD_UP)
                        onView(withId(id)).check(matches(hasFocus()))
                    }
                    press(KeyEvent.KEYCODE_DPAD_UP)
                    onView(withId(if (create) R.id.create_account else R.id.cancel_btt)).check(matches(hasFocus()))
                    press(KeyEvent.KEYCODE_BACK)
                }
            }
        }
    }

    @Test fun nativeDialogEditorsCanLeaveInBothVerticalDirectionsIncludingEmptyLists() {
        val layouts = listOf(R.layout.add_repo_input, R.layout.add_site_input,
            R.layout.stream_input, R.layout.bottom_input_dialog, R.layout.lock_pin_dialog,
            R.layout.account_edit_dialog, R.layout.player_select_source_priority, R.layout.subtitle_offset,
            R.layout.dialog_online_subtitles)
        for (layout in layouts) {
            ActivityScenario.launch<ForkComposeInputTestActivity>(
                Intent(context, ForkComposeInputTestActivity::class.java)
                    .putExtra("mode", "native-input").putExtra("layout", layout)
            ).use { scenario ->
                var editors = emptyList<EditText>()
                scenario.onActivity { activity ->
                    editors = activity.nativeRoot!!.allViews.filterIsInstance<EditText>().filter { it.isShown }.toList()
                }
                assertTrue("No editor exercised in layout $layout", editors.isNotEmpty())
                for (editor in editors) {
                    for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
                        scenario.onActivity {
                            assertFalse("TV focus must not automatically open IME", editor.showSoftInputOnFocus)
                            assertTrue(editor.requestFocus())
                        }
                        press(key)
                        scenario.onActivity { activity ->
                            val focused: View? = activity.nativeDialog?.currentFocus
                            assertNotNull("No focus after key $key in layout $layout", focused)
                            assertNotSame("Editor trapped key $key in layout $layout", editor, focused)
                            assertTrue(focused!!.isShown && focused.isEnabled)
                        }
                    }
                }
                scenario.onActivity { it.nativeDialog?.dismiss() }
            }
        }
    }
}
