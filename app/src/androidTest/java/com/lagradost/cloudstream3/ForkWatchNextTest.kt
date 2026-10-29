package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.lagradost.cloudstream3.utils.AppContextUtils.addProgramsToContinueWatching
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.PosDur
import com.lagradost.cloudstream3.utils.DataStoreHelper.ResumeWatchingResult
import com.lagradost.cloudstream3.utils.DataStoreHelper.removeLastWatched
import com.lagradost.cloudstream3.utils.DataStoreHelper.setLastWatched
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Production export + Android TV program serialization, without a TV-only system provider.
 * The phone emulator supplies Android's real ContentResolver/Cursor APIs; only storage is local.
 */
@SuppressLint("RestrictedApi")
@RunWith(AndroidJUnit4::class)
class ForkWatchNextTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class ProgramProvider : ContentProvider() {
        private val rows = linkedMapOf<Long, ContentValues>()
        private var nextId = 1L
        override fun onCreate() = true
        override fun getType(uri: Uri) = "vnd.android.cursor.item/watch_next_program"
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val id = nextId++
            rows[id] = ContentValues(requireNotNull(values)).apply { put("_id", id) }
            return ContentUris.withAppendedId(uri, id)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
            val columns = projection ?: WatchNextProgram.PROJECTION
            val id = uri.lastPathSegment?.toLongOrNull()
            return MatrixCursor(columns).apply {
                rows.filterKeys { id == null || it == id }.values.forEach { row ->
                    addRow(columns.map { row.get(it) }.toTypedArray())
                }
            }
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
                            selectionArgs: Array<out String>?): Int {
            val row = rows[ContentUris.parseId(uri)] ?: return 0
            row.putAll(requireNotNull(values))
            return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
            if (rows.remove(ContentUris.parseId(uri)) == null) 0 else 1
    }

    private fun card(name: String, parent: Int, episodeId: Int, episode: Int = 1) =
        ResumeWatchingResult(
            name = name, url = "https://example.invalid/$parent", apiName = "Watch Next fixture",
            type = TvType.TvSeries, posterUrl = "https://example.invalid/$parent.jpg",
            watchPos = PosDur(21_000, 3_600_000), id = episodeId, parentId = parent,
            episode = episode, season = 3, isFromDownload = false,
        )

    private fun programs(resolver: ContentResolver): Map<Long, WatchNextProgram> =
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION, null, null, null)!!.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getLong(cursor.getColumnIndexOrThrow("_id")), WatchNextProgram.fromCursor(cursor))
                }
            }
        }

    private fun fixture(block: suspend (ContextWrapper) -> Unit) = runBlocking {
        val previousAccount = DataStoreHelper.selectedKeyIndex
        DataStoreHelper.selectedKeyIndex = 9001
        val provider = ProgramProvider().apply {
            attachInfo(target, ProviderInfo().apply { authority = "android.media.tv" })
        }
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(target) {
            override fun getContentResolver() = resolver
        }
        try { block(context) } finally {
            for (account in listOf(9001, 9002)) {
                DataStoreHelper.selectedKeyIndex = account
                listOf(101, 202).forEach { removeLastWatched(it) }
            }
            DataStoreHelper.selectedKeyIndex = previousAccount
        }
    }

    @Test fun realWatchTimesProgressAndResumeEpisodesSurviveRepeatedExport() = fixture { context ->
        val westworld = card("Westworld", 101, -1008, 8)
            .copy(watchPos = PosDur(2_205_000, 3_600_000))
        val bref = card("Bref", 202, -2002, 2)
        setLastWatched(101, -1008, 8, 3, updateTime = 1_700_000_090_000)
        setLastWatched(202, -2002, 2, 3, updateTime = 1_700_000_000_000)
        // Simulate an old URL-only entry: the next sync must replace, not retain it.
        context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.Builder().setTitle("Stale card")
                .setInternalProviderId(westworld.url).build().toContentValues())
        context.addProgramsToContinueWatching(listOf(westworld, bref))
        val first = programs(context.contentResolver)
        assertEquals(2, first.size)
        val ordered = first.values.sortedByDescending { it.lastEngagementTimeUtcMillis }
        assertTrue(ordered[0].title.contains("Westworld"))
        assertTrue(ordered[1].title.contains("Bref"))
        val west = ordered[0]
        assertEquals(1_700_000_090_000, west.lastEngagementTimeUtcMillis)
        assertEquals(2_205_000, west.lastPlaybackPositionMillis)
        assertEquals(3_600_000, west.durationMillis)
        assertEquals("-1008", west.intentUri.authority)
        assertEquals("9001", west.intentUri.getQueryParameter("account"))
        val westId = first.entries.single { it.value.title.contains("Westworld") }.key

        // Refreshing does not manufacture recent engagement or replace the card's row ID.
        context.addProgramsToContinueWatching(listOf(westworld, bref))
        assertEquals(first.keys, programs(context.contentResolver).keys)
        assertEquals(1_700_000_090_000, programs(context.contentResolver).getValue(westId).lastEngagementTimeUtcMillis)

        setLastWatched(101, -1009, 9, 3, updateTime = 1_700_000_120_000)
        val next = westworld.copy(id = -1009, episode = 9, watchPos = PosDur(0, 3_700_000))
        context.addProgramsToContinueWatching(listOf(next, bref))
        val updated = programs(context.contentResolver).getValue(westId)
        assertEquals("-1009", updated.intentUri.authority)
        assertEquals(0, updated.lastPlaybackPositionMillis)
        assertEquals(3_700_000, updated.durationMillis)
        assertEquals(1_700_000_120_000, updated.lastEngagementTimeUtcMillis)

        context.addProgramsToContinueWatching(listOf(next))
        assertEquals(setOf(westId), programs(context.contentResolver).keys)
        context.addProgramsToContinueWatching(emptyList())
        assertTrue(programs(context.contentResolver).isEmpty())
    }

    @Test fun profileReplacementRejectsAnOldRefreshAndKeepsMovieProgress() = fixture { context ->
        val series = card("Westworld", 101, -1008, 8)
        setLastWatched(101, -1008, 8, 3, updateTime = 1_700_000_090_000)
        context.addProgramsToContinueWatching(listOf(series))
        val oldId = programs(context.contentResolver).keys.single()
        val oldGeneration = DataStoreHelper.selectedAccountNumberFlow.value
        DataStoreHelper.setAccount(DataStoreHelper.Account(9002, "Watch Next test", defaultImageIndex = 0), false)
        val movie = card("Movie", 202, 202).copy(type = TvType.Movie,
            episode = null, season = null, watchPos = PosDur(17_000, 7_200_000))
        setLastWatched(202, 202, null, null, updateTime = 1_600_000_000_000)
        context.addProgramsToContinueWatching(listOf(movie))
        val rows = programs(context.contentResolver)
        assertEquals(1, rows.size)
        assertFalse(rows.containsKey(oldId))
        val result = rows.values.single()
        assertEquals("Movie", result.title)
        assertEquals(TvContractCompat.WatchNextPrograms.TYPE_MOVIE, result.type)
        assertEquals(17_000, result.lastPlaybackPositionMillis)
        assertEquals(7_200_000, result.durationMillis)
        assertEquals("9002", result.intentUri.getQueryParameter("account"))
        context.addProgramsToContinueWatching(listOf(series), oldGeneration)
        assertEquals(rows.keys, programs(context.contentResolver).keys)
        assertEquals("Movie", programs(context.contentResolver).values.single().title)
    }
}
