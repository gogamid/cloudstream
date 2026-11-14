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
    @Test fun `held seeking accelerates at two second boundaries using the saved amount`() {
        val cases = listOf(
            -1L to 1, 0L to 1, 1_999L to 1,
            2_000L to 2, 3_999L to 2,
            4_000L to 4, 5_999L to 4,
            6_000L to 8, 7_999L to 8,
            8_000L to 16, 60_000L to 16, Long.MAX_VALUE to 16
        )
        for (seconds in listOf(5, 10)) {
            for ((heldMs, multiplier) in cases) {
                assertEquals("${seconds}s held for ${heldMs}ms",
                    seconds * 1_000L * multiplier,
                    SeekDurationPolicy.heldAmountMs(amount(hidden = seconds), heldMs))
            }
        }
        // Positive legacy preferences must not overflow or get clamped to the slider range.
        assertEquals(Int.MAX_VALUE.toLong() * 1_000L * 16,
            SeekDurationPolicy.heldAmountMs(amount(hidden = Int.MAX_VALUE), 8_000L))
    }

    @Test fun `changing visibility changes the effective amount without a reload`() {
        assertEquals(listOf(5_000L, 10_000L, 5_000L),
            listOf(false, true, false).map { amount(visible = it) })
    }
}
