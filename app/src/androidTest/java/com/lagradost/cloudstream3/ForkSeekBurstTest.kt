package com.lagradost.cloudstream3

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.ui.player.CS3IPlayer
import com.lagradost.cloudstream3.ui.player.CSPlayerEvent
import com.lagradost.cloudstream3.ui.player.ErrorEvent
import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.ui.player.PlayerAttachedEvent
import com.lagradost.cloudstream3.ui.player.PlayerSeekSettings
import com.lagradost.cloudstream3.ui.player.SettingsSeekPlayer
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

    private inner class Fixture : AutoCloseable {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val file = File.createTempFile("seek-burst-", ".wav", context.cacheDir)
        private val error = AtomicReference<Throwable?>()
        val seeks = AtomicInteger()
        lateinit var player: CS3IPlayer
        lateinit var nativePlayer: Player
        init {
            // Twenty seconds of silent PCM: no network, media server, video
            // codec, storage permission, audio content or large fixture needed.
            val bytes = 8_000 * 2 * 20
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
        fun load() {
            player.loadPlayer(context, sameEpisode = false, link = null,
                data = ExtractorUri(Uri.fromFile(file), "Seek fixture"), startPosition = 10_000,
                subtitles = emptySet(), subtitle = null, autoPlay = false, preview = false)
        }
        fun await(message: String, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < end) {
                error.get()?.let { throw AssertionError("Local Media3 fixture failed", it) }
                if (read(condition)) return
                Thread.sleep(25)
            }
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
}
