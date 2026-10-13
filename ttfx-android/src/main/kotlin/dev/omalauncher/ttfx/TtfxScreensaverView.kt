package dev.omalauncher.ttfx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Choreographer
import android.view.View
import kotlin.math.roundToInt

class TtfxScreensaverView(
    context: Context,
    private val text: String,
    private val frameRate: Int = 60,
) : View(context), Choreographer.FrameCallback {
    private val lines = text.lines().dropLastWhile { it.isBlank() }
    private val textCols = (lines.maxOfOrNull(String::length) ?: 0).coerceAtLeast(1)
    private val textRows = lines.size.coerceAtLeast(1)
    private val painter = TtfxPainter(Typeface.MONOSPACE)
    private val pacer = TtfxFramePacer(frameRate)
    private var animation: TtfxAnimation? = null
    private var finishedAtNanos = 0L
    private var frameCallbacksActive = false
    private var frameCallbackPosted = false
    private var frameCount = 0
    private var nextElapsedNanos = 0L
    private var drawElapsedNanos = 0L
    private var statsStartedNanos = 0L

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startFrameCallbacks()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            startFrameCallbacks()
        } else {
            stopFrameCallbacks()
        }
    }

    override fun onDetachedFromWindow() {
        stopFrameCallbacks()
        animation?.close()
        animation = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width > 0 && height > 0) startEffect()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackPosted = false
        if (!frameCallbacksActive) return
        if (pacer.shouldDraw(frameTimeNanos)) invalidate()
        postFrameCallback()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = animation ?: return
        val nextStartedNanos = System.nanoTime()
        val running = current.next()
        val nextFinishedNanos = System.nanoTime()
        if (!running && finishedAtNanos == 0L) finishedAtNanos = nextFinishedNanos

        val drawStartedNanos = System.nanoTime()
        painter.draw(canvas, current, Color.WHITE)
        val drawnAtNanos = System.nanoTime()
        if (statsStartedNanos == 0L) statsStartedNanos = drawStartedNanos
        nextElapsedNanos += nextFinishedNanos - nextStartedNanos
        drawElapsedNanos += drawnAtNanos - drawStartedNanos
        frameCount++
        if (frameCount == 120) {
            val wallNanos = (drawnAtNanos - statsStartedNanos).coerceAtLeast(1L)
            val fps = frameCount * 1_000_000_000.0 / wallNanos
            val averageNextMs = nextElapsedNanos / frameCount / 1_000_000.0
            val averageDrawMs = drawElapsedNanos / frameCount / 1_000_000.0
            val nonSpaceCells = current.glyphs.count { it != 0x20 }
            Log.d(
                TAG,
                "effect=${current.effect} cols=${current.cols} rows=${current.rows} fps=$fps " +
                    "next=${averageNextMs}ms draw=${averageDrawMs}ms nonSpace=$nonSpaceCells",
            )
            resetFrameMetrics(drawnAtNanos)
        }
        if (finishedAtNanos != 0L && drawnAtNanos - finishedAtNanos >= EFFECT_HOLD_NANOS) startEffect()
    }

    private fun startFrameCallbacks() {
        frameCallbacksActive = true
        pacer.reset()
        if (animation == null && width > 0 && height > 0) startEffect()
        postFrameCallback()
    }

    private fun stopFrameCallbacks() {
        frameCallbacksActive = false
        pacer.reset()
        if (frameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(this)
            frameCallbackPosted = false
        }
    }

    private fun postFrameCallback() {
        if (!frameCallbacksActive || frameCallbackPosted) return
        Choreographer.getInstance().postFrameCallback(this)
        frameCallbackPosted = true
    }

    private fun startEffect() {
        val pad = (EDGE_PADDING_DP * resources.displayMetrics.density).roundToInt()
        val fw = (width - 2 * pad).coerceAtLeast(1)
        val fh = (height - 2 * pad).coerceAtLeast(1)
        val (fitCols, fitRows) = painter.fit(fw, fh, maxOf(textCols, MIN_FIT_COLS), textRows)
        // ttfx centers text one column right in a wider canvas, so keep the canvas the logo's width.
        val cols = textCols.coerceAtMost(fitCols)
        val rows = fitRows
        val buildStartedNanos = System.nanoTime()
        val next = TtfxAnimation.random(text, cols, rows, frameRate = frameRate)
        val buildNanos = System.nanoTime() - buildStartedNanos
        animation?.close()
        animation = next
        finishedAtNanos = 0L
        resetFrameMetrics()
        Log.i(
            TAG,
            "build=${buildNanos / 1_000_000.0}ms effect=${next.effect} " +
                "fit=${fitCols}x${rows} anim=${cols}x${rows}",
        )
    }

    private fun resetFrameMetrics(startedAtNanos: Long = System.nanoTime()) {
        frameCount = 0
        nextElapsedNanos = 0L
        drawElapsedNanos = 0L
        statsStartedNanos = startedAtNanos
    }

    private companion object {
        const val TAG = "TtfxScreensaver"
        const val EFFECT_HOLD_NANOS = 2_000_000_000L
        const val EDGE_PADDING_DP = 24f
        const val MIN_FIT_COLS = 40
    }
}
