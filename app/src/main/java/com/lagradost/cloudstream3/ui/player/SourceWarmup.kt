package com.lagradost.cloudstream3.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.lagradost.cloudstream3.utils.AppContextUtils.isNetworkAvailable
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream4.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One foreground-only speculative worker, not a service or a second media player. */
internal object SourceWarmup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var context: Context? = null
    private var foreground = false
    private var enabled = false
    private var home = emptyList<DataStoreHelper.ResumeWatchingResult>()
    private var title: Pair<Any, SourceWarmupTarget>? = null
    private var titleRevision = 0
    private var worker: Job? = null
    private var generation = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkEligible: Boolean? = null

    @Synchronized
    fun foreground(context: Context, active: Boolean) {
        if (this.context == null) {
            val application = context.applicationContext
            this.context = application
            enabled = AppSettings(context).player.sourceWarmupEnabled.get()
            scope.launch {
                AppSettings(application).player.sourceWarmupEnabled.changes().collectLatest {
                    synchronized(this@SourceWarmup) {
                        enabled = it
                        restart()
                    }
                }
            }
            var account = DataStoreHelper.selectedAccountNumberFlow.value
            scope.launch {
                DataStoreHelper.selectedAccountNumberFlow.collectLatest {
                    if (it != account) {
                        account = it
                        invalidate()
                    }
                }
            }
        }
        foreground = active
        restart()
    }

    @Synchronized
    fun home(cards: List<DataStoreHelper.ResumeWatchingResult>, account: Int) {
        if (account != DataStoreHelper.selectedAccountNumberFlow.value) return
        val next = cards.filterNot { it.isFromDownload }.take(2)
        if (next == home) return
        home = next
        restart()
    }

    @Synchronized
    fun title(owner: Any, target: SourceWarmupTarget?) {
        titleRevision++
        val next = target?.let { owner to it }
        if (next == title) return
        title = next
        restart()
    }

    @Synchronized
    fun releaseTitle(owner: Any) {
        if (title?.first !== owner) return
        val revision = titleRevision
        // Leave the in-flight interest alive briefly while navigation attaches the player.
        scope.launch {
            delay(750)
            synchronized(this@SourceWarmup) {
                if (title?.first === owner && revision == titleRevision) {
                    title = null
                    home = emptyList()
                    restart()
                }
            }
        }
    }

    @Synchronized
    fun stopSpeculation() {
        generation++
        worker?.cancel()
        worker = null
        home = emptyList()
        title = null
    }

    @Synchronized
    fun invalidate() {
        stopSpeculation()
        // An existing player finishes against its captured provider. New requests are isolated
        // by account/provider identity without turning cancellation into apparent completion.
        RepoLinkGenerator.discardUnusedSourceCollections()
    }

    // Called under this object's monitor. A cancelled worker owns only a speculative lease:
    // closing it cannot cancel extraction once Play has attached its own lease.
    private fun restart() {
        generation++
        worker?.cancel()
        worker = null
        val context = context ?: return
        monitorNetwork(context)
        if (!foreground || !enabled || !canWarm(context)) return
        val version = generation
        val account = DataStoreHelper.selectedAccountNumberFlow.value
        val target = title?.second
        val cards = if (target == null) home else emptyList()
        worker = scope.launch {
            delay(600) // Let first render and rapid navigation settle before speculative I/O.
            suspend fun warm(resolve: suspend () -> SourceWarmupTarget?) {
                if (!canWarm(context)) return
                withTimeoutOrNull(45_000) {
                    try {
                        val resolved = resolve() ?: return@withTimeoutOrNull
                        if (!canWarm(context)) return@withTimeoutOrNull
                        synchronized(this@SourceWarmup) {
                            if (version != generation || account != DataStoreHelper.selectedAccountNumberFlow.value) {
                                return@withTimeoutOrNull
                            }
                        }
                        RepoLinkGenerator.warm(resolved, account)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Speculation is best effort; explicit Play retains the normal error UI.
                    }
                }
            }
            if (target != null) warm { target }
            else cards.forEach { card -> warm { resolveSourceWarmupTarget(card) } }
        }
    }

    private fun monitorNetwork(context: Context) {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (!foreground || !enabled) {
            networkCallback?.let { connectivity.unregisterNetworkCallback(it) }
            networkCallback = null
            networkEligible = null
            return
        }
        if (networkCallback != null) return
        networkEligible = canWarm(context)
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun changed() = synchronized(this@SourceWarmup) {
                val eligible = canWarm(context)
                if (networkEligible != eligible) {
                    networkEligible = eligible
                    restart()
                }
            }
            override fun onAvailable(network: Network) = changed()
            override fun onLost(network: Network) = changed()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = changed()
        }
        networkCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivity.registerDefaultNetworkCallback(callback)
        } else {
            connectivity.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        }
    }

    private fun canWarm(context: Context): Boolean =
        context.isNetworkAvailable() &&
            !(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
}
