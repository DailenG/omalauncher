package dev.omalauncher.ttfx

import org.junit.Assert.assertEquals
import org.junit.Test

class TtfxColorsTest {
    @Test fun blendAtEndpointsAndHalfway() {
        assertEquals(0x10203040, TtfxColors.blend(0x10203040, 0xffa0b0c0.toInt(), 0f))
        assertEquals(0xffa0b0c0.toInt(), TtfxColors.blend(0x10203040, 0xffa0b0c0.toInt(), 1f))
        assertEquals(0x88607080.toInt(), TtfxColors.blend(0x10203040, 0xffa0b0c0.toInt(), 0.5f))
    }

    @Test fun scalesOnlyAlpha() {
        assertEquals(0x40203040, TtfxColors.scaleAlpha(0x80203040.toInt(), 0.5f))
        assertEquals(0x00203040, TtfxColors.scaleAlpha(0x80203040.toInt(), 0f))
    }
}
