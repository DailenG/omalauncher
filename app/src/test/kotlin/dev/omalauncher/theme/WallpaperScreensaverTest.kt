package dev.omalauncher.theme

import android.app.WallpaperManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperScreensaverTest {
    @Test fun bandMovesAcrossTheInnerRoom() {
        val top = WallpaperScreensaver.band(1000, 100, 20f, 5, 0.0)
        val middle = WallpaperScreensaver.band(1000, 100, 20f, 5, 0.5)
        val bottom = WallpaperScreensaver.band(1000, 100, 20f, 5, 1.0)
        assertTrue(top.centerY < middle.centerY)
        assertTrue(middle.centerY < bottom.centerY)
        assertEquals(0, top.rows - 5)
        assertEquals(0, bottom.rows - 5)
        assertTrue((middle.rows - 5) % 2 == 0)
        assertTrue(middle.rows >= 5)
    }

    @Test fun bandCentersAnOversizedLogo() {
        val band = WallpaperScreensaver.band(100, 20, 30f, 5, 0.0)
        assertEquals(50f, band.centerY, 0f)
        assertEquals(5, band.rows)
    }

    @Test fun everyBandHasEvenNonnegativeSpareRows() {
        for (position in 0..10) {
            val band = WallpaperScreensaver.band(721, 24, 17.3f, 7, position / 10.0)
            assertTrue(band.rows >= 7)
            assertEquals(0, (band.rows - 7) % 2)
        }
    }

    @Test fun triggerRulesAndPowerSave() {
        assertTrue(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.CREATED, null, false))
        assertTrue(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.SCREEN_ON, null, false))
        assertTrue(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.SCREEN_ON, WallpaperManager.FLAG_LOCK, false))
        assertFalse(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.SCREEN_ON, WallpaperManager.FLAG_SYSTEM, false))
        assertTrue(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.USER_PRESENT, WallpaperManager.FLAG_SYSTEM, false))
        assertFalse(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.USER_PRESENT, WallpaperManager.FLAG_LOCK, false))
        assertFalse(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.USER_PRESENT, null, false))
        assertFalse(WallpaperScreensaver.plays(WallpaperScreensaver.Trigger.CREATED, null, true))
    }

    @Test fun fadeClampsOverSixHundredMilliseconds() {
        assertEquals(0f, WallpaperScreensaver.fade(0), 0f)
        assertEquals(0.5f, WallpaperScreensaver.fade(300), 0f)
        assertEquals(1f, WallpaperScreensaver.fade(600), 0f)
        assertEquals(1f, WallpaperScreensaver.fade(900), 0f)
    }

    @Test fun staticGridCentersLinesAndLeavesSpaces() {
        val grid = WallpaperScreensaver.staticGrid(listOf("AB", "C"), 4, 4)
        assertEquals(0x20, grid[0])
        assertEquals('A'.code, grid[4 + 0])
        assertEquals('B'.code, grid[4 + 1])
        assertEquals('C'.code, grid[8 + 0])
        assertEquals(0x20, grid[8 + 1])
        assertTrue(grid.all { it == 0x20 || it in 'A'.code..'C'.code })
    }
}
