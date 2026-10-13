package dev.omalauncher.screensaver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.omalauncher.ttfx.TtfxScreensaverView

class ScreensaverActivity : Activity() {
    private var powerReceiverRegistered = false
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateKeepScreenOn()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContentView(
            TtfxScreensaverView(this, ScreensaverText.load(this)).apply {
                setBackgroundColor(Color.BLACK)
            },
        )
    }

    override fun onStart() {
        super.onStart()
        if (!powerReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                powerReceiver,
                IntentFilter(Intent.ACTION_POWER_CONNECTED).apply {
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            powerReceiverRegistered = true
        }
        updateKeepScreenOn()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        finish()
        return true
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (powerReceiverRegistered) {
            unregisterReceiver(powerReceiver)
            powerReceiverRegistered = false
        }
        finish()
    }

    private fun updateKeepScreenOn() {
        if (pluggedIn()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
