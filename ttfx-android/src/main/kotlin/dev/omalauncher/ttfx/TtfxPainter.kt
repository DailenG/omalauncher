package dev.omalauncher.ttfx

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

class TtfxPainter(typeface: Typeface) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = 1f
    }
    private val blockPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val strings = HashMap<Int, String>()
    private val textScales = HashMap<Int, Float>()

    val cellWidth: Float get() = paint.measureText("M")
    val cellHeight: Float get() = (paint.fontMetrics.descent - paint.fontMetrics.ascent).coerceAtLeast(0.01f)

    /** Picks the text size to fit at least [minCols] x [minRows] cells; returns the grid for widthPx x heightPx. */
    fun fit(widthPx: Int, heightPx: Int, minCols: Int, minRows: Int = 1): Pair<Int, Int> {
        require(widthPx > 0 && heightPx > 0 && minCols > 0 && minRows > 0)
        paint.textSize = 100f
        val widthAt100 = paint.measureText("M").coerceAtLeast(0.01f)
        val metricsAt100 = paint.fontMetrics
        val heightAt100 = (metricsAt100.descent - metricsAt100.ascent).coerceAtLeast(0.01f)
        val widthSize = 100f * widthPx / (minCols * widthAt100)
        val heightSize = 100f * heightPx / (minRows * heightAt100)
        paint.textSize = min(widthSize, heightSize)
        textScales.clear()
        val metrics = paint.fontMetrics
        val cellWidth = paint.measureText("M")
        val cellHeight = (metrics.descent - metrics.ascent).coerceAtLeast(0.01f)
        return floor(widthPx / cellWidth).toInt().coerceAtLeast(minCols) to
            floor(heightPx / cellHeight).toInt().coerceAtLeast(minRows)
    }

    fun draw(
        canvas: Canvas,
        anim: TtfxAnimation,
        defaultColor: Int,
        bounds: RectF = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()),
        tint: Int = 0,
        tintFraction: Float = 0f,
        alpha: Float = 1f,
    ) {
        draw(canvas, anim.glyphs, anim.colors, anim.cols, anim.rows, defaultColor, bounds, tint, tintFraction, alpha)
    }

    fun draw(
        canvas: Canvas,
        glyphs: IntArray,
        colors: IntArray,
        cols: Int,
        rows: Int,
        defaultColor: Int,
        bounds: RectF = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat()),
        tint: Int = 0,
        tintFraction: Float = 0f,
        alpha: Float = 1f,
    ) {
        require(cols > 0 && rows > 0 && glyphs.size >= cols * rows && colors.size >= cols * rows)
        val left = bounds.left + (bounds.width() - cols * cellWidth) / 2f
        val top = bounds.top + (bounds.height() - rows * cellHeight) / 2f
        val save = canvas.save()
        canvas.clipRect(bounds)
        val metrics = paint.fontMetrics
        for (index in 0 until cols * rows) {
            val glyph = glyphs[index]
            if (glyph == 0x20) continue
            val col = index % cols
            val row = index / cols
            val baseColor = if (colors[index] == 0) defaultColor else colors[index]
            val cellColor = TtfxColors.scaleAlpha(TtfxColors.blend(baseColor, tint, tintFraction), alpha)
            val parts = BlockElements.parts(glyph)
            if (parts != null) {
                val x0 = (left + col * cellWidth).roundToInt()
                val x1 = (left + (col + 1) * cellWidth).roundToInt()
                val y0 = (top + row * cellHeight).roundToInt()
                val y1 = (top + (row + 1) * cellHeight).roundToInt()
                blockPaint.color = cellColor
                val cellAlpha = (cellColor ushr 24) and 0xff
                var part = 0
                while (part < parts.size) {
                    blockPaint.alpha = (cellAlpha * parts[part + 4]).roundToInt()
                    val partLeft = x0 + parts[part] * (x1 - x0)
                    val partTop = y0 + parts[part + 1] * (y1 - y0)
                    val partRight = x0 + parts[part + 2] * (x1 - x0)
                    val partBottom = y0 + parts[part + 3] * (y1 - y0)
                    canvas.drawRect(partLeft, partTop, partRight, partBottom, blockPaint)
                    part += 5
                }
            } else {
                paint.color = cellColor
                val text = strings.getOrPut(glyph) { String(Character.toChars(glyph)) }
                val scale = textScales.getOrPut(glyph) {
                    val advance = paint.measureText(text)
                    if (advance > cellWidth * 1.01f) cellWidth / advance else 1f
                }
                paint.textScaleX = scale
                canvas.drawText(text, left + col * cellWidth, top + row * cellHeight - metrics.ascent, paint)
                paint.textScaleX = 1f
            }
        }
        canvas.restoreToCount(save)
    }
}
