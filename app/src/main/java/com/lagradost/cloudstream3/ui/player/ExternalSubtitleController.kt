package com.lagradost.cloudstream3.ui.player

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.text.Subtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Only subtitle data is fetched/decoded here. This class never owns or reloads a video player. */
@OptIn(UnstableApi::class)
internal class ExternalSubtitleController(
    private val positionMs: () -> Long?,
    private val render: (List<Cue>) -> Unit,
    private val failed: (SubtitleData, Throwable) -> Unit,
    private val loaded: () -> Unit,
) {
    private data class Parsed(val track: SubtitleData, val bytes: ByteArray,
                              val subtitle: Subtitle, val lines: List<SubtitleCue>)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Rapid presses may cancel a blocking HTTP/file read. Bound worker overlap;
    // cancellation is checked between chunks and stale results can never publish.
    private val loader = Dispatchers.IO.limitedParallelism(2)
    private val handler = Handler(Looper.getMainLooper())
    private val session = SubtitleSelectionSession()
    private val cache = ExternalSubtitleCache<Parsed>()
    private val failedIds = mutableSetOf<String>()
    private var job: Job? = null
    private val loadingSources = ConcurrentHashMap<Long, DataSource>()

    private fun cancelLoad() {
        job?.cancel()
        // Close canceled network/file reads off main, without waiting for a read
        // timeout or blocking the video/UI thread. The loader's use block also
        // closes its stream; DataSource implementations tolerate repeated close.
        loadingSources.entries.toList().forEach { (token, source) ->
            if (loadingSources.remove(token, source)) scope.launch(Dispatchers.IO) {
                runCatching { source.close() }
            }
        }
    }
    private var active: Parsed? = null
    private var offsetMs = 0L
    private var enabled = true
    private var lastEventIndex: Int? = null
    var currentTrack: SubtitleData? = null
        private set

    private val ticker = object : Runnable {
        override fun run() {
            redraw()
            if (enabled && active != null) handler.postDelayed(this, 100)
        }
    }

    fun setRenderingEnabled(value: Boolean) {
        enabled = value
        restartTicker()
    }

    private fun restartTicker() {
        handler.removeCallbacks(ticker)
        lastEventIndex = null
        if (enabled && active != null) handler.post(ticker)
    }

    fun redraw(force: Boolean = false) {
        val parsed = active ?: return
        val position = positionMs() ?: return
        val timeUs = ExternalSubtitleLimits.positionUs(position, offsetMs)
        val next = parsed.subtitle.getNextEventTimeIndex(timeUs)
        if (force || next != lastEventIndex) {
            lastEventIndex = next
            render(parsed.subtitle.getCues(timeUs))
        }
    }

    fun setOffset(value: Long) { offsetMs = value; redraw(force = true) }
    fun hasFailed(id: String): Boolean = id in failedIds
    fun cues(): List<SubtitleCue> = active?.lines ?: emptyList()

    fun select(track: SubtitleData, factory: DataSource.Factory, retry: Boolean = false): Boolean {
        if (retry) failedIds.remove(track.getId())
        if (track.getId() in failedIds) return false
        if (currentTrack?.getId() == track.getId() && session.requestedId == track.getId()) return true
        currentTrack = track
        val token = session.request(track.getId())
        cancelLoad()
        cache[track.getId()]?.let { parsed ->
            if (session.commit(token)) {
                active = parsed
                restartTicker()
                redraw(force = true)
                loaded()
            }
            return true
        }
        launchLoad(track, token) {
            val source = factory.createDataSource()
            loadingSources[token] = source
            try {
                val context = kotlinx.coroutines.currentCoroutineContext()
                DataSourceInputStream(source, DataSpec.Builder().setUri(track.getFixedUrl()).build()).use { input ->
                    ExternalSubtitleLimits.read(input) { context.ensureActive() }
                }
            } finally {
                loadingSources.remove(token, source)
            }
        }
        return true
    }

    /** Encoding/parser-style changes reparse the cached subtitle, not the video or network. */
    fun reparse() {
        val parsed = active ?: return
        if (currentTrack?.getId() != parsed.track.getId()) return
        val token = session.request(parsed.track.getId())
        cancelLoad()
        cache.clear()
        launchLoad(parsed.track, token) { parsed.bytes }
    }

    private fun launchLoad(track: SubtitleData, token: Long, bytes: suspend () -> ByteArray) {
        job = scope.launch {
            try {
                val parsed = withContext(loader) {
                    val data = bytes()
                    ensureActive()
                    require(data.isNotEmpty()) { "Empty subtitle file" }
                    // External timing is applied by the video-clock lookup, never baked
                    // into cues. Offset changes therefore need no seek or re-download.
                    val parser = CustomDecoder(Format.Builder().setSampleMimeType(track.mimeType).build(), applyOffset = false)
                    val subtitle = parser.parseToLegacySubtitle(data, 0, data.size)
                    ensureActive()
                    val lines = parser.subtitleCuesSnapshot()
                    require(subtitle.eventTimeCount > 0 && lines.isNotEmpty()) { "Unable to decode subtitle" }
                    require(lines.size <= ExternalSubtitleLimits.MAX_CUES) { "Too many subtitle cues" }
                    Parsed(track, data, subtitle, lines)
                }
                if (session.commit(token)) {
                    failedIds.remove(track.getId())
                    cache.put(track.getId(), parsed, parsed.bytes.size)
                    active = parsed
                    restartTicker()
                    redraw(force = true)
                    loaded()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (session.fail(token)) {
                    failedIds.add(track.getId())
                    currentTrack = active?.track
                    failed(track, error)
                }
            }
        }
    }

    fun stop(clearCache: Boolean = false) {
        session.request(null)
        cancelLoad()
        job = null
        handler.removeCallbacks(ticker)
        active = null
        currentTrack = null
        lastEventIndex = null
        if (clearCache) { cache.clear(); failedIds.clear() }
        render(emptyList())
    }
}
