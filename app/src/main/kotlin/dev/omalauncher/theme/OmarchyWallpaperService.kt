package dev.omalauncher.theme

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import dev.omalauncher.LauncherData
import dev.omalauncher.screensaver.ScreensaverText
import dev.omalauncher.ttfx.TtfxAnimation
import dev.omalauncher.ttfx.TtfxFramePacer
import dev.omalauncher.ttfx.TtfxPainter
import dev.omalauncher.ui.decodeForScreen
import dev.omalauncher.ui.wallpaperCrop
import java.io.File
import kotlin.math.roundToInt
import java.util.concurrent.Executors

private const val TAG = "OmarchyWallpaper"
private const val FILE_RELOAD_DELAY_MS = 150L
private val FILE_EVENT_MASK =
    FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or FileObserver.DELETE

/**
 * Android live wallpaper showing the current Omarchy background. Its main
 * purpose is [Engine.onComputeColors]: Android 12+ derives the system color
 * scheme (quick settings, notifications, Settings, keyboard, dynamic-color
 * apps) from the colors the wallpaper reports, so we report the theme's
 * palette instead of colors sampled from the image.
 */
class OmarchyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = OmarchyEngine()

    private data class ScreensaverLayout(
        val cols: Int,
        val rows: Int,
        val bounds: RectF,
        val grid: IntArray,
    )

    private inner class OmarchyEngine : Engine() {
        private val main = Handler(Looper.getMainLooper())
        private val decoder = Executors.newSingleThreadExecutor()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        private var colors: Map<String, String> = emptyMap()
        private var bitmap: Bitmap? = null
        private var loadedPath: String? = null
        private var style = Style()
        private var screensaverText = ""
        private val painter = TtfxPainter(android.graphics.Typeface.MONOSPACE)
        private val pacer = TtfxFramePacer(60)
        private val choreographer = Choreographer.getInstance()
        private var animation: TtfxAnimation? = null
        private var framesDrawn = 0
        private var animationStartedAt = 0L
        private var fadeStartedAt = 0L
        private var firstFrameLogged = false
        private var effectEndReason = ""
        private var lastDrawState: String? = null
        private var framePosted = false
        private var pendingTrigger = false
        private var layout: ScreensaverLayout? = null
        private var lastSurface: WallpaperScreensaver.Surface? = null
        private var styleObserver: FileObserver? = null
        private var screensaverTextObserver: FileObserver? = null
        private val reloadRunnable = Runnable { reload() }

        /** Where the launcher's pages are scrolled, 0 to 1: the background slides along. */
        private var xOffset = 0.5f

        private val onThemeChanged: () -> Unit = { main.post { reload() } }
        private val onStyleChanged: () -> Unit = { main.post { reload() } }
        private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
            framePosted = false
            if (animation == null || !isVisible) return@FrameCallback
            val now = android.os.SystemClock.uptimeMillis()
            if (!firstFrameLogged) {
                firstFrameLogged = true
                Log.d(TAG, "first frame callback after=${now - animationStartedAt}ms")
            }
            if (pacer.shouldDraw(frameTimeNanos)) {
                val current = animation ?: return@FrameCallback
                if (fadeStartedAt == 0L) {
                    val hasNext = current.next()
                    val maxEffectTimeReached = now - animationStartedAt >= WallpaperScreensaver.MAX_EFFECT_MS
                    if (!hasNext || maxEffectTimeReached) {
                        effectEndReason = if (!hasNext) "next()==false" else "MAX_EFFECT_MS"
                        fadeStartedAt = now
                    }
                }
                if (fadeStartedAt != 0L && now - fadeStartedAt >= WallpaperScreensaver.FADE_MS) {
                    settle()
                    return@FrameCallback
                }
                draw()
            }
            postFrame()
        }

        private val triggerReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_SCREEN_ON) return
                    trigger(WallpaperScreensaver.Trigger.SCREEN_ON, intent.action.orEmpty())
                }
            }

        private fun fileObserver(
            directory: File,
            fileName: String,
        ): FileObserver =
            object : FileObserver(directory, FILE_EVENT_MASK) {
                override fun onEvent(
                    event: Int,
                    path: String?,
                ) {
                    if (path == fileName) {
                        main.removeCallbacks(reloadRunnable)
                        main.postDelayed(reloadRunnable, FILE_RELOAD_DELAY_MS)
                    }
                }
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            ThemeStore.changeListeners.add(onThemeChanged)
            StyleStore.changeListeners.add(onStyleChanged)
            ContextCompat.registerReceiver(
                this@OmarchyWallpaperService,
                triggerReceiver,
                IntentFilter(Intent.ACTION_SCREEN_ON),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            style = StyleStore.read(this@OmarchyWallpaperService)
            screensaverText = ScreensaverText.load(this@OmarchyWallpaperService)
            pendingTrigger = style[Style.WALLPAPER_SCREENSAVER]
            startWatchingFiles()
            Log.d(TAG, "onCreate isPreview=$isPreview flag=${screenSaverEnabled()}")
            reload()
        }

        override fun onDestroy() {
            ThemeStore.changeListeners.remove(onThemeChanged)
            StyleStore.changeListeners.remove(onStyleChanged)
            runCatching { unregisterReceiver(triggerReceiver) }
            main.removeCallbacks(reloadRunnable)
            styleObserver?.stopWatching()
            styleObserver = null
            screensaverTextObserver?.stopWatching()
            screensaverTextObserver = null
            stopAnimation("destroy")
            decoder.shutdownNow()
            super.onDestroy()
        }

        private fun startWatchingFiles() {
            styleObserver =
                fileObserver(LauncherData.styleDir(this@OmarchyWallpaperService), StyleStore.FILE)
                    .also { it.startWatching() }
            screensaverTextObserver =
                fileObserver(LauncherData.brandingDir(this@OmarchyWallpaperService), ScreensaverText.FILE)
                    .also { it.startWatching() }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            val resized = WallpaperScreensaver.surfaceResized(lastSurface, width, height, format)
            lastSurface = WallpaperScreensaver.Surface(width, height, format)
            if (!resized) {
                Log.d(TAG, "onSurfaceChanged unchanged")
                if (animation == null) draw()
                return
            }
            Log.d(TAG, "onSurfaceChanged ${width}x$height")
            layout = null
            if (animation != null) {
                stopAnimation("resized")
                startEffect()
            } else {
                draw()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "onSurfaceCreated")
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            Log.d(TAG, "onSurfaceRedrawNeeded")
            if (animation == null) draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "onSurfaceDestroyed")
            lastSurface = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            if (screenSaverEnabled()) return
            if (xOffset == this.xOffset) return
            this.xOffset = xOffset
            if (isVisible) draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            Log.d(TAG, "onVisibilityChanged visible=$visible pendingTrigger=$pendingTrigger animation=${animation != null}")
            if (!screenSaverEnabled()) {
                if (visible) draw()
                return
            }
            if (!visible) {
                stopAnimation("hidden")
            } else if (pendingTrigger) {
                startEffect()
            } else {
                draw()
            }
        }

        /** Reads the saved theme off the main thread, then redraws with it. */
        private fun reload() {
            // A change posted just before the engine was destroyed.
            if (decoder.isShutdown) return
            val wasEnabled = screenSaverEnabled()
            val oldText = screensaverText
            val oldPosition = style[Style.WALLPAPER_SCREENSAVER_POSITION]
            val oldSize = style[Style.WALLPAPER_SCREENSAVER_SIZE]
            decoder.execute {
                val loadedStyle = StyleStore.read(this@OmarchyWallpaperService)
                val loadedText = ScreensaverText.load(this@OmarchyWallpaperService)
                val (themeColors, file) = ThemeStore.savedSelection(this@OmarchyWallpaperService) ?: (emptyMap<String, String>() to null)
                val decoded = if (file?.path != loadedPath) file?.let { decodeForScreen(it, this@OmarchyWallpaperService) } else null
                main.post {
                    style = loadedStyle
                    screensaverText = loadedText
                    val enabled = screenSaverEnabled()
                    val outcome =
                        WallpaperScreensaver.reload(
                            wasEnabled,
                            enabled,
                            oldText,
                            loadedText,
                            oldPosition,
                            style[Style.WALLPAPER_SCREENSAVER_POSITION],
                            oldSize,
                            style[Style.WALLPAPER_SCREENSAVER_SIZE],
                        )
                    Log.d(TAG, "reload outcome=$outcome")
                    when (outcome) {
                        WallpaperScreensaver.Reload.OFF -> {
                            pendingTrigger = false
                            stopAnimation("reload off")
                            layout = null
                        }
                        WallpaperScreensaver.Reload.REPLAY -> {
                            pendingTrigger = true
                            stopAnimation("reload replay")
                            layout = null
                        }
                        WallpaperScreensaver.Reload.RELAYOUT -> {
                            stopAnimation("reload relayout")
                            layout = null
                        }
                        WallpaperScreensaver.Reload.REDRAW -> Unit
                    }
                    val colorsChanged = themeColors != colors
                    colors = themeColors
                    if (file?.path != loadedPath) {
                        loadedPath = file?.path
                        bitmap?.recycle()
                        bitmap = decoded
                    }
                    if (enabled && pendingTrigger && isVisible) startEffect() else if (animation == null) draw()
                    if (colorsChanged) notifyColorsChanged()
                }
            }
        }

        private fun screenSaverEnabled(): Boolean = style[Style.WALLPAPER_SCREENSAVER]

        private fun trigger(
            trigger: WallpaperScreensaver.Trigger,
            action: String,
        ) {
            if (decoder.isShutdown) return
            decoder.execute {
                val loadedStyle = StyleStore.read(this@OmarchyWallpaperService)
                val loadedText = ScreensaverText.load(this@OmarchyWallpaperService)
                main.post {
                    style = loadedStyle
                    screensaverText = loadedText
                    val powerSave = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                    val plays = WallpaperScreensaver.plays(trigger, powerSave)
                    Log.d(TAG, "trigger action=$action powerSave=$powerSave plays=$plays isVisible=$isVisible")
                    if (!screenSaverEnabled()) return@post
                    if (!plays) {
                        pendingTrigger = false
                        stopAnimation("trigger ineligible")
                        layout = null
                        draw()
                        return@post
                    }
                    pendingTrigger = true
                    if (isVisible) startEffect()
                }
            }
        }

        private fun layoutFor(width: Int, height: Int): ScreensaverLayout {
            val lines = screensaverText.split('\n').ifEmpty { listOf(" ") }
            val textCols = lines.maxOf { it.length }.coerceAtLeast(1)
            val textRows = lines.size.coerceAtLeast(1)
            val margin = (24f * resources.displayMetrics.density).roundToInt()
            val inner = (height - 2 * margin).coerceAtLeast(1)
            val areaWidth = ((width - 2 * margin).coerceAtLeast(1) * style[Style.WALLPAPER_SCREENSAVER_SIZE]).roundToInt().coerceAtLeast(1)
            painter.fit(areaWidth, inner, maxOf(textCols, 40), textRows)
            val band = WallpaperScreensaver.band(height, margin, painter.cellHeight, textRows, style[Style.WALLPAPER_SCREENSAVER_POSITION])
            val effectHeight = band.rows * painter.cellHeight
            val bounds = RectF(0f, band.centerY - effectHeight / 2f, width.toFloat(), band.centerY + effectHeight / 2f)
            return ScreensaverLayout(textCols, band.rows, bounds, WallpaperScreensaver.staticGrid(lines, textCols, band.rows))
        }

        private fun startEffect() {
            if (!screenSaverEnabled()) {
                Log.d(TAG, "startEffect skipped: disabled")
                return
            }
            if (!isVisible) {
                Log.d(TAG, "startEffect skipped: not visible")
                return
            }
            if (getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) {
                Log.d(TAG, "startEffect skipped: power save")
                pendingTrigger = false
                stopAnimation("power save")
                draw()
                return
            }
            val holder = surfaceHolder
            val width = holder.surfaceFrame.width()
            val height = holder.surfaceFrame.height()
            if (width <= 0 || height <= 0) {
                Log.d(TAG, "startEffect skipped: surface ${width}x$height")
                return
            }
            val currentLayout = layoutFor(width, height)
            layout = currentLayout
            val nextAnimation =
                TtfxAnimation.random(
                    screensaverText,
                    currentLayout.cols,
                    currentLayout.rows,
                    60,
                    exclude = WallpaperScreensaver.EXCLUDED_EFFECTS,
                )
            stopAnimation("restart")
            animation = nextAnimation
            Log.d(TAG, "startEffect effect=${nextAnimation.effect} ${currentLayout.cols}x${currentLayout.rows} surface=${width}x${height}")
            pendingTrigger = false
            animationStartedAt = android.os.SystemClock.uptimeMillis()
            fadeStartedAt = 0L
            framesDrawn = 0
            firstFrameLogged = false
            effectEndReason = ""
            lastDrawState = null
            pacer.reset()
            postFrame()
        }

        private fun postFrame() {
            if (!framePosted && animation != null && isVisible) {
                framePosted = true
                choreographer.postFrameCallback(frameCallback)
            }
        }

        private fun stopAnimation(reason: String) {
            val wasRunning = animation != null
            if (wasRunning) Log.d(TAG, "stopAnimation reason=$reason")
            if (framePosted) {
                choreographer.removeFrameCallback(frameCallback)
                framePosted = false
            }
            animation?.close()
            animation = null
            animationStartedAt = 0L
            fadeStartedAt = 0L
            pacer.reset()
        }

        private fun settle() {
            val elapsed = android.os.SystemClock.uptimeMillis() - animationStartedAt
            Log.d(TAG, "settle frames=$framesDrawn elapsed=${elapsed}ms via=$effectEndReason")
            stopAnimation("settled")
            pendingTrigger = false
            draw()
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas =
                runCatching { holder.lockHardwareCanvas() }
                    .onFailure { Log.w(TAG, "lockHardwareCanvas failed", it) }
                    .getOrNull() ?: return
            try {
                canvas.drawColor(color("background", Color.BLACK))
                if (screenSaverEnabled()) {
                    val currentLayout = layout ?: layoutFor(canvas.width, canvas.height).also { layout = it }
                    val current = animation
                    if (current != null) {
                        val tintFraction =
                            if (fadeStartedAt == 0L) 0f else WallpaperScreensaver.fade(android.os.SystemClock.uptimeMillis() - fadeStartedAt)
                        val fadeAlpha = (1f + (style[Style.WALLPAPER_SCREENSAVER_DIM] - 1.0) * tintFraction).toFloat()
                        painter.draw(
                            canvas,
                            current,
                            color("foreground", Color.WHITE),
                            currentLayout.bounds,
                            color("accent", Color.WHITE),
                            tintFraction,
                            fadeAlpha,
                        )
                        logDrawState("animation")
                        framesDrawn++
                    } else {
                        painter.draw(
                            canvas,
                            currentLayout.grid,
                            IntArray(currentLayout.grid.size),
                            currentLayout.cols,
                            currentLayout.rows,
                            color("accent", Color.WHITE),
                            currentLayout.bounds,
                            alpha = style[Style.WALLPAPER_SCREENSAVER_DIM].toFloat(),
                        )
                        logDrawState("still")
                    }
                } else {
                    lastDrawState = "image"
                    bitmap?.let { bmp ->
                        val src = wallpaperCrop(bmp.width, bmp.height, canvas.width, canvas.height, xOffset)
                        canvas.drawBitmap(bmp, src, Rect(0, 0, canvas.width, canvas.height), paint)
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun logDrawState(state: String) {
            if (lastDrawState != state) {
                Log.d(TAG, "draw state=$state")
                lastDrawState = state
            }
        }

        private fun color(
            key: String,
            fallback: Int,
        ): Int = colors[key]?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: fallback

        /** The theme palette, accent first: Android seeds its system colors from the most prominent one. */
        override fun onComputeColors(): WallpaperColors? {
            if (colors.isEmpty()) return null
            val primary = Color.valueOf(color("accent", Color.BLUE))
            val secondary = Color.valueOf(color("magenta", color("accent", Color.BLUE)))
            val tertiary = Color.valueOf(color("background", Color.BLACK))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return WallpaperColors(primary, secondary, tertiary)
            val hints =
                if (colors["mode"] == "light") WallpaperColors.HINT_SUPPORTS_DARK_TEXT else WallpaperColors.HINT_SUPPORTS_DARK_THEME
            return WallpaperColors(primary, secondary, tertiary, hints)
        }
    }
}
