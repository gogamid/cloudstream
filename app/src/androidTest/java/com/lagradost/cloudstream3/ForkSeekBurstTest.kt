package com.lagradost.cloudstream3

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.ErrorEvent
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.ui.player.CSPlayerLoading
import com.lagradost.cloudstream3.ui.player.IPlayer
import com.lagradost.cloudstream3.ui.player.PlayerAttachedEvent
import com.lagradost.cloudstream3.ui.player.PlayerSeekSettings
import com.lagradost.cloudstream3.ui.player.PlayerView
import com.lagradost.cloudstream3.ui.player.FullScreenPlayer
import com.lagradost.cloudstream3.ui.player.RemoteButtonMappings
import com.lagradost.cloudstream3.ui.player.SettingsSeekPlayer
import com.lagradost.cloudstream3.ui.settings.Globals
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Media3/app-main-loop contract, not a video-codec or low-memory benchmark. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ForkSeekBurstTest {
    // FragmentTransaction requires a public, non-inner class with a no-arg constructor.
    class TvPlayerFragment : FullScreenPlayer() {
        override fun pickLayout(): Int = R.layout.fragment_player_tv
    }

    private fun main(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try { block() } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
    }

    private fun read(block: () -> Boolean): Boolean {
        var result = false
        main { result = block() }
        return result
    }

    @Test fun subtitleTimingMappingsAccumulateWithoutSeekingOrResumingVideo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = preferences.getStringSet(RemoteButtonMappings.KEY, null)?.toSet()
        val earlier = KeyEvent.KEYCODE_7
        val later = KeyEvent.KEYCODE_8
        RemoteButtonMappings.save(context, earlier, RemoteButtonMappings.Action.SUBTITLES_EARLIER)
        RemoteButtonMappings.save(context, later, RemoteButtonMappings.Action.SUBTITLES_LATER)
        try {
            Fixture().use { fixture ->
                ActivityScenario.launch<ForkComposeInputTestActivity>(
                    Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "player")
                ).use { scenario ->
                    lateinit var fragment: FullScreenPlayer
                    scenario.onActivity { activity ->
                        fragment = FullScreenPlayer()
                        activity.supportFragmentManager.beginTransaction()
                            .replace(android.R.id.content, fragment).commitNow()
                        fragment.player = fixture.player
                    }
                    fixture.await("Playback window did not gain focus") {
                        fixture.player.getSubtitleOffset() == 0L &&
                            fragment.view?.hasWindowFocus() == true &&
                            CommonActivity.keyEventListener != null &&
                            !com.lagradost.cloudstream3.utils.ComposeTextFocus.isTyping
                    }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    fun press(key: Int, repeat: Int = 0) {
                        // Fullscreen relayout can transfer focus between main-loop turns.
                        // Wait and dispatch atomically; do not bypass the player's focus guard.
                        fixture.await("Playback window did not regain focus for key $key") {
                            if (fragment.activity?.window?.decorView?.hasWindowFocus() != true) {
                                return@await false
                            }
                            val now = SystemClock.uptimeMillis()
                            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                                assertTrue(CommonActivity.keyEventListener?.invoke(
                                    KeyEvent(now, now, action, key, repeat) to false
                                ) == true)
                            }
                            true
                        }
                    }
                    var position = 0L
                    main { position = fixture.nativePlayer.currentPosition }
                    repeat(3) { press(earlier) }
                    main { assertEquals(300L, fixture.player.getSubtitleOffset()) }
                    press(earlier, repeat = 1) // A held-key repeat must not count as another press.
                    main { assertEquals(300L, fixture.player.getSubtitleOffset()) }
                    repeat(4) { press(later) }
                    main { assertEquals(-100L, fixture.player.getSubtitleOffset()) }
                    main { fixture.player.setSubtitleOffset(275L) } // Also respect dialog-set offsets.
                    repeat(3) { press(later) }
                    main {
                        assertEquals(-25L, fixture.player.getSubtitleOffset())
                        assertEquals(position, fixture.nativePlayer.currentPosition)
                        assertFalse(fixture.nativePlayer.playWhenReady)
                        assertEquals(0, fixture.seeks.get())
                    }
                }
            }
        } finally {
            assertTrue(preferences.edit().apply {
                if (previous == null) remove(RemoteButtonMappings.KEY)
                else putStringSet(RemoteButtonMappings.KEY, previous)
            }.commit())
        }
    }

    @Test fun pausePlayAnimationSettlesToTheCurrentPlaybackIcon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, ForkComposeInputTestActivity::class.java)
            .putExtra("mode", "player-icon")
        ActivityScenario.launch<ForkComposeInputTestActivity>(intent).use { scenario ->
            var playing = false
            lateinit var view: PlayerView
            lateinit var icon: ImageView
            scenario.onActivity { activity ->
                view = PlayerView(activity)
                val backend = view.player
                view.player = object : IPlayer by backend {
                    override fun getIsPlaying() = playing
                }
                view.hasPipModeSupport = false
                view.setBackgroundColor(Color.BLACK)
                icon = ImageView(activity)
                val size = (128 * activity.resources.displayMetrics.density).toInt()
                view.addView(icon, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
                view.playerPausePlay = icon
                activity.setContentView(view)
            }
            try {
                for ((isPlaying, resource, name) in listOf(
                    Triple(true, R.drawable.netflix_pause, "playing-pause-icon"),
                    Triple(false, R.drawable.netflix_play, "paused-play-icon")
                )) {
                    scenario.onActivity {
                        playing = isPlaying
                        view.updateIsPlaying(
                            if (isPlaying) CSPlayerLoading.IsPaused else CSPlayerLoading.IsPlaying,
                            if (isPlaying) CSPlayerLoading.IsPlaying else CSPlayerLoading.IsPaused
                        )
                    }
                    Thread.sleep(500) // Both vector animations last 200 ms.
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val actual = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
                        val expected = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
                        icon.drawable.setBounds(0, 0, icon.width, icon.height)
                        icon.drawable.draw(Canvas(actual))
                        ContextCompat.getDrawable(activity, resource)!!.apply {
                            setBounds(0, 0, icon.width, icon.height)
                            draw(Canvas(expected))
                        }
                        try {
                            assertTrue("Animation did not settle to the current $name", actual.sameAs(expected))
                        } finally {
                            actual.recycle()
                            expected.recycle()
                        }
                    }
                    val directory = File(context.getExternalFilesDir(null), "source-settings").apply { mkdirs() }
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    try {
                        File(directory, "$name.png").outputStream().use {
                            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally {
                        screenshot.recycle()
                    }
                }
            } finally {
                scenario.onActivity { view.release() }
            }
        }
    }

    private inner class Fixture(durationSeconds: Int = 20) : AutoCloseable {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val file = File.createTempFile("seek-burst-", ".wav", context.cacheDir)
        private val error = AtomicReference<Throwable?>()
        val seeks = AtomicInteger()
        lateinit var player: CS3IPlayer
        lateinit var nativePlayer: Player
        init {
            // Silent PCM: no network, media server, video codec or storage permission.
            val bytes = 8_000 * 2 * durationSeconds
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes).array()
            file.outputStream().use { it.write(header); it.write(ByteArray(bytes)) }
            try {
                main {
                    player = CS3IPlayer(tvSeekEnabled = { true })
                    player.initCallbacks({ event ->
                        if (event is ErrorEvent) error.set(event.error)
                        if (event is PlayerAttachedEvent && event.player is Player) {
                            nativePlayer = event.player as Player
                            nativePlayer.addListener(object : Player.Listener {
                                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                                    if (reason == Player.DISCONTINUITY_REASON_SEEK) seeks.incrementAndGet()
                                }
                            })
                        }
                    }, null)
                    load()
                }
                ready()
                main { seeks.set(0) }
            } catch (error: Throwable) {
                close()
                throw error
            }
        }
        fun load(sameEpisode: Boolean = false, autoPlay: Boolean? = false) {
            player.loadPlayer(context, sameEpisode = sameEpisode, link = null,
                data = ExtractorUri(Uri.fromFile(file), "Seek fixture"), startPosition = 10_000,
                subtitles = emptySet(), subtitle = null, autoPlay = autoPlay, preview = false)
        }
        fun await(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < end) {
                error.get()?.let { throw AssertionError("Local Media3 fixture failed", it) }
                if (read(condition)) return
                Thread.sleep(25)
            }
            // Capture before ActivityScenario cleanup replaces the failing window.
            runCatching {
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                val directory = File(context.getExternalFilesDir(null), "source-settings").apply { mkdirs() }
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    automation.executeShellCommand("dumpsys window")
                ).use { input ->
                    File(directory, "playback-timeout-windows.txt").outputStream().use { input.copyTo(it) }
                }
                val screenshot = automation.takeScreenshot()
                try {
                    File(directory, "playback-timeout.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    screenshot.recycle()
                }
            }.exceptionOrNull()?.let { android.util.Log.e("ForkSeekBurstTest", "Timeout capture failed", it) }
            fail(message)
        }
        fun ready() {
            await("Local fixture did not become READY") { player.isPlaybackReady() }
            Thread.sleep(100) // Let the normal offline initial-resume re-seek settle.
            await("Initial resume did not settle") { player.isPlaybackReady() }
        }
        override fun close() {
            if (::player.isInitialized) main { player.release(); player.releaseCallbacks() }
            file.delete()
        }
    }

    private fun withSeekSettings(block: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val values = mapOf(
            context.getString(R.string.android_tv_interface_off_seek_key) to 5,
            context.getString(R.string.android_tv_interface_on_seek_key) to 10,
            context.getString(R.string.double_tap_seek_time_key) to 7
        )
        val previous = values.keys.associateWith { preferences.all[it] }
        assertTrue(preferences.edit().apply { values.forEach { (key, value) -> putInt(key, value) } }.commit())
        try { block() } finally {
            assertTrue(preferences.edit().apply {
                previous.forEach { (key, value) -> if (value == null) remove(key) else putInt(key, value as Int) }
            }.commit())
        }
    }

    @Test fun diagnosticsCanBeEnabledAndDisabledDuringRealPlaybackWithoutReload() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val key = "playback_diagnostics"
        val original = preferences.all[key] as? Boolean
        fun logs(): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "logcat -d --pid=${android.os.Process.myPid()} -s CSPlaybackDebug:I '*:S'"
            )
        ).bufferedReader().use { it.readText() }
        try {
            assertTrue(preferences.edit().putBoolean(key, false).commit())
            Fixture().use { f ->
                val before = logs()
                main { f.nativePlayer.seekTo(7_000) }
                f.ready()
                assertEquals(before, logs())
                assertTrue(preferences.edit().putBoolean(key, true).commit())
                main { f.nativePlayer.seekTo(3_000) }
                f.ready()
                val enabled = logs()
                assertTrue(enabled.length > before.length)
                assertTrue(enabled.contains("bufferedMs="))
                assertFalse(enabled.contains("file://"))
                assertFalse(enabled.contains("seek-burst-"))
                assertTrue(preferences.edit().putBoolean(key, false).commit())
                main { f.nativePlayer.seekTo(8_000) }
                f.ready()
                assertEquals(enabled, logs())
                main {
                    assertFalse(f.player.getPlayWhenReady())
                    assertEquals(8_000.0, (f.player.getPosition() ?: -1).toDouble(), 100.0)
                }
            }
        } finally {
            assertTrue(preferences.edit().apply {
                if (original == null) remove(key) else putBoolean(key, original)
            }.commit())
        }
    }

    @Test fun heldTvSeekKeysAccelerateButNewPressesAndPhoneInputDoNot() = withSeekSettings {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val layoutKey = context.getString(R.string.app_layout_key)
        val previousLayout = preferences.all[layoutKey] as? Int
        val previousMappings = preferences.getStringSet(RemoteButtonMappings.KEY, null)?.toSet()
        assertTrue(preferences.edit().putInt(layoutKey, 2).commit())
        context.updateTv()
        RemoteButtonMappings.save(context, KeyEvent.KEYCODE_7, RemoteButtonMappings.Action.SEEK_FORWARD)
        RemoteButtonMappings.save(context, KeyEvent.KEYCODE_8, RemoteButtonMappings.Action.SEEK_BACK)
        RemoteButtonMappings.save(context, KeyEvent.KEYCODE_9, RemoteButtonMappings.Action.CONTROLS)
        try {
            Fixture(durationSeconds = 600).use { f ->
                ActivityScenario.launch<ForkComposeInputTestActivity>(
                    Intent(context, ForkComposeInputTestActivity::class.java).putExtra("mode", "player")
                ).use { scenario ->
                    // Match GeneratorPlayer's TV layout; the base fragment uses phone controls.
                    val fragment = TvPlayerFragment()
                    val settings = PlayerSeekSettings(context)
                    scenario.onActivity { activity ->
                        activity.supportFragmentManager.beginTransaction()
                            .replace(android.R.id.content, fragment).commitNow()
                        fragment.player = f.player
                        // This fixture owns the backend callbacks; reflect its already-ready,
                        // paused state in the newly attached host instead of leaving it buffering.
                        fragment.currentPlayerStatus = CSPlayerLoading.IsPaused
                        f.player.setSeekDurationProvider {
                            settings.amountMs(Globals.isLayout(Globals.TV or Globals.EMULATOR), fragment.isUIShowing())
                        }
                    }
                    f.await("Playback window did not gain focus") { fragment.view?.hasWindowFocus() == true }
                    fun down(key: Int, heldMs: Long = 0, repeat: Int = 0) {
                        val now = SystemClock.uptimeMillis()
                        assertTrue(CommonActivity.keyEventListener?.invoke(
                            KeyEvent(now - heldMs, now, KeyEvent.ACTION_DOWN, key, repeat) to false
                        ) == true)
                    }
                    fun finishAt(expectedMs: Long, expectedSeeks: Int) {
                        main { f.player.finishInteractiveSeek() }
                        f.await("Seek did not settle") {
                            !f.player.isInteractiveSeeking() && f.seeks.get() == expectedSeeks &&
                                (f.nativePlayer.playbackState == Player.STATE_READY ||
                                    f.nativePlayer.playbackState == Player.STATE_ENDED)
                        }
                        main {
                            assertEquals(expectedMs.toDouble(), f.nativePlayer.currentPosition.toDouble(), 100.0)
                            assertEquals(expectedSeeks, f.seeks.get())
                            assertFalse(f.player.getPlayWhenReady())
                            assertFalse(f.player.isActuallyPlaying())
                        }
                    }
                    main {
                        // Hidden controls: 5s base, then 5 + 10 + 20 + 40 + 80 + 80s.
                        down(KeyEvent.KEYCODE_DPAD_RIGHT)
                        for ((index, heldMs) in listOf(1_999L, 2_000L, 4_000L, 6_000L, 8_000L, 9_000L).withIndex()) {
                            down(KeyEvent.KEYCODE_DPAD_RIGHT, heldMs, index + 1)
                        }
                        val now = SystemClock.uptimeMillis()
                        CommonActivity.keyEventListener?.invoke(
                            KeyEvent(now - 9_000, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0) to false)
                        // A fresh down is normal speed even if its timestamps span a long interval.
                        down(KeyEvent.KEYCODE_DPAD_RIGHT, heldMs = 8_000)
                        down(KeyEvent.KEYCODE_8) // Mapped rewind: -5s, then -10s.
                        down(KeyEvent.KEYCODE_8, heldMs = 2_000, repeat = 1)
                        down(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, heldMs = 4_000, repeat = 1) // +20s
                        down(KeyEvent.KEYCODE_MEDIA_REWIND, heldMs = 6_000, repeat = 1) // -40s
                        down(KeyEvent.KEYCODE_7, heldMs = 8_000, repeat = 1) // Mapped forward: +80s.
                        assertEquals(0, f.seeks.get())
                        assertTrue(f.player.isInteractiveSeeking())
                    }
                    finishAt(300_000, 1)
                    main {
                        down(KeyEvent.KEYCODE_9) // Show controls and focus their playback button.
                        assertTrue(fragment.isUIShowing())
                        assertTrue(fragment.playerPausePlay?.requestFocus() == true)
                        down(KeyEvent.KEYCODE_DPAD_LEFT) // Independent shown setting: -10s.
                        down(KeyEvent.KEYCODE_DPAD_LEFT, heldMs = 2_000, repeat = 1) // -20s.
                    }
                    finishAt(270_000, 2)
                    main {
                        repeat(3) { down(KeyEvent.KEYCODE_7, heldMs = 8_000, repeat = it + 1) }
                    }
                    finishAt(599_999, 3) // Clamp at the end without overflowing the duration.
                    main {
                        repeat(4) { down(KeyEvent.KEYCODE_8, heldMs = 8_000, repeat = it + 1) }
                    }
                    finishAt(0, 4)
                    main {
                        assertTrue(preferences.edit().putInt(layoutKey, 0).commit())
                        context.updateTv()
                        down(KeyEvent.KEYCODE_7, heldMs = 8_000, repeat = 1) // Phone mappings remain single-press.
                        assertFalse(f.player.isInteractiveSeeking())
                        down(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, heldMs = 8_000, repeat = 1)
                    }
                    finishAt(7_000, 5) // Phone's own 7s setting, not an accelerated TV amount.
                }
            }
        } finally {
            assertTrue(preferences.edit().apply {
                if (previousLayout == null) remove(layoutKey) else putInt(layoutKey, previousLayout)
                if (previousMappings == null) remove(RemoteButtonMappings.KEY)
                else putStringSet(RemoteButtonMappings.KEY, previousMappings)
            }.commit())
            context.updateTv()
        }
    }

    @Test fun mappedRewindUsesSavedFiveSecondAmountNotThirty() = withSeekSettings {
        Fixture().use { f ->
            main {
                f.player.handleEvent(CSPlayerEvent.SeekBack)
                assertTrue(f.player.isInteractiveSeeking())
                f.player.finishInteractiveSeek()
            }
            f.ready()
            main {
                assertEquals(1, f.seeks.get())
                assertEquals(5_000.0, (f.player.getPosition() ?: -1).toDouble(), 100.0)
                assertFalse(f.player.getPlayWhenReady())
            }
        }
    }

    @Test fun shownTenSecondSettingIsLiveAndIndependentOfHiddenFiveSeconds() = withSeekSettings {
        Fixture().use { f ->
            val settings = PlayerSeekSettings(InstrumentationRegistry.getInstrumentation().targetContext)
            var shown = false
            main {
                f.player.setSeekDurationProvider { settings.amountMs(isTv = true, controlsVisible = shown) }
                val controls = SettingsSeekPlayer(f.nativePlayer,
                    { settings.amountMs(isTv = true, controlsVisible = shown) },
                    f.player.currentRelativeSeek())
                assertEquals(5_000L, controls.getSeekBackIncrement())
                shown = true
                assertEquals(10_000L, controls.getSeekBackIncrement())
                assertEquals(10_000L, controls.getSeekForwardIncrement())
                f.player.handleEvent(CSPlayerEvent.SeekBack)
                f.player.finishInteractiveSeek()
            }
            f.ready()
            main {
                assertEquals(0.0, (f.player.getPosition() ?: -1).toDouble(), 100.0)
                assertFalse(f.player.getPlayWhenReady())
            }
        }
    }

    @Test fun nativeControlsFollowPhoneAmountAndDoNotBypassTvBatcher() = withSeekSettings {
        Fixture().use { f ->
            val settings = PlayerSeekSettings(InstrumentationRegistry.getInstrumentation().targetContext)
            main {
                val controls = SettingsSeekPlayer(f.nativePlayer,
                    { settings.amountMs(isTv = false, controlsVisible = true) },
                    f.player.currentRelativeSeek())
                assertEquals(7_000L, controls.getSeekBackIncrement())
                assertEquals((f.nativePlayer as ExoPlayer).audioSessionId, controls.audioSessionId ?: -1)
                controls.seekBack()
                assertTrue(f.player.isInteractiveSeeking())
                assertEquals(0, f.seeks.get())
                f.player.finishInteractiveSeek()
            }
            f.ready()
            main {
                assertEquals(1, f.seeks.get())
                assertEquals(3_000.0, (f.player.getPosition() ?: -1).toDouble(), 100.0)
                assertFalse(f.player.getPlayWhenReady())
            }
        }
    }

    @Test fun oldNativeControlsCannotSeekOrResumeReplacementBackend() = withSeekSettings {
        Fixture().use { f ->
            lateinit var controls: SettingsSeekPlayer
            main {
                controls = SettingsSeekPlayer(f.nativePlayer, { 5_000L }, f.player.currentRelativeSeek())
                f.player.release()
                f.load()
            }
            f.ready()
            main {
                f.seeks.set(0)
                controls.seekBack()
                assertFalse(f.player.isInteractiveSeeking())
            }
            Thread.sleep(600)
            main {
                assertEquals(0, f.seeks.get())
                assertEquals(10_000.0, (f.player.getPosition() ?: -1).toDouble(), 100.0)
                assertFalse(f.player.getPlayWhenReady())
            }
        }
    }

    @Test fun rapidRewindCommitsOneRealSeekAndPreservesPause() {
        Fixture().use { f ->
            var expected = 0L
            main {
                expected = (f.player.getPosition() ?: 0) - 5_000
                repeat(100) { f.player.seekTime(-50) }
                assertTrue(f.player.isInteractiveSeeking())
                assertFalse(f.player.getPlayWhenReady())
                assertEquals(0, f.seeks.get())
            }
            f.await("Burst did not commit") { !f.player.isInteractiveSeeking() }
            f.ready()
            main {
                assertEquals(1, f.seeks.get())
                assertEquals(expected.toDouble(), (f.player.getPosition() ?: -1).toDouble(), 100.0)
                assertFalse(f.player.getPlayWhenReady())
                assertFalse(f.player.isActuallyPlaying())
            }
        }
    }

    @Test fun temporaryPauseDoesNotClobberPlayIntentAndExplicitPauseWins() {
        Fixture().use { f ->
            main { f.player.handleEvent(CSPlayerEvent.Play) }
            f.await("Audio clock did not start") { f.player.isActuallyPlaying() }
            main {
                repeat(50) { f.player.seekTime(-50) }
                assertTrue(f.player.isInteractiveSeeking())
                assertTrue(f.player.getPlayWhenReady())
                assertFalse(f.player.isActuallyPlaying())
                assertEquals(0, f.seeks.get())
                f.player.handleEvent(CSPlayerEvent.Pause)
                assertFalse(f.player.isInteractiveSeeking())
                assertFalse(f.player.getPlayWhenReady())
            }
            Thread.sleep(600)
            main {
                assertEquals(1, f.seeks.get())
                assertFalse(f.player.getPlayWhenReady())
                assertFalse(f.player.isActuallyPlaying())
            }
        }
    }

    @Test fun releasedBurstCannotSeekOrResumeReplacementBackend() {
        Fixture().use { f ->
            main {
                repeat(50) { f.player.seekTime(-50) }
                f.player.release()
                f.load()
            }
            f.ready()
            main { f.seeks.set(0) }
            Thread.sleep(600)
            main {
                assertEquals(0, f.seeks.get())
                assertEquals(10_000.0, (f.player.getPosition() ?: -1).toDouble(), 100.0)
                assertFalse(f.player.getPlayWhenReady())
                assertFalse(f.player.isInteractiveSeeking())
            }
        }
    }

    @Test fun waitingForLateSourceRetainsPlayIntentButUiAndNativePauseStillWin() {
        // Native pause is the same path used by PlayerView/MediaSession controls.
        for (pause in listOf("none", "ui", "native")) {
            Fixture().use { f ->
                main { f.player.handleEvent(CSPlayerEvent.Play) }
                f.await("Audio clock did not start") { f.player.isActuallyPlaying() }
                main {
                    f.player.stop()
                    assertEquals(Player.STATE_IDLE, f.nativePlayer.playbackState)
                    assertFalse(f.player.isActuallyPlaying())
                    assertTrue(f.player.getPlayWhenReady())
                }
                // Give the stopped backend's callbacks a turn before the late source arrives.
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                main {
                    when (pause) {
                        "ui" -> f.player.handleEvent(CSPlayerEvent.Pause)
                        "native" -> f.nativePlayer.pause()
                    }
                    assertEquals(pause == "none", f.player.getPlayWhenReady())
                    f.load(sameEpisode = true, autoPlay = true)
                }
                f.ready()
                main {
                    assertEquals(pause == "none", f.player.getPlayWhenReady())
                    assertEquals(pause == "none", f.nativePlayer.playWhenReady)
                }
            }
        }
    }
}
