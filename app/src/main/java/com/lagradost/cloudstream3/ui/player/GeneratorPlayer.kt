package com.lagradost.cloudstream3.ui.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.animation.addListener
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.text.toSpanned
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerNotificationManager.EXTRA_INSTANCE_ID
import androidx.media3.ui.PlayerNotificationManager.MediaDescriptionAdapter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.databinding.DialogOnlineSubtitlesBinding
import com.lagradost.cloudstream3.databinding.FragmentPlayerBinding
import com.lagradost.cloudstream3.databinding.PlayerSelectSourceAndSubsBinding
import com.lagradost.cloudstream3.databinding.PlayerSelectTracksBinding
import com.lagradost.cloudstream3.isAnimeOp
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.isLiveStream
import com.lagradost.cloudstream3.isMovieType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeNullable
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.subtitleProviders
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup
import com.lagradost.cloudstream3.ui.player.CS3IPlayer.Companion.preferredAudioTrackLanguage
import com.lagradost.cloudstream3.ui.player.CustomDecoder.Companion.updateForcedEncoding
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.ui.player.source_priority.LinkSource
import com.lagradost.cloudstream3.ui.player.source_priority.ProfileSettings
import com.lagradost.cloudstream3.ui.player.source_priority.QualityDataHelper
import com.lagradost.cloudstream3.ui.player.source_priority.QualityProfileDialog
import com.lagradost.cloudstream3.ui.result.ACTION_CLICK_DEFAULT
import com.lagradost.cloudstream3.ui.result.EpisodeAdapter
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.ui.result.ResultFragment.bindLogo
import com.lagradost.cloudstream3.ui.result.ResultViewModel2
import com.lagradost.cloudstream3.ui.result.SyncViewModel
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageTagIETF
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragmentDialog2
import com.lagradost.cloudstream3.utils.AppContextUtils.getShortSeasonText
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.AppContextUtils.sortSubs
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToLanguageName
import com.lagradost.cloudstream3.utils.SubtitleHelper.languages
import com.lagradost.cloudstream3.utils.UIHelper.clipboardHelper
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.hideProgress
import com.lagradost.cloudstream3.utils.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.utils.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.utils.UIHelper.showProgress
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream3.utils.downloader.DownloadUtils.getImageBitmapFromUrl
import com.lagradost.cloudstream3.utils.setText
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.videoskip.VideoSkipStamp
import com.lagradost.safefile.SafeFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Serializable
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
class GeneratorPlayer : FullScreenPlayer() {
    companion object {
        const val NOTIFICATION_ID = 2326
        const val CHANNEL_ID = 7340
        const val STOP_ACTION = "stopcs3"

        private val generators = ConcurrentHashMap<String, VideoGenerator<*>>()
        fun newInstance(
            generator: VideoGenerator<*>,
            index: Int,
            syncData: HashMap<String, String>? = null
        ): Bundle {
            Log.i(TAG, "newInstance = $syncData")
            val uuid = UUID.randomUUID().toString()
            generators[uuid] = generator
            return Bundle().apply {
                putString("uuid", uuid)
                putInt("index", index)
                if (syncData != null) putSerializable("syncData", syncData)
            }
        }

        val subsProviders = subtitleProviders
        val subsProvidersIsActive
            get() = subsProviders.isNotEmpty()
    }


    private var limitTitle = 0
    private var showTitle = false
    private var showName = false
    private var showResolution = false
    private var showMediaInfo = false

    private lateinit var viewModel: PlayerGeneratorViewModel //by activityViewModels()
    private lateinit var sync: SyncViewModel

    private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
    private var currentSelectedSubtitles: SubtitleData? = null
    private val subtitleToggle = SubtitleToggleSelection()
    private val currentMeta: Any? get() = viewModel.state.generatorState?.meta
    private val nextMeta: Any? get() = viewModel.state.generatorState?.nextMeta

    private var isPlayerActive: AtomicBoolean = AtomicBoolean(false)
    private var isNextEpisode: Boolean = false // this is used to reset the watch time

    private var preferredAutoSelectSubtitles: String? = null // null means do nothing, "" means none
    private val allMeta: List<ResultEpisode>?
        get() = viewModel.state.generatorState?.allMeta?.filterIsInstance<ResultEpisode>()
            ?.map { episode ->
                // Refresh all the episodes watch duration
                getViewPos(episode.id)?.let { data ->
                    episode.copy(position = data.position, duration = data.duration)
                } ?: episode
            }

    private fun seriesPreferenceKey(): String? {
        val episode = currentMeta as? ResultEpisode ?: return null
        if (episode.tvType.isMovieType() || episode.tvType.isLiveStream() || episode.tvType == TvType.NSFW) return null
        return "${DataStoreHelper.currentAccount}/series_source_preferences/${episode.parentId}"
    }

    private fun seriesPreferences(): SeriesSourcePreferences = seriesPreferenceKey()?.let {
        com.lagradost.cloudstream3.CloudStreamApp.getKey<SeriesSourcePreferences>(it)
    } ?: SeriesSourcePreferences()

    private fun videoIdentity(link: VideoLink): SeriesSourcePreferences.Video? = link.first?.let {
        SeriesSourcePreferences.Video(it.source, it.name, it.quality, it.type.name,
            runCatching { java.net.URI(it.url).host?.lowercase(java.util.Locale.ROOT) }.getOrNull())
    }

    private fun rememberVideo(link: VideoLink) {
        val key = seriesPreferenceKey() ?: return
        val identity = videoIdentity(link) ?: return
        setKey(key, seriesPreferences().copy(video = identity))
    }

    private fun preferredSeriesSubtitle(subtitles: Set<SubtitleData>): SubtitleData? {
        val preferred = seriesPreferences().subtitle ?: return null
        return sortSubs(subtitles).firstOrNull(preferred::matches)
    }

    private fun setSubtitles(subtitle: SubtitleData?, userInitiated: Boolean): Boolean {
        subtitleToggle.remember(player.getCurrentPreferredSubtitle())
        subtitleToggle.remember(subtitle)
        if (userInitiated) seriesPreferenceKey()?.let { key ->
            setKey(key, seriesPreferences().withSubtitleSelection(
                subtitle?.let(SeriesSourcePreferences.Subtitle::from)
            ))
        }
        // Explicit Off also matters before a first auto-selected track arrives.
        if (userInitiated) {
            val subtitleLanguageTagIETF = if (subtitle == null) {
                "" // -> No Subtitles
            } else {
                subtitle.getIETF_tag()
            }

            if (subtitleLanguageTagIETF != null) {
                Log.i(TAG, "Set SUBTITLE_AUTO_SELECT_KEY to '$subtitleLanguageTagIETF'")
                setKey(SUBTITLE_AUTO_SELECT_KEY, subtitleLanguageTagIETF)
                preferredAutoSelectSubtitles = subtitleLanguageTagIETF
            }
        }

        currentSelectedSubtitles = subtitle
        //Log.i(TAG, "setSubtitles = $subtitle")
        return player.setPreferredSubtitles(subtitle, userInitiated)
    }

    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {
        viewModel.addSubtitles(subtitles.toSet())
    }

    override fun onTracksInfoChanged() {
        val tracks = player.getVideoTracks()
        playerBinding?.playerTracksBtt?.isVisible =
            tracks.allVideoTracks.size > 1 || tracks.allAudioTracks.size > 1
        // Only set the preferred language if it is available.
        // Otherwise, it may give some users audio track init failed!
        if (tracks.allAudioTracks.any { it.language == preferredAudioTrackLanguage }) {
            player.setPreferredAudioTrack(preferredAudioTrackLanguage)
        }
        updatePlayerInfo()
    }

    override fun playerStatusChanged() {
        super.playerStatusChanged()
        if (player.getIsPlaying()) {
            viewModel.forceClearCache = false
        }
    }

    override fun selectSubtitleLanguage(language: String) {
        val ctx = context ?: return
        val subtitle = SubtitleLanguageSelection.next(
            sortSubs(viewModel.state.subtitles), language,
            currentSelectedSubtitles ?: player.getCurrentPreferredSubtitle()
        )
        if (subtitle == null) {
            val label = ctx.getString(if (language == "ru")
                R.string.remote_russian_subtitles else R.string.remote_english_subtitles)
            android.widget.Toast.makeText(ctx, ctx.getString(R.string.remote_subtitle_missing, label),
                android.widget.Toast.LENGTH_SHORT).show()
            return // Do not disable or replace the current subtitles on a miss.
        }
        // Follow the same selection path as the subtitle menu, including language preference.
        // Existing URL/file tracks may require a player reload; no subtitle-provider search.
        player.setActiveSubtitles(viewModel.state.subtitles)
        if (setSubtitles(subtitle, true)) {
            player.saveData()
            player.reloadPlayer(ctx) // Preserves playback position and play/pause state.
        }
        android.widget.Toast.makeText(ctx, ctx.getString(R.string.remote_subtitle_selected, subtitle.name.trim()),
            android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun toggleSubtitles() {
        val ctx = context ?: return
        if (!isAdded || view == null || sourceSessionInstance != viewModel.state.instance ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isDialogOpen()) return
        // The backend includes pending selections and rolls back failures. The
        // fragment's cached menu choice can be stale after an automatic failure.
        val current = player.getCurrentPreferredSubtitle()
        val embeddedIds = player.getVideoTracks().allTextTracks.mapNotNull { it.id }.toSet()
        val available = subtitleToggle.available(sortSubs(viewModel.state.subtitles), embeddedIds)
        val preferred = preferredSeriesSubtitle(viewModel.state.subtitles)
            ?: getAutoSelectSubtitle(viewModel.state.subtitles, settings = true, downloads = true)
        val target = subtitleToggle.target(current, available, preferred)
        if (current == null && target == null) {
            android.widget.Toast.makeText(ctx, R.string.remote_no_subtitles, android.widget.Toast.LENGTH_SHORT).show()
            return // No search, preference changes or video reload on a miss.
        }
        player.setActiveSubtitles(viewModel.state.subtitles)
        if (setSubtitles(target, true)) {
            player.saveData()
            player.reloadPlayer(ctx) // Only for a backend requiring reload; CS3 returns false.
        }
        val message = target?.let { ctx.getString(R.string.remote_subtitle_selected, it.name.trim()) }
            ?: ctx.getString(R.string.remote_subtitles_off)
        android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun selectNextVideoSource() {
        val ctx = context ?: return
        if (!isAdded || view == null || sourceSessionInstance != viewModel.state.instance ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isDialogOpen()) return
        val current = currentSelectedLink
        val next = sourcePlan.nextManual(sourceCandidates(), current)
        if (next == null) {
            android.widget.Toast.makeText(ctx, R.string.remote_source_missing, android.widget.Toast.LENGTH_SHORT).show()
            return // Keep playing; late arrivals can be selected by a later press.
        }
        val intent = if (current == null) true else player.getPlayWhenReady()
        rememberVideo(next)
        // This is a user choice, not automatic error failover: no five-second
        // startup deadline. The normal manual chooser uses this same load path.
        loadLink(next, sameEpisode = current != null, manualSelection = true, autoPlay = intent)
        android.widget.Toast.makeText(ctx, ctx.getString(R.string.remote_source_selected,
            next.first?.name ?: next.second?.name.orEmpty()), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun noSubtitles(): Boolean {
        return setSubtitles(null, true)
    }

    private fun getPos(): Long {
        val durPos = getViewPos(viewModel.state.generatorState?.id) ?: return 0L
        if (durPos.duration == 0L) return 0L
        if (durPos.position * 100L / durPos.duration > 95L) {
            return 0L
        }
        return durPos.position
    }

    private var currentVerifyLink: Job? = null

    private fun loadExtractorJob(extractorLink: ExtractorLink?) {
        currentVerifyLink?.cancel()

        extractorLink?.let { link ->
            currentVerifyLink = ioSafe {
                if (link.extractorData != null) {
                    getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)
                }
            }
        }
    }

    // https://github.com/androidx/media/blob/main/libraries/ui/src/main/java/androidx/media3/ui/PlayerNotificationManager.java#L1517
    private fun createBroadcastIntent(
        action: String,
        context: Context,
        instanceId: Int
    ): PendingIntent {
        val intent: Intent = Intent(action).setPackage(context.packageName)
        intent.putExtra(EXTRA_INSTANCE_ID, instanceId)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT

        return PendingIntent.getBroadcast(context, instanceId, intent, pendingFlags)
    }

    private var cachedPlayerNotificationManager: PlayerNotificationManager? = null

    private fun getMediaNotification(context: Context): PlayerNotificationManager {
        val cache = cachedPlayerNotificationManager
        if (cache != null) return cache
        return PlayerNotificationManager.Builder(
            context,
            NOTIFICATION_ID,
            CHANNEL_ID.toString()
        )
            .setChannelNameResourceId(R.string.player_notification_channel_name)
            .setChannelDescriptionResourceId(R.string.player_notification_channel_description)
            .setMediaDescriptionAdapter(object : MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return when (val meta = currentMeta) {
                        is ResultEpisode -> {
                            meta.headerName
                        }

                        is ExtractorUri -> {
                            meta.headerName ?: meta.name
                        }

                        else -> null
                    } ?: "Unknown"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    // Open the app without creating a new task to resume playback seamlessly
                    return PendingIntentCompat.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        0,
                        false
                    )
                }

                override fun getCurrentContentText(player: Player): CharSequence? {
                    return when (val meta = currentMeta) {
                        is ResultEpisode -> {
                            meta.name
                        }

                        is ExtractorUri -> {
                            if (meta.headerName == null) {
                                null
                            } else {
                                meta.name
                            }
                        }

                        else -> null
                    }
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    ioSafe {
                        val url = when (val meta = currentMeta) {
                            is ResultEpisode -> {
                                meta.poster
                            }

                            else -> null
                        }
                        // if we have a poster url try with it first
                        if (url != null) {
                            val urlBitmap = context.getImageBitmapFromUrl(url)
                            if (urlBitmap != null) {
                                callback.onBitmap(urlBitmap)
                                return@ioSafe
                            }
                        }

                        // retry several times with a preview in case the preview generator is slow
                        repeat(10) {
                            val preview = this@GeneratorPlayer.player.getPreview(0.5f)
                            if (preview != null) {
                                callback.onBitmap(preview)
                                return@repeat
                            }
                            delay(1000L)
                        }
                    }

                    // return null as we want to use the callback
                    return null
                }
            }).setCustomActionReceiver(object : PlayerNotificationManager.CustomActionReceiver {
                // we have to use a custom action for stop if we want to exit the player instead of just stopping playback
                override fun createCustomActions(
                    context: Context,
                    instanceId: Int
                ): MutableMap<String, NotificationCompat.Action> {
                    return mutableMapOf(
                        STOP_ACTION to NotificationCompat.Action(
                            R.drawable.baseline_stop_24,
                            @SuppressLint("PrivateResource")
                            context.getString(androidx.media3.ui.R.string.exo_controls_stop_description),
                            createBroadcastIntent(STOP_ACTION, context, instanceId)
                        )
                    )
                }

                override fun getCustomActions(player: Player): MutableList<String> {
                    return mutableListOf(STOP_ACTION)
                }

                override fun onCustomAction(player: Player, action: String, intent: Intent) {
                    when (action) {
                        STOP_ACTION -> {
                            exitPlayer()
                        }
                    }
                }
            })
            .setPlayActionIconResourceId(R.drawable.ic_baseline_play_arrow_24)
            .setPauseActionIconResourceId(R.drawable.netflix_pause)
            .setSmallIconResourceId(R.drawable.baseline_headphones_24)
            .setStopActionIconResourceId(R.drawable.baseline_stop_24)
            .setRewindActionIconResourceId(R.drawable.go_back_30)
            .setFastForwardActionIconResourceId(R.drawable.go_forward_30)
            .setNextActionIconResourceId(R.drawable.ic_baseline_skip_next_24)
            .setPreviousActionIconResourceId(R.drawable.baseline_skip_previous_24)
            .build().apply {
                setColorized(true) // Color
                setUseChronometer(true) // Seekbar

                // Don't show the prev episode button
                setUsePreviousAction(false)
                setUsePreviousActionInCompactView(false)

                // Don't show the next episode button
                setUseNextAction(false)
                setUseNextActionInCompactView(false)

                // Show the skip 30s in both modes
                setUseFastForwardAction(true)
                setUseFastForwardActionInCompactView(true)

                // Only show rewind in expanded
                setUseRewindAction(true)
                setUseFastForwardActionInCompactView(false)

                // Use custom stop action
                setUseStopAction(false)
            }
            .also { cachedPlayerNotificationManager = it }
    }

    override fun playerUpdated(player: Any?) {
        super.playerUpdated(player)

        // Cancel the notification when released
        if (player == null) {
            cachedPlayerNotificationManager?.setPlayer(null)
            cachedPlayerNotificationManager = null
            return
        }

        // setup the notification when starting the player
        if (player is ExoPlayer) {
            val ctx = context ?: return
            getMediaNotification(ctx).apply {
                setPlayer(player)
                mMediaSession?.platformToken?.let {
                    setMediaSessionToken(it)
                }
            }
        }
    }

    override fun onDownload(event: DownloadEvent) {
        super.onDownload(event)
        showDownloadProgress(event)
    }

    private fun showDownloadProgress(event: DownloadEvent) {
        activity?.runOnUiThread {
            playerBinding?.downloadedProgress?.apply {
                val indeterminate = event.totalBytes <= 0 || event.downloadedBytes <= 0
                isIndeterminate = indeterminate
                if (!indeterminate) {
                    max = (event.totalBytes / 1000).toInt()
                    progress = (event.downloadedBytes / 1000).toInt()
                }
            }
            playerBinding?.downloadedProgressText.setText(
                txt(
                    R.string.download_size_format,
                    android.text.format.Formatter.formatShortFileSize(
                        context,
                        event.downloadedBytes
                    ),
                    android.text.format.Formatter.formatShortFileSize(context, event.totalBytes)
                )
            )
            val downloadSpeed =
                android.text.format.Formatter.formatShortFileSize(context, event.downloadSpeed)
            playerBinding?.downloadedProgressSpeedText?.text =
                    // todo string fmt
                event.connections?.let { connections ->
                    "%s/s - %d Connections".format(downloadSpeed, connections)
                } ?: downloadSpeed

            // don't display when done
            playerBinding?.downloadedProgressSpeedText?.isGone =
                event.downloadedBytes != 0L && event.downloadedBytes - 1024 >= event.totalBytes
        }
    }

    private var sourceStartupJob: Job? = null
    private var sourceCollectionWaitJob: Job? = null
    private var deferredPlayerActionJob: Job? = null
    private var subtitleAutoSelectJob: Job? = null
    private val sourceOperations = PlaybackOperationGuard()
    private var cachedSortedSourceLinks: List<DisplayLink>? = null
    private var cachedSourceCandidates = emptyList<SourcePlaybackPlan.Candidate<VideoLink>>()
    private var sourcePlan = SourcePlaybackPlan<VideoLink>(::sourceAttemptKey)
    private var sourceSessionInstance = -1
    private var sourceCollectionStartedAt = 0L
    private var loggedFirstSources = false
    private var awaitingSourceFallback = false
    private var sourceResumePosition: Long? = null
    private var manuallySelectedSource = false
    private var sourcePlaybackStarted = false

    // URL identity is episode-local only; persistent preferences never contain it.
    private fun sourceAttemptKey(link: VideoLink): String = link.first?.let { "${it.type}:${it.url}" }
        ?: link.second?.uri.toString()

    private fun sourceCandidates(): List<SourcePlaybackPlan.Candidate<VideoLink>> {
        val sorted = viewModel.state.sortLinks(currentQualityProfile)
        if (sorted !== cachedSortedSourceLinks) {
            cachedSortedSourceLinks = sorted
            cachedSourceCandidates = sorted.map {
                SourcePlaybackPlan.Candidate(it.link, videoIdentity(it.link), it.priority, it.shouldUseLink)
            }
        }
        return cachedSourceCandidates
    }

    /** Serialize deferred errors/fallback with dialogs, pause, source changes and navigation. */
    private fun deferPlayerAction(action: () -> Unit): Boolean {
        if (!isAdded || view == null || sourceSessionInstance != viewModel.state.instance) return true
        if (!isDialogOpen() && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        val token = sourceOperations.token()
        val instance = viewModel.state.instance
        deferredPlayerActionJob?.cancel()
        deferredPlayerActionJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && sourceOperations.isCurrent(token) && viewModel.state.instance == instance) {
                delay(250)
                if (!isDialogOpen() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    action()
                    return@launch
                }
            }
        }
        return true
    }

    private fun scheduleSubtitleAutoSelection() {
        if (subtitleAutoSelectJob?.isActive == true || !isAdded || view == null) return
        val instance = viewModel.state.instance
        subtitleAutoSelectJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250) // Coalesce a burst of late subtitle/embedded-track callbacks.
            while (isActive && instance == viewModel.state.instance) {
                val started = sourcePlaybackStarted || (player as? CS3IPlayer)?.hasStartedPlayback() == true
                val ready = (player as? CS3IPlayer)?.isPlaybackReady() ?: started
                if (isPlayerActive.get() && started && ready && !awaitingSourceFallback && !isDialogOpen() &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    autoSelectSubtitles()
                    return@launch
                }
                delay(250)
            }
        }
    }

    private fun collectingSources() = viewModel.state.loading is Resource.Loading

    private fun prepareSourceSession() {
        if (sourceSessionInstance == viewModel.state.instance) return
        sourceSessionInstance = viewModel.state.instance
        subtitleToggle.clear()
        sourceCollectionStartedAt = SystemClock.elapsedRealtime()
        loggedFirstSources = false
        cachedSortedSourceLinks = null
        cachedSourceCandidates = emptyList()
        sourceCollectionWaitJob?.cancel()
        sourceCollectionWaitJob = null
        sourcePlan = SourcePlaybackPlan(::sourceAttemptKey)
        awaitingSourceFallback = false
        sourceResumePosition = null
        manuallySelectedSource = false
        sourcePlaybackStarted = false
        releasePlayer()
    }

    private fun maybeStartSource() {
        if (isPlayerActive.get()) return
        val instance = viewModel.state.instance
        if (sourceCollectionWaitJob?.isActive == true) return
        val preferences = seriesPreferences()
        sourceCollectionWaitJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && instance == viewModel.state.instance && !isPlayerActive.get()) {
                val candidates = sourceCandidates()
                val collecting = collectingSources()
                if (sourcePlan.ready(SystemClock.elapsedRealtime(), candidates, preferences,
                        collecting, viewModel.generator?.canSkipLoading == true,
                        QualityDataHelper.AUTO_SKIP_PRIORITY)) {
                    startPlayer()
                    return@launch
                }
                if (!collecting) {
                    startPlayer() // Also handles a genuinely empty, completed extraction.
                    return@launch
                }
                delay(250)
            }
        }
    }

    private fun watchSourceStartup(link: VideoLink) {
        if (sourcePlaybackStarted) return
        val preferenceKey = seriesPreferenceKey()
        val identity = videoIdentity(link)
        sourceStartupJob = viewLifecycleOwner.lifecycleScope.launch {
            val timeout = SourceStartupTimeout(automatic = !manuallySelectedSource)
            var playedMs = 0L
            var previous = SystemClock.elapsedRealtime()
            while (isActive && currentSelectedLink == link) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                // A suspended main thread/background interval is not startup time.
                val elapsed = (now - previous).coerceAtMost(250L)
                previous = now
                val actuallyStarted = (player as? CS3IPlayer)?.hasStartedPlayback() ?: player.getIsPlaying()
                if (actuallyStarted) sourcePlaybackStarted = true
                val eligible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    !isDialogOpen() && player.getPlayWhenReady() &&
                    (player as? CS3IPlayer)?.isInteractiveSeeking() != true
                val playingNow = (player as? CS3IPlayer)?.isActuallyPlaying() ?: player.getIsPlaying()
                if (eligible && playingNow) playedMs += elapsed
                if (playedMs >= 5_000L) {
                    // Learn a fallback without replacing the explicit manual choice.
                    if (preferenceKey != null && identity != null && preferenceKey == seriesPreferenceKey()) {
                        setKey(preferenceKey, seriesPreferences().copy(lastWorkingVideo = identity))
                    }
                    return@launch
                }
                if (timeout.tick(elapsed, eligible, playing = sourcePlaybackStarted)) {
                    // Penalize this family for this episode only; do not overwrite
                    // the manual preference or repeatedly retry its URL variants.
                    sourcePlan.failed(link, identity)
                    Log.i(TAG, "Automatic source startup exceeded 5 seconds without playback; trying next source")
                    nextMirror()
                    return@launch
                }
            }
        }
    }

    private fun loadLink(link: VideoLink?, sameEpisode: Boolean, manualSelection: Boolean? = null,
                         autoPlay: Boolean? = true) {
        if (link == null || !isAdded || view == null) return
        sourceOperations.advance()
        deferredPlayerActionJob?.cancel()
        subtitleAutoSelectJob?.cancel()
        sourceStartupJob?.cancel()
        sourceStartupJob = null
        val previousStarted = sourcePlaybackStarted || (player as? CS3IPlayer)?.hasStartedPlayback() == true
        if (!sameEpisode) sourceResumePosition = if (isNextEpisode) 0L else getPos()
        else if (previousStarted) sourceResumePosition = player.getPosition() ?: sourceResumePosition
        sourcePlaybackStarted = sameEpisode && currentSelectedLink == link && previousStarted
        sourcePlan.attempted(link)
        awaitingSourceFallback = false
        manuallySelectedSource = manualSelection ?: (sameEpisode && manuallySelectedSource)
        isPlayerActive.set(true)
        // manage UI
        binding?.playerLoadingOverlay?.isVisible = false
        val isTorrent =
            link.first?.type == ExtractorLinkType.MAGNET || link.first?.type == ExtractorLinkType.TORRENT

        playerBinding?.downloadHeader?.isVisible = false
        playerBinding?.downloadHeaderToggle?.isVisible = isTorrent
        if (!isLayout(PHONE)) {
            playerBinding?.downloadBothHeader?.isVisible = isTorrent
        }

        showDownloadProgress(DownloadEvent(0, 0, 0, null))

        // uiReset() // Removed due to UX

        currentSelectedLink = link
        //  setEpisodes(viewModel.getAllMeta() ?: emptyList())
        setPlayerDimen(null)
        setTitle()
        if (!sameEpisode)
            hasRequestedStamps = false

        loadExtractorJob(link.first)
        // load player
        context?.let { ctx ->
            val (url, uri) = link
            val subtitles = viewModel.state.subtitles
            player.loadPlayer(
                ctx,
                sameEpisode,
                url,
                uri,
                // Keep the resume target across failed startup attempts; a
                // buffering player's temporary zero must not become the next target.
                startPosition = sourceResumePosition,
                subtitles,
                if (seriesPreferences().subtitlesOff) null else
                    (if (sameEpisode) currentSelectedSubtitles else null) ?: preferredSeriesSubtitle(subtitles)
                    ?: getAutoSelectSubtitle(subtitles, settings = true, downloads = true),
                autoPlay = autoPlay,
                preview = true
            )
        }

        watchSourceStartup(link)
        scheduleSubtitleAutoSelection()
        if (!sameEpisode) {
            player.addTimeStamps(emptyList()) // clear stamps
            // Resets subtitle delay, as we watch some other content
            player.setSubtitleOffset(0)
        }
    }

    data class TempMetaData(
        var episode: Int? = null,
        var season: Int? = null,
        var name: String? = null,
        var imdbId: String? = null,
    )

    private fun getMetaData(): TempMetaData {
        val meta = TempMetaData()

        when (val newMeta = currentMeta) {
            is ResultEpisode -> {
                if (!newMeta.tvType.isMovieType()) {
                    meta.episode = newMeta.episode
                    meta.season = newMeta.season
                }
                meta.name = newMeta.headerName
            }

            is ExtractorUri -> {
                if (newMeta.tvType?.isMovieType() == false) {
                    meta.episode = newMeta.episode
                    meta.season = newMeta.season
                }
                meta.name = newMeta.headerName
            }
        }
        return meta
    }

    fun getName(entry: AbstractSubtitleEntities.SubtitleEntity, withLanguage: Boolean): String {
        if (entry.lang.isBlank() || !withLanguage) {
            return entry.name
        }
        val language = fromTagToLanguageName(entry.lang.trim()) ?: entry.lang
        return "$language ${entry.name}"
    }

    override fun openOnlineSubPicker(
        context: Context, loadResponse: LoadResponse?, dismissCallback: (() -> Unit)
    ) {
        val providers = subsProviders.toList()
        val isSingleProvider = subsProviders.size == 1

        val dialog = Dialog(context, R.style.DialogFullscreenPlayer)
        val binding =
            DialogOnlineSubtitlesBinding.inflate(LayoutInflater.from(context), null, false)
        com.lagradost.cloudstream3.utils.TvTextInputNavigation.prepare(binding.root, dialog = true)
        binding.subtitlesSearch.nextFocusDownId = R.id.subtitle_adapter
        dialog.setContentView(binding.root)
        com.lagradost.cloudstream3.utils.DialogApplyShortcut.install(dialog)
        fixSystemBarsPadding(binding.root)

        var currentSubtitles: List<AbstractSubtitleEntities.SubtitleEntity> = emptyList()
        var currentSubtitle: AbstractSubtitleEntities.SubtitleEntity? = null

        val layout = R.layout.sort_bottom_single_choice_double_text
        val arrayAdapter =
            object : ArrayAdapter<AbstractSubtitleEntities.SubtitleEntity>(dialog.context, layout) {
                fun setHearingImpairedIcon(
                    imageViewEnd: ImageView?, position: Int
                ) {
                    if (imageViewEnd == null) return
                    val isHearingImpaired =
                        currentSubtitles.getOrNull(position)?.isHearingImpaired ?: false

                    val drawableEnd = if (isHearingImpaired) {
                        ContextCompat.getDrawable(
                            context, R.drawable.ic_baseline_hearing_24
                        )?.apply {
                            setTint(
                                ContextCompat.getColor(
                                    context, R.color.textColor
                                )
                            )
                        }
                    } else null

                    imageViewEnd.setImageDrawable(drawableEnd)
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(layout, null)

                    val item = getItem(position)

                    val mainTextView = view.findViewById<TextView>(R.id.main_text)
                    val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text)
                    val drawableEnd = view.findViewById<ImageView>(R.id.drawable_end)

                    mainTextView?.text = item?.let { getName(it, false) }

                    val language =
                        item?.let { fromTagToLanguageName(it.lang) ?: it.lang } ?: ""
                    val providerSuffix =
                        if (isSingleProvider || item == null) "" else " · ${item.source}"
                    @SuppressLint("SetTextI18n")
                    secondaryTextView?.text = language + providerSuffix

                    setHearingImpairedIcon(drawableEnd, position)
                    return view
                }
            }

        dialog.show()
        binding.cancelBtt.setOnClickListener {
            dialog.dismissSafe()
        }

        binding.subtitleAdapter.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        binding.subtitleAdapter.adapter = arrayAdapter

        binding.subtitleAdapter.setOnItemClickListener { _, _, position, _ ->
            currentSubtitle = currentSubtitles.getOrNull(position) ?: return@setOnItemClickListener
        }

        var currentLanguageTagIETF: String = getAutoSelectLanguageTagIETF()


        fun setSubtitlesList(list: List<AbstractSubtitleEntities.SubtitleEntity>) {
            currentSubtitles = list
            arrayAdapter.clear()
            arrayAdapter.addAll(currentSubtitles)
        }

        val currentTempMeta = getMetaData()

        // bruh idk why it is not correct
        val color =
            ColorStateList.valueOf(context.colorFromAttribute(androidx.appcompat.R.attr.colorAccent))
        binding.searchLoadingBar.progressTintList = color
        binding.searchLoadingBar.indeterminateTintList = color

        observeNullable(viewModel.currentSubtitleYear) {
            // When year is changed search again
            binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
            binding.yearBtt.text = it?.toString() ?: txt(R.string.none).asString(context)
        }

        binding.yearBtt.setOnClickListener {
            val none = txt(R.string.none).asString(context)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val earliestYear = 1900

            val years = (currentYear downTo earliestYear).toList()
            val options = listOf(none) + years.map {
                it.toString()
            }

            val selectedIndex = viewModel.currentSubtitleYear.value
                ?.let {
                    // + 1 since none also takes a space
                    years.indexOf(it) + 1
                }
                ?.takeIf { it >= 0 } ?: 0

            activity?.showDialog(
                options,
                selectedIndex,
                txt(R.string.year).asString(context),
                true, {
                }, { index ->
                    viewModel.setSubtitleYear(years.getOrNull(index - 1))
                }
            )
        }

        binding.subtitlesSearch.setOnQueryTextListener(object :
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchLoadingBar.show()
                ioSafe {
                    val search =
                        SubtitleSearch(
                            query = query ?: return@ioSafe,
                            imdbId = loadResponse?.getImdbId(),
                            tmdbId = loadResponse?.getTMDbId()?.toInt(),
                            malId = loadResponse?.getMalId()?.toInt(),
                            aniListId = loadResponse?.getAniListId()?.toInt(),
                            epNumber = currentTempMeta.episode,
                            seasonNumber = currentTempMeta.season,
                            lang = currentLanguageTagIETF.ifBlank { null },
                            year = viewModel.currentSubtitleYear.value
                        )

                    // TODO Make ui a lot better, like search with tabs
                    val results = providers.amap {
                        when (val response = Resource.fromResult(it.search(search))) {
                            is Resource.Success -> {
                                response.value
                            }

                            is Resource.Loading -> {
                                emptyList()
                            }

                            is Resource.Failure -> {
                                showToast(response.errorString)
                                emptyList()
                            }
                        }
                    }
                    val max = results.maxOfOrNull { it.size } ?: return@ioSafe

                    // very ugly
                    val items = ArrayList<AbstractSubtitleEntities.SubtitleEntity>()
                    val arrays = results.size
                    for (index in 0 until max) {
                        for (i in 0 until arrays) {
                            items.add(results[i].getOrNull(index) ?: continue)
                        }
                    }

                    // ugly ik
                    activity?.runOnUiThread {
                        setSubtitlesList(items)
                        binding.searchLoadingBar.hide()
                    }
                }

                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })

        binding.searchFilter.setOnClickListener { view ->
            val languagesTagName =
                languages
                    .map { Pair(it.IETF_tag, it.nameNextToFlagEmoji()) }
                    .sortedBy {
                        it.second.substringAfter("\u00a0").lowercase()
                    } // name ignoring flag emoji
            val (langTagsIETF, langNames) = languagesTagName.unzip()

            activity?.showDialog(
                langNames,
                langTagsIETF.indexOf(currentLanguageTagIETF),
                view?.context?.getString(R.string.subs_subtitle_languages)
                    ?: return@setOnClickListener,
                true,
                { }) { index ->
                currentLanguageTagIETF = langTagsIETF[index]
                binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
            }
        }

        binding.applyBtt.setOnClickListener {
            val currentSubtitle = currentSubtitle
            if (currentSubtitle == null) {
                dialog.dismissSafe()
                return@setOnClickListener
            }

            val api = providers.firstOrNull { it.idPrefix == currentSubtitle.idPrefix }
            if (api == null) {
                dialog.dismissSafe()
                return@setOnClickListener
            }

            binding.applyBtt.showProgress()
            ioSafe {
                val apiResource =
                    Resource.fromResult(api.resource(currentSubtitle))
                binding.applyBtt.hideProgress()
                when (apiResource) {
                    is Resource.Success -> {
                        val subtitles = apiResource.value.getSubtitles().map { resource ->
                            SubtitleData(
                                originalName = resource.name ?: getName(
                                    currentSubtitle,
                                    true
                                ),
                                nameSuffix = "",
                                url = resource.url,
                                origin = resource.origin,
                                mimeType = resource.url.toSubtitleMimeType(),
                                headers = currentSubtitle.headers,
                                languageCode = currentSubtitle.lang
                            )
                        }
                        if (subtitles.isEmpty()) {
                            showToast(R.string.no_subtitles)
                            return@ioSafe
                        }
                        dialog.dismissSafe()
                        runOnMainThread {
                            addAndSelectSubtitles(*subtitles.toTypedArray())
                        }
                    }

                    is Resource.Failure -> {
                        showToast(apiResource.errorString)
                    }

                    is Resource.Loading -> {
                        // not possible
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            dismissCallback.invoke()
        }

        dialog.show()
        binding.subtitlesSearch.setQuery(currentTempMeta.name, true)
        //TODO: Set year text from currently loaded movie on Player
        //dialog.subtitles_search_year?.setText(currentTempMeta.year)
    }

    private fun openSubPicker() {
        try {
            subsPathPicker.launch(
                arrayOf(
                    "text/plain",
                    "text/str",
                    "application/octet-stream",
                    MimeTypes.TEXT_UNKNOWN,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.APPLICATION_TTML,
                    MimeTypes.APPLICATION_MP4VTT,
                    MimeTypes.APPLICATION_SUBRIP,
                )
            )
        } catch (e: Exception) {
            logError(e)
        }
    }

    @MainThread
    private fun addAndSelectSubtitles(
        vararg subtitleData: SubtitleData
    ) {
        if (subtitleData.isEmpty() || !isAdded || view == null) return
        val ctx = context ?: return
        val selectedSubtitle = subtitleData.first()
        viewModel.addSubtitles(subtitleData.toSet())

        // this is used instead of observe(viewModel._currentSubs), because observe is too slow
        player.setActiveSubtitles(viewModel.state.subtitles)

        // External text is selected independently; never rebuild the video here.
        if (setSubtitles(selectedSubtitle, true)) {
            player.saveData()
            player.reloadPlayer(ctx)
        }

        selectSourceDialog?.dismissSafe()
        selectSourceDialog = null

        showToast(
            String.format(ctx.getString(R.string.player_loaded_subtitles), selectedSubtitle.name),
            Toast.LENGTH_LONG
        )
    }

    // Open file picker
    private val subsPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            safe {
                // It lies, it can be null if file manager quits.
                if (uri == null) return@safe
                val ctx = context ?: CloudStreamApp.context ?: return@safe
                // RW perms for the path
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                val file = SafeFile.fromUri(ctx, uri)
                val fileName = file?.name()
                println("Loaded subtitle file. Selected URI path: $uri - Name: $fileName")
                // DO NOT REMOVE THE FILE EXTENSION FROM NAME, IT'S NEEDED FOR MIME TYPES
                val name = fileName ?: uri.toString()

                val subtitleData = SubtitleData(
                    name,
                    "",
                    uri.toString(),
                    SubtitleOrigin.DOWNLOADED_FILE,
                    name.toSubtitleMimeType(),
                    emptyMap(),
                    null
                )

                addAndSelectSubtitles(subtitleData)
            }
        }

    /** Will toast both when an error is found and when a subtitle is selected,
     * so only use from a user click and not a background process */
    private fun addFirstSub(query: SubtitleSearch) =
        viewLifecycleOwner.lifecycleScope.launch {
            val instance = viewModel.state.instance
            // async should not have a race condition if they are on the same group
            var hasSelectASubtitle = false

            // first come first served with these subtitles
            // we might want to change it to prefer different sources when used multiple times,
            // however caching might make this random after the first click too
            subsProviders.toList().amap { provider ->
                val success = when (val result = Resource.fromResult(
                    provider.search(
                        query = query
                    )
                )) {
                    is Resource.Failure -> {
                        // scope might cancel, so we do an extra check
                        if (this.isActive) {
                            showToast("${provider.idPrefix}${result.errorString}")
                        }
                        return@amap
                    }

                    is Resource.Loading -> {
                        // unreachable
                        return@amap
                    }

                    is Resource.Success -> {
                        result.value
                    }
                }

                // try to add every subtitle until we have added a new subtitle file
                for (subtitleEntry in success) {
                    if (hasSelectASubtitle || !this.isActive) {
                        break
                    }

                    val subtitleResources = provider.resource(subtitleEntry).getOrNull() ?: continue

                    val subtitles = subtitleResources.getSubtitles().map { resource ->
                        SubtitleData(
                            originalName = resource.name ?: getName(subtitleEntry, true),
                            nameSuffix = "",
                            url = resource.url,
                            origin = resource.origin,
                            mimeType = resource.url.toSubtitleMimeType(),
                            headers = subtitleEntry.headers,
                            languageCode = subtitleEntry.lang,
                        )
                    }

                    // checks for both a race condition and if any of the subs generated is new
                    if (this.isActive && viewModel.state.instance == instance &&
                        !viewModel.state.subtitles.containsAll(subtitles) && !hasSelectASubtitle) {
                        hasSelectASubtitle = true
                        runOnMainThread {
                            if (isActive && viewModel.state.instance == instance) {
                                addAndSelectSubtitles(*subtitles.toTypedArray())
                            }
                        }
                        break
                    }
                }
            }
            // maybe better error here?
            if (!hasSelectASubtitle && this.isActive && viewModel.state.instance == instance) {
                showToast(R.string.no_subtitles)
            }
        }


    override fun showMirrorsDialogue() {
        if (selectSourceDialog?.isShowing == true || !isAdded || view == null) return
        val dialogToken = sourceOperations.token()
        val dialogInstance = viewModel.state.instance
        fun isCurrentDialog() = isAdded && view != null && dialogInstance == viewModel.state.instance &&
            sourceOperations.isCurrent(dialogToken)
        try {
            currentSelectedSubtitles = player.getCurrentPreferredSubtitle()
            //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
            context?.let { ctx ->
                // This chooser need not pause video just to change text tracks.
                val currentSubtitles = sortSubs(viewModel.state.subtitles)

                val sourceDialog = Dialog(ctx, R.style.DialogFullscreenPlayer)
                val binding =
                    PlayerSelectSourceAndSubsBinding.inflate(LayoutInflater.from(ctx), null, false)
                sourceDialog.setContentView(binding.root)

                fixSystemBarsPadding(binding.root)
                selectSourceDialog = sourceDialog

                sourceDialog.show()
                com.lagradost.cloudstream3.utils.DialogApplyShortcut.install(sourceDialog)
                val providerList = binding.sortProviders
                val subtitleList = binding.sortSubtitles
                val subtitleOptionList = binding.sortSubtitlesOptions

                val loadFromFileFooter: TextView =
                    layoutInflater.inflate(R.layout.sort_bottom_footer_add_choice, null) as TextView

                loadFromFileFooter.text = ctx.getString(R.string.player_load_subtitles)
                loadFromFileFooter.setOnClickListener {
                    openSubPicker()
                }
                subtitleList.addFooterView(loadFromFileFooter)

                var shouldDismiss = true

                binding.subtitleSettingsBtt.setOnClickListener {
                    safe {
                        val subtitlesFragment = SubtitlesFragmentDialog2()
                        subtitlesFragment.systemBarsAddPadding = true
                        subtitlesFragment.show(this.parentFragmentManager, "SubtitleSettings")
                    }
                }

                fun dismiss() {
                    activity?.hideSystemUI()
                }

                if (subsProvidersIsActive) {
                    val currentLoadResponse = viewModel.state.generatorState?.response

                    val loadFromOpenSubsFooter: TextView = layoutInflater.inflate(
                        R.layout.sort_bottom_footer_add_choice, null
                    ) as TextView

                    loadFromOpenSubsFooter.text =
                        ctx.getString(R.string.player_load_subtitles_online)

                    loadFromOpenSubsFooter.setOnClickListener {
                        shouldDismiss = false
                        sourceDialog.dismissSafe(activity)
                        selectSourceDialog = null
                        openOnlineSubPicker(it.context, currentLoadResponse) {
                            dismiss()
                        }
                    }
                    subtitleList.addFooterView(loadFromOpenSubsFooter)

                    // subs from 1 button here
                    val metadata = getMetaData()
                    val queryName = metadata.name ?: currentLoadResponse?.name
                    if (queryName != null) {
                        val currentLanguageTagIETF: String = getAutoSelectLanguageTagIETF()
                        val loadFromFirstSubsFooter: TextView = layoutInflater.inflate(
                            R.layout.sort_bottom_footer_add_choice, null
                        ) as TextView

                        loadFromFirstSubsFooter.text =
                            ctx.getString(R.string.player_load_one_subtitle_online)

                        loadFromFirstSubsFooter.setOnClickListener {
                            sourceDialog.dismissSafe(activity)
                            selectSourceDialog = null
                            showToast(R.string.loading)
                            addFirstSub(
                                SubtitleSearch(
                                    query = queryName,
                                    imdbId = currentLoadResponse?.getImdbId(),
                                    tmdbId = currentLoadResponse?.getTMDbId()?.toInt(),
                                    malId = currentLoadResponse?.getMalId()?.toInt(),
                                    aniListId = currentLoadResponse?.getAniListId()?.toInt(),
                                    epNumber = metadata.episode,
                                    seasonNumber = metadata.season,
                                    lang = currentLanguageTagIETF.ifBlank { null },
                                    year = viewModel.currentSubtitleYear.value
                                )
                            )
                        }
                        subtitleList.addFooterView(loadFromFirstSubsFooter)
                    }
                }

                var sourceIndex = 0
                var sourceSelectionTouched = false
                var startSource = 0
                // Filtered and sorted links
                var currentHiddenFooter: View? = null
                var filteredLinks: List<DisplayLink> = emptyList()

                fun refreshLinks(qualityProfile: Int) {
                    val currentLinkUsed = currentSelectedLink
                    // Always display current linkFooter
                    val sortedLinks = viewModel.state.sortLinks(qualityProfile)

                    filteredLinks = sortedLinks.filter { it.shouldUseLink || it.link == currentLinkUsed }

                    if (sortedLinks.isEmpty()) {
                        sourceDialog.findViewById<LinearLayout>(R.id.sort_sources_holder)?.isGone =
                            true
                    } else {
                        startSource = filteredLinks.indexOfFirst { it.link == currentLinkUsed }
                        sourceIndex = startSource

                        val sourcesArrayAdapter =
                            ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

                        sourcesArrayAdapter.addAll(filteredLinks.map { displayLink ->
                            val (link, uri) = displayLink.link
                            val name = link?.name ?: uri?.name ?: "NULL"
                            "$name ${Qualities.getStringByInt(link?.quality)}"
                        })

                        providerList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                        providerList.adapter = sourcesArrayAdapter
                        providerList.setSelection(sourceIndex)
                        providerList.setItemChecked(sourceIndex, true)

                        providerList.setOnItemClickListener { _, _, which, _ ->
                            sourceSelectionTouched = true
                            sourceIndex = which
                            providerList.setItemChecked(which, true)
                        }

                        providerList.setOnItemLongClickListener { _, _, position, _ ->
                            filteredLinks.getOrNull(position)?.link?.first?.url?.let {
                                clipboardHelper(
                                    txt(R.string.video_source),
                                    it
                                )
                            }
                            true
                        }

                        val hiddenLinks = sortedLinks.size - filteredLinks.size
                        providerList.removeFooterView(currentHiddenFooter)

                        if (hiddenLinks > 0) {
                            val hiddenLinksFooter: TextView = layoutInflater.inflate(
                                R.layout.sort_bottom_footer_add_choice, null
                            ) as TextView

                            providerList.addFooterView(hiddenLinksFooter, null, false)
                            currentHiddenFooter = hiddenLinksFooter

                            val hiddenLinksText =
                                ctx.resources.getQuantityString(R.plurals.links_hidden, hiddenLinks)
                                    .format(hiddenLinks)
                            hiddenLinksFooter.text = hiddenLinksText
                            hiddenLinksFooter.setCompoundDrawables(null, null, null, null)
                            hiddenLinksFooter.setTypeface(null, Typeface.ITALIC)
                        }
                    }
                }

                refreshLinks(currentQualityProfile)

                sourceDialog.setOnDismissListener {
                    if (shouldDismiss) dismiss()
                    selectSourceDialog = null
                }


                val subsArrayAdapter =
                    ArrayAdapter<Spanned>(ctx, R.layout.sort_bottom_single_choice)
                subsArrayAdapter.add(ctx.getString(R.string.no_subtitles).html())

                val subtitlesGrouped =
                    currentSubtitles.groupBy { it.originalName }.map { (key, value) ->
                        key to value.sortedBy { it.nameSuffix.toIntOrNull() ?: 0 }
                    }.toMap()
                val subtitlesGroupedList = subtitlesGrouped.entries.toList()

                val subtitles = subtitlesGrouped.map { it.key.html() }

                val subtitleGroupIndexStart =
                    subtitlesGrouped.keys.indexOf(currentSelectedSubtitles?.originalName) + 1
                var subtitleGroupIndex = subtitleGroupIndexStart

                val subtitleOptionIndexStart =
                    subtitlesGrouped[currentSelectedSubtitles?.originalName]?.indexOfFirst { it.nameSuffix == currentSelectedSubtitles?.nameSuffix }
                        ?: 0
                var subtitleOptionIndex = subtitleOptionIndexStart

                subsArrayAdapter.addAll(subtitles)

                subtitleList.adapter = subsArrayAdapter
                subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                subtitleList.setSelection(subtitleGroupIndex)
                subtitleList.setItemChecked(subtitleGroupIndex, true)

                val subsOptionsArrayAdapter =
                    ArrayAdapter<Spanned>(ctx, R.layout.sort_bottom_single_choice)

                subtitleOptionList.adapter = subsOptionsArrayAdapter
                subtitleOptionList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                fun updateSubtitleOptionList() {
                    subsOptionsArrayAdapter.clear()

                    val subtitleOptions =
                        subtitlesGroupedList
                            .getOrNull(subtitleGroupIndex - 1)?.value?.map { subtitle ->
                                val nameSuffix = subtitle.nameSuffix.html()
                                nameSuffix.ifBlank {
                                    when (subtitle.origin) {
                                        SubtitleOrigin.URL -> txt(R.string.subtitles_from_online)
                                        SubtitleOrigin.DOWNLOADED_FILE -> txt(R.string.downloaded)
                                        SubtitleOrigin.EMBEDDED_IN_VIDEO -> txt(R.string.subtitles_from_embedded)
                                    }.asString(ctx).toSpanned()
                                }
                            }
                            ?: emptyList()

                    // Show nothing if there is nothing to select
                    val shouldHide = subtitleOptions.size < 2
                    subtitleOptionList.isGone = shouldHide // Make it easier to click
                    if (shouldHide) return

                    subsOptionsArrayAdapter.addAll(subtitleOptions)

                    subtitleOptionList.setSelection(subtitleOptionIndex)
                    subtitleOptionList.setItemChecked(subtitleOptionIndex, true)
                }

                updateSubtitleOptionList()

                subtitleList.setOnItemClickListener { _, _, which, _ ->
                    if (which > subtitlesGrouped.size) {
                        // Since android TV is funky the setOnItemClickListener will be triggered
                        // instead of setOnClickListener when selecting. To override this we programmatically
                        // click the view when selecting an item outside the list.

                        // Cheeky way of getting the view at that position to click it
                        // to avoid keeping track of the various footers.
                        // getChildAt() gives null :(
                        val child = subtitleList.adapter.getView(which, null, subtitleList)
                        child?.performClick()
                    } else {
                        if (subtitleGroupIndex != which) {
                            subtitleGroupIndex = which
                            subtitleOptionIndex =
                                if (subtitleGroupIndex == subtitleGroupIndexStart) {
                                    subtitleOptionIndexStart
                                } else {
                                    0
                                }
                        }
                        subtitleList.setItemChecked(which, true)
                        updateSubtitleOptionList()
                    }
                }

                subtitleOptionList.setOnItemClickListener { _, _, which, _ ->
                    if (which >= (subtitlesGroupedList.getOrNull(subtitleGroupIndex - 1)?.value?.size
                            ?: -1)
                    ) {
                        val child = subtitleOptionList.adapter.getView(which, null, subtitleList)
                        child?.performClick()
                    } else {
                        subtitleOptionIndex = which
                        subtitleOptionList.setItemChecked(which, true)
                    }
                }

                binding.cancelBtt.setOnClickListener {
                    sourceDialog.dismissSafe(activity)
                    this.selectSourceDialog = null
                }

                fun setProfileName(profile: Int) {
                    binding.sourceSettingsBtt.setText(
                        QualityDataHelper.getProfileName(
                            profile
                        )
                    )
                }
                setProfileName(currentQualityProfile)

                binding.profilesClickSettings.setOnClickListener {
                    val activity = activity ?: return@setOnClickListener
                    val dialog = QualityProfileDialog(
                        activity,
                        R.style.DialogFullscreenPlayer,
                        viewModel.state.links.mapNotNull {
                            it.first?.let { extractorLink ->
                                LinkSource(
                                    extractorLink
                                )
                            }
                        },
                        currentQualityProfile
                    ) { profile ->
                        currentQualityProfile = profile.id
                        setProfileName(profile.id)
                    }

                    dialog.setOnDismissListener {
                        if (sourceDialog.isShowing && isCurrentDialog()) {
                            viewModel.state.clearSortedLinksCache()
                            refreshLinks(currentQualityProfile)
                        }
                    }

                    dialog.show()
                }

                binding.subtitlesEncodingFormat.apply {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

                    val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
                    val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

                    val value = settingsManager.getString(
                        ctx.getString(R.string.subtitles_encoding_key), null
                    )
                    val index = prefValues.indexOf(value)
                    text = prefNames[if (index == -1) 0 else index]
                }

                binding.subtitlesEncodingFormat.setOnClickListener {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

                    val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
                    val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

                    val currentPrefMedia = settingsManager.getString(
                        ctx.getString(R.string.subtitles_encoding_key), null
                    )

                    shouldDismiss = false
                    sourceDialog.dismissSafe(activity)
                    selectSourceDialog = null

                    val index = prefValues.indexOf(currentPrefMedia)
                    activity?.showDialog(
                        prefNames.toList(),
                        if (index == -1) 0 else index,
                        ctx.getString(R.string.subtitles_encoding),
                        true,
                        {}) {
                        settingsManager.edit {
                            putString(
                                ctx.getString(R.string.subtitles_encoding_key), prefValues[it]
                            )
                        }
                        updateForcedEncoding(ctx)
                        dismiss()
                        player.refreshSubtitles()
                    }
                }

                binding.applyBtt.setOnClickListener {
                    if (!isCurrentDialog()) {
                        sourceDialog.dismissSafe(activity)
                        return@setOnClickListener
                    }
                    if (sourceSelectionTouched) {
                        manuallySelectedSource = true
                        sourceStartupJob?.cancel()
                        sourceStartupJob = null
                        filteredLinks.getOrNull(sourceIndex)?.let { rememberVideo(it.link) }
                    }
                    var init = sourceSelectionTouched || sourceIndex != startSource
                    if (subtitleGroupIndex != subtitleGroupIndexStart || subtitleOptionIndex != subtitleOptionIndexStart) {
                        init = init or if (subtitleGroupIndex <= 0) {
                            noSubtitles()
                        } else {
                            subtitlesGroupedList.getOrNull(subtitleGroupIndex - 1)?.value?.getOrNull(
                                subtitleOptionIndex
                            )?.let {
                                setSubtitles(it, true)
                            } ?: false
                        }
                    }
                    if (init) {
                        filteredLinks.getOrNull(sourceIndex)?.let {
                            if (sourceIndex != startSource) rememberVideo(it.link)
                            loadLink(it.link, true, manualSelection =
                                if (sourceSelectionTouched || sourceIndex != startSource) true else null,
                                autoPlay = player.getPlayWhenReady())
                        }
                    }
                    sourceDialog.dismissSafe(activity)
                    selectSourceDialog = null
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun showTracksDialogue() {
        try {
            //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
            context?.let { ctx ->
                val tracks = player.getVideoTracks()

                val isPlaying = player.getPlayWhenReady()
                player.handleEvent(CSPlayerEvent.Pause)

                val currentVideoTracks = tracks.allVideoTracks.sortedBy {
                    it.height?.times(-1)
                }
                val currentAudioTracks = tracks.allAudioTracks
                val binding: PlayerSelectTracksBinding =
                    PlayerSelectTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
                val trackDialog = Dialog(ctx, R.style.DialogFullscreenPlayer)
                this.selectTrackDialog = trackDialog
                trackDialog.setContentView(binding.root)
                trackDialog.show()
                com.lagradost.cloudstream3.utils.DialogApplyShortcut.install(trackDialog)

                fixSystemBarsPadding(binding.root)

                // selectTracksDialog = tracksDialog

                val videosList = binding.videoTracksList
                val audioList = binding.autoTracksList

                binding.videoTracksHolder.isVisible = currentVideoTracks.size > 1
                binding.audioTracksHolder.isVisible = currentAudioTracks.size > 1

                fun dismiss() {
                    if (isPlaying) {
                        player.handleEvent(CSPlayerEvent.Play)
                    }
                    activity?.hideSystemUI()
                }

                val videosArrayAdapter =
                    ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

                videosArrayAdapter.addAll(currentVideoTracks.mapIndexed { index, format ->
                    format.label
                        ?: (if (format.height == NO_VALUE || format.width == NO_VALUE) index else "${format.width}x${format.height}").toString()
                })

                videosList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                videosList.adapter = videosArrayAdapter

                // Sometimes the data is not the same because some data gets resolved at different stages i think
                var videoIndex = currentVideoTracks.indexOf(tracks.currentVideoTrack).takeIf {
                    it != -1
                } ?: currentVideoTracks.indexOfFirst {
                    tracks.currentVideoTrack?.id == it.id
                }

                videosList.setSelection(videoIndex)
                videosList.setItemChecked(videoIndex, true)

                videosList.setOnItemClickListener { _, _, which, _ ->
                    videoIndex = which
                    videosList.setItemChecked(which, true)
                }

                trackDialog.setOnDismissListener {
                    dismiss()
                    // selectTracksDialog = null
                }

                var audioIndexStart = currentAudioTracks.indexOfFirst { track ->
                    track.id == tracks.currentAudioTrack?.id &&
                            track.formatIndex == tracks.currentAudioTrack?.formatIndex
                }.coerceAtLeast(0)

                val audioArrayAdapter =
                    ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

                audioArrayAdapter.addAll(
                    currentAudioTracks.mapIndexed { _, track ->

                        val language = (
                                track.language?.trim()?.let { raw ->
                                    fromTagToLanguageName(raw)
                                        ?: fromTagToLanguageName(
                                            raw.replace('_', '-').substringBefore('-').lowercase()
                                        )
                                        ?: raw
                                }
                                    ?: track.label
                                    ?: "Audio"
                                ).replaceFirstChar { it.uppercaseChar() }

                        val codec = audioCodecName(track.sampleMimeType)

                        val channelCount = track.channelCount

                        val channels = when {
                            // May be below 1 or null when unknown
                            channelCount == null || channelCount <= 0 -> ""
                            channelCount == 1 -> "Mono"
                            channelCount == 2 -> "Stereo"
                            channelCount == 6 -> "5.1"
                            channelCount == 8 -> "7.1"
                            else -> "${channelCount}ch"
                        }

                        listOfNotNull(
                            language.takeIf { it.isNotBlank() }
                                ?.replaceFirstChar { it.uppercaseChar() },
                            channels.takeIf { it.isNotBlank() },
                            codec.takeIf { it.isNotBlank() }?.uppercase()
                        ).joinToString(" • ")


                    }
                )

                audioList.adapter = audioArrayAdapter
                audioList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                audioList.setSelection(audioIndexStart)
                audioList.setItemChecked(audioIndexStart, true)

                audioList.setOnItemClickListener { _, _, which, _ ->
                    audioIndexStart = which
                    audioList.setItemChecked(which, true)
                }

                binding.cancelBtt.setOnClickListener {
                    trackDialog.dismissSafe(activity)
                    this.selectTrackDialog = null
                }

                binding.applyBtt.setOnClickListener {
                    val currentTrack = currentAudioTracks.getOrNull(audioIndexStart)
                    player.setPreferredAudioTrack(
                        currentTrack?.language,
                        currentTrack?.id,
                        currentTrack?.formatIndex,
                    )

                    val currentVideo = currentVideoTracks.getOrNull(videoIndex)
                    val width = currentVideo?.width ?: NO_VALUE
                    val height = currentVideo?.height ?: NO_VALUE
                    if (width != NO_VALUE && height != NO_VALUE) {
                        player.setMaxVideoSize(width, height, currentVideo?.id)
                    }
                    trackDialog.dismissSafe(activity)
                    this.selectTrackDialog = null
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    override fun playerError(exception: Throwable) {
        sourceStartupJob?.cancel()
        sourceStartupJob = null
        if (deferPlayerAction { playerError(exception) }) return
        currentSelectedLink?.let { link ->
            sourcePlan.failed(link, videoIdentity(link))
            viewModel.modifyState { this.addError(link) }
        }

        val currentUrl =
            currentSelectedLink?.let { it.first?.url ?: it.second?.uri?.toString() } ?: "unknown"
        val headers = currentSelectedLink?.first?.headers?.toString() ?: "none"
        val referer = currentSelectedLink?.first?.referer ?: "none"
        Log.e(
            TAG,
            "playerError: $currentSelectedLink, " +
                    "type=${exception::class.qualifiedName}, " +
                    "message=${exception.message}, url=$currentUrl, headers=$headers, " +
                    "referer=$referer, position=${player.getPosition() ?: "unknown"}, " +
                    "duration=${player.getDuration() ?: "unknown"}, " +
                    "isPlaying=${player.getIsPlaying()}", exception
        )

        if (!hasNextMirror()) {
            viewModel.forceClearCache = true
        }
        super.playerError(exception)
    }

    private fun noLinksFound() {
        viewModel.forceClearCache = true
        val hiddenLinks = viewModel.state.sortLinks(currentQualityProfile).count { !it.shouldUseLink }

        context?.let { ctx ->
            // Display that there are hidden links to the user.
            if (hiddenLinks > 0) {
                val noLinksString = ctx.getString(R.string.no_links_found_toast)
                val hiddenString =
                    ctx.resources.getQuantityString(R.plurals.links_hidden, hiddenLinks)
                        .format(hiddenLinks)
                val toastText = "$noLinksString\n($hiddenString)"
                showToast(toastText, Toast.LENGTH_SHORT)
            } else {
                showToast(R.string.no_links_found_toast, Toast.LENGTH_SHORT)
            }
        }

        activity?.popCurrentPage()
    }

    private fun startPlayer() {
        // We don't want double load when you skip loading
        if (isPlayerActive.get()) {
            return
        }

        val firstAvailableLink = sourcePlan.ranked(sourceCandidates(), seriesPreferences()).firstOrNull()?.id
        if (firstAvailableLink == null) {
            if (collectingSources()) return
            noLinksFound()
            return
        }
        // Atomic operation to prevent double loading
        if (!isPlayerActive.compareAndSet(false, true)) {
            return
        }
        Log.i(TAG, "Source pipeline: starting after ${SystemClock.elapsedRealtime() - sourceCollectionStartedAt}ms; " +
            "collected=${viewModel.state.links.size}; collecting=${collectingSources()}; " +
            "savedMatch=${videoIdentity(firstAvailableLink)?.let { seriesPreferences().video?.match(it) } ?: 0}")
        loadLink(firstAvailableLink, false)
        showPlayerMetadata()
    }

    private fun showPlayerMetadata() {
        val overlay = playerBinding?.playerMetadataScrim ?: return

        val titleView = overlay.findViewById<TextView>(R.id.player_movie_title)
        val logoView = overlay.findViewById<ImageView>(R.id.player_movie_logo)
        val metaView = overlay.findViewById<TextView>(R.id.player_movie_meta)
        val descView = overlay.findViewById<TextView>(R.id.player_movie_overview)

        val load = viewModel.state.generatorState?.response ?: return
        val episode = currentMeta as? ResultEpisode
        titleView.text = load.name

        bindLogo(
            url = load.logoUrl,
            headers = load.posterHeaders,
            titleView = titleView,
            logoView = logoView
        )

        val meta = arrayOf(
            load.tags?.takeIf { it.isNotEmpty() }?.take(6)?.joinToString(", "),
            load.year?.toString(),
            if (!load.type.isMovieType())
                context?.getShortSeasonText(
                    episode = episode?.episode,
                    season = episode?.season
                )
            else null,
            load.score?.let { "⭐ $it" }
        ).filterNotNull()
            .joinToString(" • ")

        metaView.text = meta
        metaView.isVisible = meta.isNotBlank()


        val description = load.plot

        if (!description.isNullOrBlank()) {
            descView.isVisible = true
            descView.text = description.html()
        } else {
            descView.isVisible = false

        }
    }

    override fun nextEpisode() {
        if (viewModel.hasNextEpisode() == true) {
            isNextEpisode = true
            releasePlayer()
            viewModel.loadLinksNext()
        }
    }

    override fun prevEpisode() {
        if (viewModel.hasPrevEpisode() == true) {
            isNextEpisode = true
            releasePlayer()
            viewModel.loadLinksPrev()
        }
    }

    private fun getNextLink(): VideoLink? =
        sourcePlan.ranked(sourceCandidates(), seriesPreferences()).firstOrNull()?.id

    override fun hasNextMirror(): Boolean {
        return getNextLink() != null || collectingSources()
    }

    override fun nextMirror() {
        if (deferPlayerAction { nextMirror() }) return
        val nextLink = getNextLink()
        if (nextLink == null) {
            if (collectingSources()) {
                awaitingSourceFallback = true
                // Do not close the player while useful sources are still arriving.
                player.handleEvent(CSPlayerEvent.Pause, PlayerEventSource.UI)
            } else noLinksFound()
            return
        }

        loadLink(nextLink, true, manualSelection = false)
    }

    override fun onDestroyView() {
        sourceOperations.advance()
        deferredPlayerActionJob?.cancel()
        subtitleAutoSelectJob?.cancel()
        sourceCollectionWaitJob?.cancel()
        sourceStartupJob?.cancel()
        // Old dialog listeners must not resume or mutate a recreated player.
        listOf(selectSourceDialog, selectSubtitlesDialog, selectTrackDialog, selectSpeedDialog, selectCompressorDialog).forEach { dialog ->
            dialog?.setOnDismissListener(null)
            dialog?.dismissSafe(activity)
        }
        selectSourceDialog = null
        selectSubtitlesDialog = null
        selectTrackDialog = null
        selectSpeedDialog = null
        selectCompressorDialog = null
        cachedSortedSourceLinks = null
        cachedSourceCandidates = emptyList()
        super.onDestroyView()
    }

    override fun onDestroy() {
        sourceCollectionWaitJob?.cancel()
        sourceStartupJob?.cancel()
        ResultFragment.updateUI()
        currentVerifyLink?.cancel()
        super.onDestroy()
    }

    var maxEpisodeSet: Int? = null
    var hasRequestedStamps: Boolean = false
    override fun playerPositionChanged(position: Long, duration: Long) {
        // Preparing a failed source can briefly report position zero with a known
        // duration. Do not overwrite the stored resume point before actual playback.
        if (!sourcePlaybackStarted && (player as? CS3IPlayer)?.hasStartedPlayback() == false) return
        // Don't save livestream data
        if ((currentMeta as? ResultEpisode)?.tvType?.isLiveStream() == true) return

        // Don't save NSFW data
        if ((currentMeta as? ResultEpisode)?.tvType == TvType.NSFW) return

        if (duration <= 0L) return // idk how you achieved this, but div by zero crash
        if (!hasRequestedStamps) {
            hasRequestedStamps = true
            val fetchStamps = context?.let { ctx ->
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                settingsManager.getBoolean(
                    ctx.getString(R.string.enable_skip_op_from_database),
                    true
                )
            } ?: true
            if (fetchStamps)
                viewModel.loadStamps(duration)
        }

        val percentage = position * 100L / duration

        DataStoreHelper.setViewPosAndResume(
            viewModel.state.generatorState?.id,
            position,
            duration,
            currentMeta,
            nextMeta
        )

        var isOpVisible = false
        when (val meta = currentMeta) {
            is ResultEpisode -> {
                if (percentage >= UPDATE_SYNC_PROGRESS_PERCENTAGE && (maxEpisodeSet
                        ?: -1) < meta.episode
                ) {
                    context?.let { ctx ->
                        val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                        if (settingsManager.getBoolean(
                                ctx.getString(R.string.episode_sync_enabled_key), true
                            )
                        ) {
                            maxEpisodeSet = meta.episode
                            sync.modifyMaxEpisode(meta.totalEpisodeIndex ?: meta.episode)
                        }
                    }
                }

                if (meta.tvType.isAnimeOp()) isOpVisible = percentage < SKIP_OP_VIDEO_PERCENTAGE
            }
        }

        playerBinding?.playerSkipOp?.isVisible = isOpVisible

        when {
            isLayout(PHONE) ->
                playerBinding?.playerSkipEpisode?.isVisible =
                    !isOpVisible && viewModel.hasNextEpisode() == true

            else -> {
                val hasNextEpisode = viewModel.hasNextEpisode() == true
                playerBinding?.playerGoForward?.isVisible = hasNextEpisode
                playerBinding?.playerGoForwardRoot?.isVisible = hasNextEpisode
            }

        }

        if (percentage >= PRELOAD_NEXT_EPISODE_PERCENTAGE && !collectingSources() && !isDialogOpen()) {
            viewModel.preLoadNextLinks()
        }
    }

    private fun getAutoSelectSubtitle(
        subtitles: Set<SubtitleData>, settings: Boolean, downloads: Boolean
    ): SubtitleData? {
        if (seriesPreferences().subtitlesOff) return null
        preferredSeriesSubtitle(subtitles)?.let { return it }
        val langCode = preferredAutoSelectSubtitles ?: return null
        if (downloads) {
            sortSubs(subtitles).firstOrNull {
                it.origin == SubtitleOrigin.DOWNLOADED_FILE && it.matchesLanguageCode(
                    langCode
                )
            }?.let { return it }
        }

        if (!settings) return null

        return sortSubs(subtitles).firstOrNull { it.matchesLanguageCode(langCode) }
    }

    private fun autoSelectFromSettings(): Boolean {
        // A matching series choice takes precedence when tracks arrive after video load.
        if (seriesPreferences().subtitlesOff) return false
        preferredSeriesSubtitle(viewModel.state.subtitles)?.let { preferred ->
            if (player.getCurrentPreferredSubtitle()?.getId() != preferred.getId()) {
                val ctx = context ?: return false
                if (setSubtitles(preferred, false)) {
                    player.saveData()
                    player.reloadPlayer(ctx)
                }
            }
            return true
        }
        // auto select subtitle based on settings
        val langCode = preferredAutoSelectSubtitles
        val current = player.getCurrentPreferredSubtitle()
        Log.i(TAG, "autoSelectFromSettings = $current")
        context?.let { ctx ->
            // Only use the player preferred subtitle if it matches the available language
            if (current != null && (langCode == null || current.matchesLanguageCode(langCode))) {
                if (setSubtitles(current, false)) {
                    player.saveData()
                    player.reloadPlayer(ctx)
                    return true
                }
            } else if (!langCode.isNullOrEmpty()) {
                getAutoSelectSubtitle(
                    viewModel.state.subtitles, settings = true, downloads = false
                )?.let { sub ->
                    if (setSubtitles(sub, false)) {
                        player.saveData()
                        player.reloadPlayer(ctx)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun autoSelectFromDownloads() {
        if (seriesPreferences().subtitlesOff) return
        if (player.getCurrentPreferredSubtitle() != null) {
            return
        }
        val sub =
            getAutoSelectSubtitle(viewModel.state.subtitles, settings = false, downloads = true)
                ?: return
        val ctx = context ?: return
        if (!setSubtitles(sub, false)) {
            return
        }
        player.saveData()
        player.reloadPlayer(ctx)
    }

    private fun autoSelectSubtitles() {
        //Log.i(TAG, "autoSelectSubtitles")
        safe {
            if (!autoSelectFromSettings()) {
                autoSelectFromDownloads()
            }
        }
    }

    private fun getHeaderName(): String? {
        return when (val meta = currentMeta) {
            is ResultEpisode -> meta.headerName
            is ExtractorUri -> meta.headerName
            else -> null
        }
    }

    private fun getPlayerVideoTitle(): String {
        var headerName: String? = null
        var subName: String? = null
        var episode: Int? = null
        var season: Int? = null
        var tvType: TvType? = null

        when (val meta = currentMeta) {
            is ResultEpisode -> {
                headerName = meta.headerName
                subName = meta.name
                episode = meta.episode
                season = meta.season
                tvType = meta.tvType
            }

            is ExtractorUri -> {
                headerName = meta.headerName
                subName = meta.name
                episode = meta.episode
                season = meta.season
                tvType = meta.tvType
            }
        }
        context?.let { ctx ->
            //Generate video title
            val playerVideoTitle = if (headerName != null) {
                (headerName + if (tvType.isEpisodeBased() && episode != null) if (season == null) " - ${
                    ctx.getString(
                        R.string.episode
                    )
                } $episode"
                else " \"${ctx.getString(R.string.season_short)}${season}:${
                    ctx.getString(
                        R.string.episode_short
                    )
                }${episode}\""
                else "") + if (subName.isNullOrBlank() || subName == headerName) "" else " - $subName"
            } else {
                ""
            }
            return playerVideoTitle
        }
        return ""
    }

    fun setTitle() {
        var playerVideoTitle = getPlayerVideoTitle()

        //Hide title, if set in setting
        if (limitTitle < 0) {
            playerBinding?.playerVideoTitle?.visibility = View.GONE
        } else {
            //Truncate video title if it exceeds limit
            val differenceInLength = playerVideoTitle.length - limitTitle
            val margin = 3 //If the difference is smaller than or equal to this value, ignore it
            if (limitTitle > 0 && differenceInLength > margin) {
                playerVideoTitle = playerVideoTitle.substring(0, limitTitle - 1) + "..."
            }
        }
        val isFiller: Boolean? = (currentMeta as? ResultEpisode)?.isFiller

        playerBinding?.playerEpisodeFillerHolder?.isVisible = isFiller ?: false
        playerBinding?.playerVideoTitle?.text = playerVideoTitle
        playerBinding?.offlinePin?.isVisible = viewModel.generator is DownloadFileGenerator
    }

    fun setPlayerDimen(widthHeight: Pair<Int, Int>?) {
        val resolution = widthHeight?.let { "${it.first}x${it.second}" }
        val name = currentSelectedLink?.first?.name ?: currentSelectedLink?.second?.name
        val title = getHeaderName()

        val result = listOfNotNull(
            title?.takeIf { showTitle && it.isNotBlank() },
            name?.takeIf { showName && it.isNotBlank() },
            resolution?.takeIf { showResolution && it.isNotBlank() },
        ).joinToString(" - ")

        playerBinding?.playerVideoTitleRez?.apply {
            text = result
            isVisible = result.isNotBlank()
        }
    }


    private fun videoCodecName(mime: String?): String? {
        val m = mime?.lowercase() ?: return null
        return when {
            m.contains("avc") || m.contains("h264") -> "AVC"
            m.contains("hevc") || m.contains("h265") -> "HEVC"
            m.contains("av1") -> "AV1"
            m.contains("vp9") -> "VP9"
            m.contains("vp8") -> "VP8"
            "/" in m -> m.substringAfter("/").uppercase()
            else -> m.uppercase()
        }
    }

    private fun audioCodecName(mime: String?): String {
        val m = mime?.lowercase()?.trim().orEmpty()
        if (m.isBlank()) return ""
        return when {
            m.contains("eac3-joc") -> "Dolby Atmos"
            m.contains("truehd") -> "TrueHD"
            m.contains("eac3") -> "E-AC3"
            m.contains("ac-3") || m.contains("ac3") -> "AC3"
            m.contains("aac") || m.contains("mp4a") -> "AAC"
            m.contains("opus") -> "Opus"
            m.contains("vorbis") -> "Vorbis"
            m.contains("mp3") -> "MP3"
            m.contains("flac") -> "FLAC"
            m.contains("dts") -> "DTS"
            m.contains("pcm") -> "PCM"
            m.contains("alac") -> "ALAC"
            m.contains("amr") -> "AMR"
            m.contains("/") -> m.substringAfter("/").uppercase().takeIf { it.isNotBlank() } ?: ""
            else -> ""
        }
    }

    private fun updatePlayerInfo() {
        val tracks = player.getVideoTracks()

        val videoTrack = tracks.currentVideoTrack
        val audioTrack = tracks.currentAudioTrack

        val ctx = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        showMediaInfo = prefs.getBoolean(ctx.getString(R.string.show_media_info_key), false)

        val videoCodec = videoCodecName(videoTrack?.sampleMimeType)
        val audioCodec = audioCodecName(audioTrack?.sampleMimeType)
        val languageName = fromTagToLanguageName(audioTrack?.language)
        val label = audioTrack?.label

        val channelCount = audioTrack?.channelCount

        val channels = when {
            // May be below 1 or null when unknown
            channelCount == null || channelCount <= 0 -> ""
            channelCount == 1 -> "Mono"
            channelCount == 2 -> "Stereo"
            channelCount == 6 -> "5.1"
            channelCount == 8 -> "7.1"
            else -> "${channelCount}ch"
        }

        val language = languageName?.takeIf { it.isNotBlank() }?.let { lang ->
            label?.takeIf { it.isNotBlank() && !it.equals(lang, true) }
                ?.let { lang }
                ?: lang
        } ?: label?.takeIf { it.isNotBlank() }

        val stats = arrayOf(
            videoCodec,
            language,
            channels,
            audioCodec
        ).filter { !it.isNullOrBlank() }.joinToString(" • ")

        playerBinding?.playerVideoInfo?.apply {
            text = stats
            isVisible = showMediaInfo && stats.isNotBlank()
        }
    }

    override fun playerDimensionsLoaded(width: Int, height: Int) {
        super.playerDimensionsLoaded(width, height)
        setPlayerDimen(width to height)
    }

    private fun unwrapBundle(savedInstanceState: Bundle?) {
        Log.i(TAG, "unwrapBundle = $savedInstanceState")
        savedInstanceState?.let { bundle ->
            sync.addSyncs(bundle.getSafeSerializable<HashMap<String, String>>("syncData"))
        }
    }

    /**
     * This is used instead of layout-television to follow the
     * settings and some TV devices are not classified as TV
     * for some reason.
     */
    override fun pickLayout(): Int =
        if (isLayout(TV or EMULATOR)) R.layout.fragment_player_tv else R.layout.fragment_player

    var skipAnimator: ValueAnimator? = null
    var skipIndex = 0

    private fun displayTimeStamp(show: Boolean) {
        if (timestampShowState == show) return
        skipIndex++
        timestampShowState = show
        playerBinding?.skipChapterButton?.apply {
            val showWidth = 170.toPx
            val noShowWidth = 10.toPx
            //if((show && width == showWidth) || (!show && width == noShowWidth)) {
            //    return
            //}
            val to = if (show) showWidth else noShowWidth
            val from = if (!show) showWidth else noShowWidth

            skipAnimator?.cancel()
            isVisible = true

            /** Focus instantly to make the focus color appear instantly */
            if (show && !isShowing) {
                // Automatically request focus if the menu is not opened
                playerBinding?.skipChapterButton?.requestFocus()
            }

            // just in case
            val lay = layoutParams
            lay.width = from
            layoutParams = lay
            skipAnimator = ValueAnimator.ofInt(
                from, to
            ).apply {
                addListener(onEnd = {
                    if (!show) {
                        playerBinding?.skipChapterButton?.isVisible = false
                        if (!isShowing) {
                            // Automatically return focus to play pause
                            playerBinding?.playerPausePlay?.requestFocus()
                        }
                    }
                })
                addUpdateListener { valueAnimator ->
                    val value = valueAnimator.animatedValue as Int
                    val layoutParams: ViewGroup.LayoutParams = layoutParams
                    layoutParams.width = value
                    setLayoutParams(layoutParams)
                }
                duration = 500
                start()
            }
        }
    }

    override fun onTimestampSkipped(timestamp: VideoSkipStamp) {
        displayTimeStamp(false)
    }

    override fun onTimestamp(timestamp: VideoSkipStamp?) {
        if (timestamp != null) {
            playerBinding?.skipChapterButton?.setText(timestamp.uiText)
            displayTimeStamp(true)
            val currentIndex = skipIndex
            playerBinding?.skipChapterButton?.handler?.postDelayed({
                if (skipIndex == currentIndex)
                    displayTimeStamp(false)
            }, 6000)
        } else {
            displayTimeStamp(false)
        }
    }

    override fun isThereEpisodes(): Boolean {
        // Checks if there is a second episode of type ResultEpisode
        // => There exists more than 1 episode, and they are all ResultEpisode
        return viewModel.state.generatorState?.allMeta?.getOrNull(1) as? ResultEpisode != null
    }

    override fun showEpisodesOverlay() {
        try {
            playerBinding?.apply {
                playerEpisodeList.setRecycledViewPool(EpisodeAdapter.sharedPool)
                playerEpisodeList.adapter = EpisodeAdapter(
                    false,
                    { episodeClick ->
                        if (episodeClick.action == ACTION_CLICK_DEFAULT) {
                            isNextEpisode = false
                            releasePlayer()
                            playerEpisodeOverlay.isGone = true
                            episodeClick.position?.let { viewModel.loadThisEpisode(it) }
                        }
                    },
                    { downloadClickEvent ->
                        DownloadButtonSetup.handleDownloadClick(downloadClickEvent)
                    }
                )
                playerEpisodeList.setLinearListLayout(
                    isHorizontal = false,
                    nextUp = FOCUS_SELF,
                    nextDown = FOCUS_SELF,
                    nextRight = FOCUS_SELF,
                )
                val episodes = allMeta ?: emptyList()
                (playerEpisodeList.adapter as? EpisodeAdapter)?.submitList(episodes)

                // Scroll to current episode
                viewModel.state.generatorState?.index?.let { index ->
                    playerEpisodeList.scrollToPosition(index)
                    // Ensure focus on tv
                    if (isLayout(TV)) {
                        playerEpisodeList.post {
                            val viewHolder =
                                playerEpisodeList.findViewHolderForAdapterPosition(index)
                            viewHolder?.itemView?.requestFocus()
                            viewHolder?.itemView?.let { itemView ->
                                itemView.isFocusableInTouchMode = true
                                itemView.requestFocus()
                            }
                        }
                    }
                }

                // update overlay season title
                var lastTopIndex = -1
                playerEpisodeList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        val layoutManager =
                            recyclerView.layoutManager as? LinearLayoutManager ?: return
                        val topIndex = layoutManager.findFirstCompletelyVisibleItemPosition()
                        if (topIndex != RecyclerView.NO_POSITION && topIndex != lastTopIndex) {
                            @Suppress("AssignedValueIsNeverRead")
                            lastTopIndex = topIndex
                            val topItem = episodes.getOrNull(topIndex)
                            topItem?.let {
                                playerEpisodeOverlayTitle.setText(
                                    ResultViewModel2.seasonToTxt(
                                        topItem.seasonData,
                                        topItem.seasonIndex
                                    )
                                )
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    @MainThread
    fun releasePlayer() {
        sourceOperations.advance()
        deferredPlayerActionJob?.cancel()
        subtitleAutoSelectJob?.cancel()
        sourceStartupJob?.cancel()
        sourceStartupJob = null
        player.release()
        currentSelectedSubtitles = null
        currentSelectedLink = null
        isPlayerActive.set(false)
        binding?.overlayLoadingSkipButton?.isVisible = false
        binding?.playerLoadingOverlay?.isVisible = true
        viewModel.modifyState { setError(emptyList()) }
        uiReset()
    }

    fun exitPlayer() {
        playerHostView?.exitFullscreen()
        player.release()
        activity?.popCurrentPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("index", viewModel.episodeIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onBindingCreated(binding: FragmentPlayerBinding, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[PlayerGeneratorViewModel::class.java]
        sync = ViewModelProvider(this)[SyncViewModel::class.java]

        val uuid = savedInstanceState?.getString("uuid") ?: arguments?.getString("uuid")
        val index = savedInstanceState?.getInt("index") ?: arguments?.getInt("index")
        val generator = generators[uuid]

        unwrapBundle(savedInstanceState)
        unwrapBundle(arguments)

        super.onBindingCreated(binding, savedInstanceState)

        // Avoid showing no links found
        if (generator == null || index == null) {
            exitPlayer()
            return
        }
        viewModel.attachGenerator(generator, index)

        context?.let { ctx ->
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
            showName = settingsManager.getBoolean(ctx.getString(R.string.show_name_key), true)
            showResolution =
                settingsManager.getBoolean(ctx.getString(R.string.show_resolution_key), true)
            showMediaInfo =
                settingsManager.getBoolean(ctx.getString(R.string.show_media_info_key), false)
            limitTitle = settingsManager.getInt(ctx.getString(R.string.prefer_title_limit_key), 0)
            updateForcedEncoding(ctx)
            viewModel.filterSubByLang =
                settingsManager.getBoolean(getString(R.string.filter_sub_lang_key), false)
            if (viewModel.filterSubByLang) {
                val langFromPrefMedia = settingsManager.getStringSet(
                    this.getString(R.string.provider_lang_key), mutableSetOf("en")
                )
                viewModel.langFilterList = langFromPrefMedia?.mapNotNull {
                    fromTagToEnglishLanguageName(it)?.lowercase() ?: return@mapNotNull null
                } ?: listOf()
            }

            // Set up TV clock visibility
            if (isLayout(TV)) {
                val showTvClock = settingsManager.getBoolean(ctx.getString(R.string.tv_layout_clock_key), false)
                playerBinding?.playerVideoClock?.isVisible = showTvClock
            } else {
                playerBinding?.playerVideoClock?.isVisible = false
            }
        }

        unwrapBundle(savedInstanceState)
        unwrapBundle(arguments)

        sync.updateUserData()

        preferredAutoSelectSubtitles = getAutoSelectLanguageTagIETF()

        val selectedLink = currentSelectedLink
        if (selectedLink == null) {
            viewModel.loadLinks()
        } else {
            // Recreated view, so we need to recreate the
            loadLink(selectedLink, true)
        }

        binding.overlayLoadingSkipButton.setOnClickListener {
            sourcePlan.skipRequested = true
            // Skip waiting, not extraction. Late sources/subtitles remain available.
            startPlayer()
        }

        binding.playerLoadingGoBack.setOnClickListener {
            exitPlayer()
        }

        playerBinding?.downloadHeader?.setOnClickListener {
            it?.isVisible = false
        }

        playerBinding?.downloadHeaderToggle?.setOnClickListener {
            playerBinding?.downloadHeader?.let {
                it.isVisible = !it.isVisible
            }
        }

        observe(viewModel.currentStamps) { (stamps, instance) ->
            if (instance != viewModel.state.instance) return@observe // Outdated observe
            player.addTimeStamps(stamps)
        }

        observe(viewModel.currentSubtitles) { (subtitles, instance) ->
            if (instance != viewModel.state.instance) return@observe // Outdated observe
            player.setActiveSubtitles(subtitles)

            // If the file is downloaded then do not select auto select the subtitles
            // Downloaded subtitles cannot be selected immediately after loading since
            // player.getCurrentPreferredSubtitle() cannot fetch data from non-loaded subtitles
            // Resulting in unselecting the downloaded subtitle
            if (subtitles.lastOrNull()?.origin != SubtitleOrigin.DOWNLOADED_FILE) {
                scheduleSubtitleAutoSelection()
            }
        }
        observe(viewModel.loadingLinks) { (loading, instance) ->
            if (instance != viewModel.state.instance) return@observe // Outdated observe

            prepareSourceSession()
            when (loading) {
                is Resource.Loading -> maybeStartSource()
                is Resource.Success -> {
                    if (awaitingSourceFallback) nextMirror() else maybeStartSource()
                }
                is Resource.Failure -> {
                    if (!isPlayerActive.get()) showToast(loading.errorString, Toast.LENGTH_LONG)
                    if (awaitingSourceFallback) nextMirror() else maybeStartSource()
                }
            }
        }

        observe(viewModel.currentLinks) { (_, instance) ->
            if (instance != viewModel.state.instance) return@observe // Outdated observe
            prepareSourceSession()

            val sortedLinks = viewModel.state.sortLinks(currentQualityProfile)
            val usableLinks = sortedLinks.count { link -> link.shouldUseLink }
            if (usableLinks > 0 && !loggedFirstSources) {
                loggedFirstSources = true
                Log.i(TAG, "Source pipeline: first usable sources after ${SystemClock.elapsedRealtime() - sourceCollectionStartedAt}ms; usable=$usableLinks")
            }

            val turnVisible = usableLinks > 0 && viewModel.generator?.canSkipLoading == true
            val wasGone = binding.overlayLoadingSkipButton.isGone

            binding.overlayLoadingSkipButton.apply {
                isVisible = turnVisible

                if (usableLinks == 0) {
                    setText(R.string.skip_loading)
                } else {
                    @SuppressLint("SetTextI18n")
                    text = "${context.getString(R.string.skip_loading)} (${usableLinks})"
                }
            }

            if (awaitingSourceFallback) nextMirror() else maybeStartSource()

            if (turnVisible && wasGone) {
                binding.overlayLoadingSkipButton.requestFocus()
            }
        }
    }
}

@Suppress("DEPRECATION")
inline fun <reified T : Serializable> Bundle.getSafeSerializable(key: String): T? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) getSerializable(key) as? T else getSerializable(
        key,
        T::class.java
    )
