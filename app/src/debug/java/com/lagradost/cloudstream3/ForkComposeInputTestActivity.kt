package com.lagradost.cloudstream3

import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
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
class ForkComposeInputTestActivity : FragmentActivity() {
    val ready = AtomicBoolean(false)
    val applied = AtomicInteger(0)
    val unrelated = AtomicInteger(0)
    val leaveFocused = AtomicBoolean(false)
    var dialogView: View? = null
    var nativeDialog: android.app.Dialog? = null
    var nativeRoot: View? = null
    private var editorFocus: FocusRequester? = null
    fun focusEditor() { editorFocus?.requestFocus() }
    val typing: Boolean get() = ComposeTextFocus.isTyping
    val keyboardVisible: Boolean get() = ViewCompat.getRootWindowInsets(window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "tv"
        if (mode == "player") {
            // Host the real playback fragment for mapped-key instrumentation.
            ready.set(true)
            return
        }
        if (mode == "native-login") {
            val api = object : com.lagradost.cloudstream3.syncproviders.AuthAPI() {
                override val name = "Navigation fixture"
                override val inAppLoginRequirement = com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement(
                    username = true, email = intent.getBooleanExtra("email", true), server = true, password = true)
                override val createAccountUrl = if (intent.getBooleanExtra("create", false)) "https://example.invalid" else null
            }
            com.lagradost.cloudstream3.ui.settings.SettingsAccount.showAppLogin(
                this, com.lagradost.cloudstream3.syncproviders.PlainAuthRepo(api))
            return
        }
        if (mode == "native-input") {
            val root = layoutInflater.inflate(intent.getIntExtra("layout", R.layout.bottom_input_dialog), null)
            com.lagradost.cloudstream3.utils.TvTextInputNavigation.prepare(root, dialog = true)
            nativeRoot = root
            val builder = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogCustom).setView(root)
            if (intent.getIntExtra("layout", 0) == R.layout.lock_pin_dialog) {
                builder.setNegativeButton(android.R.string.cancel) { _, _ -> }
            }
            nativeDialog = builder.show()
            return
        }
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
                            "selection" -> SelectionDialog()
                            "source-wait" -> SourceLoadingPreference("source_startup_wait_seconds")
                            "source-count" -> SourceLoadingPreference("source_auto_start_count")
                            "source-collection" -> SourceCollectionPreferences()
                            "playback-diagnostics" -> PlaybackDiagnosticsPreference()
                            "disabled", "unrelated" -> OtherDialog(mode)
                            else -> Editor(mode == "settings-search")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        nativeDialog?.dismiss()
        nativeDialog = null
        nativeRoot = null
        super.onDestroy()
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
    private fun SelectionDialog() {
        var visible by remember { mutableStateOf(true) }
        if (!visible) return
        val entries = linkedMapOf(1 to "First", 2 to "Second")
        val confirm = if (intent.getBooleanExtra("confirm", false)) "Apply" else null
        val dismiss = if (intent.getBooleanExtra("dismiss", false)) "Cancel" else null
        if (intent.getBooleanExtra("multi", false)) {
            MultiSelectDialog(
                entries = entries, selectedKeys = emptySet<Int>(),
                confirmText = confirm, dismissText = dismiss,
                dismiss = { visible = false },
                confirm = { applied.set(it.size); visible = false },
            )
        } else {
            SingleSelectDialog(
                entries = entries, selectedKey = 1,
                confirmText = confirm, dismissText = dismiss,
                dismiss = { visible = false },
                confirm = { applied.set(it); visible = false },
            )
        }
    }

    @Composable
    private fun SourceLoadingPreference(key: String) {
        // Render the actual active setting, not a lookalike slider with test-only wiring.
        val item = com.lagradost.cloudstream3.ui.settings.SettingsPlayerScreen.getPreferences()
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceGroup>()
            .flatMap { it.preferenceItems }
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceItem.SliderPreference>()
            .single { it.preference.key() == key }
        com.mihon.presentation.settings.PreferenceScreen(items = listOf(item))
        SideEffect { ready.set(true) }
    }

    @Composable
    private fun SourceCollectionPreferences() {
        val items = com.lagradost.cloudstream3.ui.settings.SettingsPlayerScreen.getPreferences()
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceGroup>()
            .flatMap { it.preferenceItems }
            .filter {
                when (it) {
                    is com.mihon.presentation.settings.Preference.PreferenceItem.SliderPreference ->
                        it.preference.key() == "source_auto_start_wait_seconds"
                    is com.mihon.presentation.settings.Preference.PreferenceItem.SwitchPreference ->
                        it.preference.key() == "remember_series_source"
                    else -> false
                }
            }
        com.mihon.presentation.settings.PreferenceScreen(items = items)
        SideEffect { ready.set(true) }
    }

    @Composable
    private fun PlaybackDiagnosticsPreference() {
        val item = com.lagradost.cloudstream3.ui.settings.SettingsPlayerScreen.getPreferences()
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceGroup>()
            .flatMap { it.preferenceItems }
            .filterIsInstance<com.mihon.presentation.settings.Preference.PreferenceItem.SwitchPreference>()
            .single { it.preference.key() == "playback_diagnostics" }
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
