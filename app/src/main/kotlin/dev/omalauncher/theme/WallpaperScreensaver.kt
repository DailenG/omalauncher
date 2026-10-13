package dev.omalauncher.theme

import android.app.WallpaperManager
import kotlin.math.floor
import kotlin.math.min

internal object WallpaperScreensaver {
    const val FADE_MS = 600L
    const val MAX_EFFECT_MS = 10_000L
    val EXCLUDED_EFFECTS = setOf("matrix", "thunderstorm")

    data class Band(val rows: Int, val centerY: Float)

    fun band(heightPx: Int, marginPx: Int, cellHeight: Float, textRows: Int, position: Double): Band {
        val logoHeight = textRows * cellHeight
        val inner = heightPx - 2f * marginPx
        if (logoHeight > inner) return Band(textRows, heightPx / 2f)
        val free = (inner - logoHeight).coerceAtLeast(0f)
        val centerY = marginPx + logoHeight / 2f + position.coerceIn(0.0, 1.0).toFloat() * free
        val spare =
            floor(
                min(
                    centerY - marginPx - logoHeight / 2f,
                    heightPx - marginPx - centerY - logoHeight / 2f,
                ) / cellHeight,
            ).toInt().coerceAtLeast(0)
        return Band(textRows + 2 * spare, centerY)
    }

    enum class Trigger { CREATED, SCREEN_ON, USER_PRESENT }

    fun plays(trigger: Trigger, flags: Int?, powerSave: Boolean): Boolean {
        if (powerSave) return false
        return when (trigger) {
            Trigger.CREATED -> true
            Trigger.SCREEN_ON -> flags == null || flags and WallpaperManager.FLAG_LOCK != 0
            Trigger.USER_PRESENT -> flags != null && flags and WallpaperManager.FLAG_SYSTEM != 0 && flags and WallpaperManager.FLAG_LOCK == 0
        }
    }

    fun fade(elapsedMs: Long): Float = (elapsedMs.coerceAtLeast(0L) / FADE_MS.toFloat()).coerceIn(0f, 1f)

    fun staticGrid(lines: List<String>, cols: Int, rows: Int): IntArray {
        require(cols > 0 && rows > 0)
        val grid = IntArray(cols * rows) { 0x20 }
        val start = ((rows - lines.size) / 2).coerceAtLeast(0)
        lines.forEachIndexed { lineIndex, line ->
            val row = start + lineIndex
            if (row !in 0 until rows) return@forEachIndexed
            line.take(cols).forEachIndexed { col, glyph -> grid[row * cols + col] = glyph.code }
        }
        return grid
    }
}
