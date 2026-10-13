package dev.omalauncher.screensaver

import android.graphics.Color
import android.service.dreams.DreamService
import dev.omalauncher.ttfx.TtfxScreensaverView

class OmarchyDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(
            TtfxScreensaverView(this, ScreensaverText.load(this)).apply {
                setBackgroundColor(Color.BLACK)
            },
        )
    }
}
