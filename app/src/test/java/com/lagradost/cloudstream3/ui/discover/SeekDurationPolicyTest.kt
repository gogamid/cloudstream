package com.lagradost.cloudstream3.ui.discover

import com.lagradost.cloudstream3.ui.player.SeekBurstController
import com.lagradost.cloudstream3.ui.player.SeekDurationPolicy
import org.junit.Assert.*
import org.junit.Test

class SeekDurationPolicyTest {
    private fun amount(tv: Boolean = true, visible: Boolean = false,
                       tap: Int = 15, shown: Int = 10, hidden: Int = 5) =
        SeekDurationPolicy.amountMs(tv, visible, tap, shown, hidden)

    @Test fun `hidden TV controls respect five seconds not fixed thirty`() {
        assertEquals(5_000L, amount())
    }
    @Test fun `shown TV controls use their independent ten second setting`() {
        assertEquals(10_000L, amount(visible = true))
    }
    @Test fun `phone controls and gestures use double tap setting`() {
        assertEquals(15_000L, amount(tv = false))
        assertEquals(15_000L, amount(tv = false, visible = true))
    }
    @Test fun `all slider amounts survive without quantization or forced defaults`() {
        for (seconds in 5..60) {
            assertEquals(seconds * 1_000L, amount(hidden = seconds))
            assertEquals(seconds * 1_000L, amount(visible = true, shown = seconds))
            assertEquals(seconds * 1_000L, amount(tv = false, tap = seconds))
        }
    }
    @Test fun `only the chosen setting is validated`() {
        assertEquals(5_000L, amount(tap = -1, shown = 0))
        assertEquals(10_000L, amount(visible = true, tap = 0, hidden = -5))
    }
    @Test fun `invalid amounts safely default without modifying stored values`() {
        for (seconds in listOf(0, -1, Int.MIN_VALUE)) {
            assertEquals(10_000L, amount(hidden = seconds))
            assertEquals(10_000L, amount(visible = true, shown = seconds))
            assertEquals(10_000L, amount(tv = false, tap = seconds))
        }
    }
    @Test fun `positive legacy values outside slider range are preserved`() {
        for (seconds in listOf(1, 2, 120, Int.MAX_VALUE)) {
            assertEquals(seconds.toLong() * 1_000L, amount(hidden = seconds))
        }
    }
    @Test fun `batching accumulates configured five or ten seconds per press`() {
        for (seconds in listOf(5, 10)) {
            val seeks = mutableListOf<Long>()
            var preview: SeekBurstController.Target? = null
            val burst = SeekBurstController(
                position = { 120_000L }, duration = { 300_000L }, intent = { false },
                pause = {}, seek = { seeks += it }, restore = {}, preview = { preview = it },
                schedule = { _, _ -> }, remove = {}
            )
            repeat(3) { burst.add(-amount(hidden = seconds)) }
            assertTrue(seeks.isEmpty())
            val expected = 120_000L - 3 * seconds * 1_000L
            assertEquals(expected, preview?.toMs)
            burst.finish()
            assertEquals(listOf(expected), seeks)
        }
    }
    @Test fun `changing visibility changes the effective amount without a reload`() {
        assertEquals(listOf(5_000L, 10_000L, 5_000L),
            listOf(false, true, false).map { amount(visible = it) })
    }
}
