package com.lagradost.cloudstream3

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.ui.player.SourceStartupTimeout
import com.lagradost.cloudstream3.ui.player.sourceStartupWaitMs
import com.lagradost.cloudstream3.ui.player.sourceAutoStartCount
import com.lagradost.cloudstream4.AppSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real active settings UI -> stored preference -> production startup-budget adapter. */
@RunWith(AndroidJUnit4::class)
class ForkSourceWaitSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun playbackDiagnosticsDefaultOffCanEnablePersistAndDisable() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val setting = AppSettings(context).player.playbackDiagnostics
        val previous = preferences.all[setting.key()] as? Boolean
        assertTrue(preferences.edit().remove(setting.key()).commit())
        try {
            assertFalse(setting.get())
            ActivityScenario.launch<ForkComposeInputTestActivity>(
                Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "playback-diagnostics")
            ).use { scenario ->
                val toggle = compose.onNodeWithText(context.getString(R.string.playback_diagnostics_title))
                toggle.assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 15_000) { setting.get() }
                scenario.recreate()
                compose.waitForIdle()
                assertTrue(AppSettings(context).player.playbackDiagnostics.get())
                toggle.performClick()
                compose.waitUntil(timeoutMillis = 15_000) { !setting.get() }
            }
        } finally {
            assertTrue(preferences.edit().apply {
                if (previous == null) remove(setting.key()) else putBoolean(setting.key(), previous)
            }.commit())
        }
    }

    private fun isolatedPreference(block: () -> Unit) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = AppSettings(context).player
        val keys = listOf(settings.sourceStartupWaitSeconds.key(), settings.sourceAutoStartCount.key())
        val original = keys.associateWith { preferences.all[it] }
        assertTrue(preferences.edit().apply { keys.forEach { remove(it) } }.commit())
        try { block() } finally {
            assertTrue(preferences.edit().apply {
                original.forEach { (key, value) -> if (value == null) remove(key) else putInt(key, value as Int) }
            }.commit())
        }
    }

    @Test fun activeSliderPersistsCustomWaitAndDisableAcrossRecreation() = isolatedPreference {
        val preference = AppSettings(context).player.sourceStartupWaitSeconds
        assertEquals(5, preference.get())
        assertEquals(5_000L, sourceStartupWaitMs(context))
        val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ActivityScenario.launch<ForkComposeInputTestActivity>(
            Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "source-wait")
        ).use { scenario ->
            compose.onNodeWithText(context.getString(R.string.source_startup_wait_title)).assertIsDisplayed()
            compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
            compose.waitUntil(timeoutMillis = 15_000) { preference.get() == 15 }
            assertEquals(15_000L, sourceStartupWaitMs(context))
            scenario.recreate()
            compose.waitForIdle()
            val info = compose.onNode(slider).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(15f, info.current, 0.001f)
            compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
            compose.waitUntil(timeoutMillis = 15_000) { preference.get() == 0 }
            assertEquals(0L, sourceStartupWaitMs(context))
            assertFalse(SourceStartupTimeout(true, sourceStartupWaitMs(context)).tick(Long.MAX_VALUE, true, false))
        }
    }

    @Test fun activeCountSliderDefaultsToFiftyAndPersistsCustomOrDisabledWithoutChangingWait() = isolatedPreference {
        val settings = AppSettings(context).player
        assertEquals(50, settings.sourceAutoStartCount.get())
        assertEquals(50, sourceAutoStartCount(context))
        settings.sourceStartupWaitSeconds.set(15)
        val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ActivityScenario.launch<ForkComposeInputTestActivity>(
            Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "source-count")
        ).use { scenario ->
            compose.onNodeWithText(context.getString(R.string.source_auto_start_count_title)).assertIsDisplayed()
            val initial = compose.onNode(slider).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(50f, initial.current, 0.001f)
            compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
            compose.waitUntil(timeoutMillis = 15_000) { settings.sourceAutoStartCount.get() == 25 }
            assertEquals(25, sourceAutoStartCount(context))
            scenario.recreate()
            compose.waitForIdle()
            val info = compose.onNode(slider).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(25f, info.current, 0.001f)
            compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
            compose.waitUntil(timeoutMillis = 15_000) { settings.sourceAutoStartCount.get() == 0 }
            assertEquals(0, sourceAutoStartCount(context))
            assertEquals(15_000L, sourceStartupWaitMs(context))
        }
    }

    @Test fun savedBudgetGivesEveryAutomaticAttemptItsOwnWaitWithoutChangingManualBehavior() = isolatedPreference {
        val preference = AppSettings(context).player.sourceStartupWaitSeconds
        preference.set(10)
        val first = SourceStartupTimeout(true, sourceStartupWaitMs(context))
        assertFalse(first.tick(5_000, true, false))
        assertTrue(first.tick(5_000, true, false))
        val next = SourceStartupTimeout(true, sourceStartupWaitMs(context))
        assertFalse(next.tick(9_999, true, false))
        // A running attempt keeps its snapshot; a later attempt reads the new setting.
        preference.set(30)
        assertTrue(next.tick(1, true, false))
        val updated = SourceStartupTimeout(true, sourceStartupWaitMs(context))
        assertFalse(updated.tick(29_999, true, false))
        assertTrue(updated.tick(1, true, false))
        val manual = SourceStartupTimeout(false, sourceStartupWaitMs(context))
        assertFalse(manual.tick(Long.MAX_VALUE, true, false))
    }
}
