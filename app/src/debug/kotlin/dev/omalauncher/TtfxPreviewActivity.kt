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
    private val minCols = (logo.lines().maxOfOrNull(String::length) ?: 0) + 4
    private val painter = TtfxPainter(Typeface.MONOSPACE)
    private var animation: TtfxAnimation? = null
    private var finishedAtNanos = 0L
    private var frameCount = 0
    private var elapsedNanos = 0L
    private var previousFrameNanos = 0L

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
        if (previousFrameNanos == 0L || frameTimeNanos - previousFrameNanos >= 16_666_667L) {
            previousFrameNanos = frameTimeNanos
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val started = System.nanoTime()
        val current = animation ?: return
        if (!current.next() && finishedAtNanos == 0L) finishedAtNanos = System.nanoTime()
        if (finishedAtNanos != 0L && System.nanoTime() - finishedAtNanos >= 2_000_000_000L) startEffect()
        animation?.let { painter.draw(canvas, it, Color.WHITE) }

        elapsedNanos += System.nanoTime() - started
        frameCount++
        if (frameCount == 120) {
            Log.i("TtfxPreview", "average next()+draw=${elapsedNanos / frameCount / 1_000_000.0}ms")
            frameCount = 0
            elapsedNanos = 0L
        }
    }

    private fun startEffect() {
        animation?.close()
        val (cols, rows) = painter.fit(width, height, minCols)
        animation = TtfxAnimation.random(logo, cols, rows, frameRate = 60)
        finishedAtNanos = 0L
        Log.i("TtfxPreview", "effect=${animation?.effect} cols=$cols rows=$rows")
    }
}
