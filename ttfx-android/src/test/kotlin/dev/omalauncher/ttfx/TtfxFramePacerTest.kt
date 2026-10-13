package dev.omalauncher.ttfx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtfxFramePacerTest {
    @Test
    fun pacesToSixtyFramesPerSecondAtCommonRefreshRates() {
        val durationNanos = 10_000_000_000L
        for (refreshRate in listOf(60, 90, 120, 144)) {
            val pacer = TtfxFramePacer(60)
            val vsyncInterval = 1_000_000_000L / refreshRate
            var drawn = 0
            var frameTime = 0L
            while (frameTime < durationNanos) {
                if (pacer.shouldDraw(frameTime)) drawn++
                frameTime += vsyncInterval
            }
            val fps = drawn / (durationNanos / 1_000_000_000.0)
            assertTrue("$refreshRate Hz yielded $fps fps", fps in 58.0..61.0)
        }
    }

    @Test
    fun stallResetsDeadlineInsteadOfDrawingEveryFollowingVsync() {
        val pacer = TtfxFramePacer(60)
        assertTrue(pacer.shouldDraw(0L))
        assertTrue(pacer.shouldDraw(16_666_667L))
        val afterStall = 216_666_667L
        assertTrue(pacer.shouldDraw(afterStall))
        assertFalse(pacer.shouldDraw(afterStall + 8_333_333L))
    }
}
