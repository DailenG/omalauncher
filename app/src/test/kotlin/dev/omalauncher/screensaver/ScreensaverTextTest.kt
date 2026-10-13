package dev.omalauncher.screensaver

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreensaverTextTest {
    @Test fun normalizesCrLf() {
        assertEquals("first\nsecond", ScreensaverText.normalize("first\r\nsecond"))
    }

    @Test fun normalizesLoneCarriageReturns() {
        assertEquals("first\nsecond", ScreensaverText.normalize("first\rsecond"))
    }

    @Test fun expandsTabsToFourColumnStops() {
        assertEquals("    first\nab  second", ScreensaverText.normalize("\tfirst\nab\tsecond"))
    }

    @Test fun trimsTrailingWhitespaceAndOuterBlankLines() {
        assertEquals("first\n\nsecond", ScreensaverText.normalize("  \nfirst   \n \nsecond\t \n"))
    }

    @Test fun whitespaceOnlyTextIsNull() {
        assertNull(ScreensaverText.normalize("  \n\t\n"))
    }

    @Test fun bundledLogoKeepsItsDimensions() {
        val logo = File("../ttfx-android/src/main/assets/ttfx/omarchy-logo.txt").readText(Charsets.UTF_8)
        val normalized = ScreensaverText.normalize(logo) ?: error("bundled logo normalized to null")
        val lines = normalized.lines()
        assertEquals(10, lines.size)
        assertEquals(81, lines.maxOf(String::length))
    }
}
