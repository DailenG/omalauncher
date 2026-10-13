package dev.omalauncher

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import dev.omalauncher.ttfx.TtfxAnimation
import dev.omalauncher.ttfx.TtfxFramePacer
import dev.omalauncher.ttfx.TtfxPainter

class TtfxPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(TtfxPreviewView(this).apply { setOnClickListener { finish() } })
    }
}

private class TtfxPreviewView(context: android.content.Context) : View(context), Choreographer.FrameCallback {
    private val logo = TtfxAnimation.omarchyLogo(context)
    private val logoCols = logo.lines().maxOfOrNull(String::length) ?: 0
    private val minCols = logoCols + 4
    private val painter = TtfxPainter(Typeface.MONOSPACE)
    private val pacer = TtfxFramePacer(60)
    private var animation: TtfxAnimation? = null
    private var finishedAtNanos = 0L
    private var frameCount = 0
    private var nextElapsedNanos = 0L
    private var drawElapsedNanos = 0L
    private var statsStartedNanos = 0L

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pacer.reset()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        animation?.close()
        animation = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        if (width > 0 && height > 0) startEffect()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (pacer.shouldDraw(frameTimeNanos)) invalidate()
        Choreographer.getInstance().postFrameCallback(this)
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
            Log.i(
                "TtfxPreview",
                "effect=${current.effect} cols=${current.cols} rows=${current.rows} fps=$fps " +
                    "next=${averageNextMs}ms draw=${averageDrawMs}ms nonSpace=$nonSpaceCells",
            )
            resetFrameMetrics(drawnAtNanos)
        }
        if (finishedAtNanos != 0L && drawnAtNanos - finishedAtNanos >= 2_000_000_000L) startEffect()
    }

    private fun startEffect() {
        val (fitCols, rows) = painter.fit(width, height, minCols)
        // ttfx centers text one column right in a wider canvas, so keep the canvas the logo's width.
        val cols = logoCols.coerceAtMost(fitCols)
        val buildStartedNanos = System.nanoTime()
        val next = TtfxAnimation.random(logo, cols, rows, frameRate = 60)
        val buildNanos = System.nanoTime() - buildStartedNanos
        animation?.close()
        animation = next
        finishedAtNanos = 0L
        resetFrameMetrics()
        Log.i(
            "TtfxPreview",
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
}
