package com.lagradost.cloudstream3.ui.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Call
import okhttp3.Request

/** One selection owns its calls, including calls created after cancellation. */
internal class SubtitleCallFactory(private val delegate: Call.Factory) : Call.Factory {
    private val calls = mutableListOf<Call>()
    private var cancelled = false

    @Synchronized
    override fun newCall(request: Request): Call = delegate.newCall(request).also {
        if (cancelled) it.cancel() else calls.add(it)
    }

    @Synchronized
    fun cancel() {
        cancelled = true
        calls.forEach { it.cancel() }
        calls.clear()
    }
}

/** Cancel the HTTP call itself: closing a DataSource cannot abort a pending open. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SubtitleRequestFactory(
    client: Call.Factory,
    headers: Map<String, String>,
    userAgent: String,
) : DataSource.Factory {
    private val calls = SubtitleCallFactory(client)
    private val delegate = OkHttpDataSource.Factory(calls)
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(headers)

    override fun createDataSource(): DataSource = delegate.createDataSource()
    fun cancel() = calls.cancel()
}
