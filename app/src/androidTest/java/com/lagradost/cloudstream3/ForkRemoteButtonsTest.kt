package com.lagradost.cloudstream3

import android.content.Intent
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings
import com.lagradost.cloudstream3.utils.DialogApplyShortcut
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real native settings navigation, storage compatibility and button-5 assignment. */
@RunWith(AndroidJUnit4::class)
class ForkRemoteButtonsTest {
    @Test fun applySettingLivesUnderRemoteButtonsAndFiveCanMapToNextSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val originalMappings = prefs.getStringSet(RemoteButtonMappings.KEY, null)?.toSet()
        val originalApply = DialogApplyShortcut.load(context)
        val key = KeyEvent.KEYCODE_5
        val apply = KeyEvent.KEYCODE_PROG_RED
        fun row(action: RemoteButtonMappings.Action) =
            "${RemoteButtonMappings.keyName(key)} → ${context.getString(action.label)}"
        fun applyRow(code: Int?) = "${context.getString(R.string.dialog_apply_button)} → " +
            (code?.let(RemoteButtonMappings::keyName) ?: context.getString(R.string.remote_disabled))
        try {
            DialogApplyShortcut.save(context, apply)
            RemoteButtonMappings.save(context, key, RemoteButtonMappings.Action.SKIP_CHAPTER)
            ActivityScenario.launch<ForkComposeInputTestActivity>(
                Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "remote")
            ).use {
                onView(withText(applyRow(apply))).check(matches(isDisplayed()))
                onData(equalTo(row(RemoteButtonMappings.Action.SKIP_CHAPTER))).perform(click())
                onData(equalTo(context.getString(R.string.remote_next_source))).perform(click())
                assertEquals(RemoteButtonMappings.Action.NEXT_SOURCE, RemoteButtonMappings.load(context)[key])
                assertEquals(apply, DialogApplyShortcut.load(context))
                onView(withText(row(RemoteButtonMappings.Action.NEXT_SOURCE))).check(matches(isDisplayed()))

                onData(equalTo(context.getString(R.string.remote_add))).perform(click())
                onView(withText(R.string.remote_press_button)).check(matches(isDisplayed()))
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(key)
                onData(equalTo(context.getString(R.string.remote_next_source))).perform(click())
                assertEquals(RemoteButtonMappings.Action.NEXT_SOURCE, RemoteButtonMappings.load(context)[key])

                onData(equalTo(applyRow(apply))).perform(click())
                onData(equalTo(context.getString(R.string.remote_disabled))).perform(click())
                assertNull(DialogApplyShortcut.load(context))
                assertEquals(RemoteButtonMappings.Action.NEXT_SOURCE, RemoteButtonMappings.load(context)[key])
                onView(withText(applyRow(null))).check(matches(isDisplayed()))

                // Reset retains its original playback-only scope, now made explicit in the label.
                DialogApplyShortcut.save(context, apply)
                onData(equalTo(context.getString(R.string.remote_reset))).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                assertTrue(RemoteButtonMappings.load(context).isEmpty())
                assertEquals(apply, DialogApplyShortcut.load(context))
            }
        } finally {
            val editor = prefs.edit()
            if (originalMappings == null) editor.remove(RemoteButtonMappings.KEY)
            else editor.putStringSet(RemoteButtonMappings.KEY, originalMappings)
            editor.apply()
            DialogApplyShortcut.save(context, originalApply)
        }
    }
}
