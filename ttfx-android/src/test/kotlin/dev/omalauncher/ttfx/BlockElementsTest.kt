package dev.omalauncher.ttfx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockElementsTest {
    @Test fun nonBlockGlyphsHaveNoParts() {
        assertNull(BlockElements.parts(0x41))
    }

    @Test fun fullAndHalfBlocks() {
        assertParts(0x2588, floatArrayOf(0f, 0f, 1f, 1f, 1f))
        assertParts(0x2580, floatArrayOf(0f, 0f, 1f, 0.5f, 1f))
        assertParts(0x2584, floatArrayOf(0f, 0.5f, 1f, 1f, 1f))
    }

    @Test fun eighthBlocks() {
        assertParts(0x2581, floatArrayOf(0f, 0.875f, 1f, 1f, 1f))
        assertParts(0x2589, floatArrayOf(0f, 0f, 0.875f, 1f, 1f))
        assertParts(0x258f, floatArrayOf(0f, 0f, 0.125f, 1f, 1f))
    }

    @Test fun rightAndShadeBlocks() {
        assertParts(0x2590, floatArrayOf(0.5f, 0f, 1f, 1f, 1f))
        assertParts(0x2591, floatArrayOf(0f, 0f, 1f, 1f, 0.25f))
    }

    @Test fun quadrantCombinationsKeepTheirSpecifiedOrder() {
        assertParts(
            0x259a,
            floatArrayOf(0f, 0f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 1f, 1f, 1f),
        )
        assertParts(
            0x259f,
            floatArrayOf(0.5f, 0f, 1f, 0.5f, 1f, 0f, 0.5f, 0.5f, 1f, 1f, 0.5f, 0.5f, 1f, 1f, 1f),
        )
    }

    @Test fun everyBlockCodepointHasValidParts() {
        for (codepoint in 0x2580..0x259f) {
            val parts = BlockElements.parts(codepoint) ?: error("U+${codepoint.toString(16)} has no parts")
            assertTrue("U+${codepoint.toString(16)} has no rectangles", parts.isNotEmpty())
            assertTrue("U+${codepoint.toString(16)} has an incomplete tuple", parts.size % 5 == 0)
            assertTrue("U+${codepoint.toString(16)} has out-of-range values", parts.all { it in 0f..1f })
        }
    }

    private fun assertParts(codepoint: Int, expected: FloatArray) {
        val actual = BlockElements.parts(codepoint) ?: error("U+${codepoint.toString(16)} has no parts")
        assertArrayEquals(expected, actual, 0f)
    }
}
