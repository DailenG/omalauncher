package dev.omalauncher.screensaver

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings

object ScreensaverPolicy {
    const val LEAD_MILLIS = 10_000L

    /** Delay before the home screensaver shows, or null when it's off. */
    fun delayMillis(idleSeconds: Double, screenOffMillis: Long, pluggedIn: Boolean): Long? {
        if (idleSeconds <= 0.0) return null
        val idle = (idleSeconds * 1000).toLong()
        if (pluggedIn || screenOffMillis > idle) return idle
        return (screenOffMillis - LEAD_MILLIS).coerceAtLeast(screenOffMillis / 2)
    }
}

fun Context.screenOffMillis(): Long =
    runCatching {
        Settings.System.getLong(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Long.MAX_VALUE)
    }.getOrDefault(Long.MAX_VALUE)

fun Context.pluggedIn(): Boolean =
    registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        ?.let { it != 0 }
        ?: false
