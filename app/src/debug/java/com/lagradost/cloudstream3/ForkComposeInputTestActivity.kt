package com.lagradost.cloudstream3

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lagradost.cloudstream3.utils.ComposeTextFocus
import com.lagradost.cloudstream4.compose.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only instrumentation host. Never part of the signed release APK. */
class ForkComposeInputTestActivity : ComponentActivity() {
    val ready = AtomicBoolean(false)
    val applied = AtomicInteger(0)
    val unrelated = AtomicInteger(0)
    val leaveFocused = AtomicBoolean(false)
    var dialogView: View? = null
    private var editorFocus: FocusRequester? = null
    fun focusEditor() { editorFocus?.requestFocus() }
    val typing: Boolean get() = ComposeTextFocus.isTyping
    val keyboardVisible: Boolean get() = ViewCompat.getRootWindowInsets(window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "tv"
        if (mode == "remote") {
            com.lagradost.cloudstream3.ui.settings.RemoteButtonsDialog.show(this)
            ready.set(true)
            return
        }
        setContent {
            CloudStreamThemeAppSettings {
                CompositionLocalProvider(DeviceLayout.LocalLayout provides if (mode == "phone" || mode == "retry") PHONE else TV) {
                    Surface(Modifier.fillMaxSize()) {
                        when (mode) {
                            "apply" -> ApplyDialog()
                            "retry" -> RetryPreference()
                            "source-wait" -> SourceWaitPreference()
                            "disabled", "unrelated" -> OtherDialog(mode)
                            else -> Editor(mode == "settings-search")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Editor(settingsSearch: Boolean) {
        var text by remember { mutableStateOf("") }
        val requester = remember { FocusRequester() }
        SideEffect { editorFocus = requester }
        Column {
            if (settingsSearch) {
                androidx.compose.foundation.layout.Box(Modifier.focusRequester(requester)) {
                    com.lagradost.cloudstream3.ui.settings.SettingsFragmentScreen.SettingsSearch(
                        androidx.compose.foundation.text.input.rememberTextFieldState()
                    )
                }
            } else {
                BlackTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(requester),
                    value = text,
                    onValueChange = { text = it },
                    placeHolder = "Editor",
                )
            }
            BlackButton(text = "Leave", modifier = Modifier.onFocusChanged { leaveFocused.set(it.isFocused) }) {}
        }
        LaunchedEffect(Unit) {
            requester.requestFocus()
            ready.set(true)
        }
    }

    @Composable
    private fun SourceWaitPreference() {
        // Render the actual active setting, not a lookalike slider with test-only wiring.
        val item = com.lagradost.cloudstream3.ui.settings.SettingsPlayerScreen.getPreferences()
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceGroup>()
            .flatMap { it.preferenceItems }
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceItem.SliderPreference>()
            .single { it.preference.key() == "source_startup_wait_seconds" }
        com.mihon.presentation.settings.PreferenceScreen(items = listOf(item))
        SideEffect { ready.set(true) }
    }

    @Composable
    private fun RetryPreference() {
        com.mihon.presentation.settings.widget.EditTextPreferenceWidget(
            title = "Retry preference", subtitle = null, icon = null, value = "Original",
            onConfirm = { applied.incrementAndGet() >= 2 },
        )
        SideEffect { ready.set(true) }
    }

    @Composable
    private fun ApplyDialog() {
        var showing by remember { mutableStateOf(true) }
        if (showing) SingleSelectDialog(
            title = "Settings Apply",
            entries = mapOf(1 to "First", 2 to "Second"),
            selectedKey = 1,
            confirmText = "Apply",
            dismissText = "Cancel",
            dismiss = { showing = false },
            confirm = { applied.incrementAndGet(); showing = false },
            iconProvider = { _, _ ->
                val view = LocalView.current
                SideEffect { dialogView = view; ready.set(true) }
            },
            applyShortcut = true,
        )
    }

    @Composable
    private fun OtherDialog(mode: String) {
        AlertDialog(
            onDismissRequest = {},
            text = {
                val view = LocalView.current
                SideEffect { dialogView = view; ready.set(true) }
                if (mode == "disabled") DialogApplyEffect(enabled = false) { applied.incrementAndGet() }
                Text("Not an enabled Apply action")
            },
            confirmButton = { WhiteButton(text = "Login") { unrelated.incrementAndGet() } },
        )
    }
}
