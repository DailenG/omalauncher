package dev.omalauncher.ttfx

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.floor

class TtfxPainter(typeface: Typeface) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = 1f
    }
    private val strings = HashMap<Int, String>()

    /** Picks the text size so [minCols] cells fill [widthPx]; returns the grid (cols, rows) for widthPx x heightPx. */
    fun fit(widthPx: Int, heightPx: Int, minCols: Int): Pair<Int, Int> {
        require(widthPx > 0 && heightPx > 0 && minCols > 0)
        paint.textSize = 1f
        val unitWidth = paint.measureText("M").coerceAtLeast(0.01f)
        paint.textSize = widthPx / (minCols * unitWidth)
        val metrics = paint.fontMetrics
        val cellWidth = paint.measureText("M")
        val cellHeight = (metrics.descent - metrics.ascent).coerceAtLeast(0.01f)
        return floor(widthPx / cellWidth).toInt().coerceAtLeast(minCols) to
            floor(heightPx / cellHeight).toInt().coerceAtLeast(1)
    }

    fun draw(canvas: Canvas, anim: TtfxAnimation, defaultColor: Int) {
        val metrics = paint.fontMetrics
        val cellWidth = paint.measureText("M")
        val cellHeight = metrics.descent - metrics.ascent
        val left = (canvas.width - anim.cols * cellWidth) / 2f
        val top = (canvas.height - anim.rows * cellHeight) / 2f
        val save = canvas.save()
        canvas.clipRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        for (index in anim.glyphs.indices) {
            val glyph = anim.glyphs[index]
            if (glyph == 0x20) continue
            paint.color = if (anim.colors[index] == 0) defaultColor else anim.colors[index]
            val text = strings.getOrPut(glyph) { String(Character.toChars(glyph)) }
            val col = index % anim.cols
            val row = index / anim.cols
            canvas.drawText(text, left + col * cellWidth, top + row * cellHeight - metrics.ascent, paint)
        }
        canvas.restoreToCount(save)
    }
}
