package dev.omalauncher.screensaver

import android.graphics.Color
import android.service.dreams.DreamService
import dev.omalauncher.ttfx.TtfxAnimation
import dev.omalauncher.ttfx.TtfxScreensaverView

class OmarchyDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(
            TtfxScreensaverView(this, TtfxAnimation.omarchyLogo(this)).apply {
                setBackgroundColor(Color.BLACK)
            },
        )
    }
}
