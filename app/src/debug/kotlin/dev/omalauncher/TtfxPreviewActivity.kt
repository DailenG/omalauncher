package dev.omalauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import dev.omalauncher.screensaver.ScreensaverText
import dev.omalauncher.ttfx.TtfxScreensaverView

class TtfxPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(TtfxScreensaverView(this, ScreensaverText.load(this)).apply { setOnClickListener { finish() } })
    }
}
