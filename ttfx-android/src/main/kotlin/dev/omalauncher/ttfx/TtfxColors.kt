package dev.omalauncher.ttfx

import kotlin.math.roundToInt

internal object TtfxColors {
    fun blend(argb: Int, tint: Int, t: Float): Int {
        val amount = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = (argb ushr shift) and 0xff
            val to = (tint ushr shift) and 0xff
            return (from + (to - from) * amount).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun scaleAlpha(argb: Int, alpha: Float): Int {
        val scaled = (((argb ushr 24) and 0xff) * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (argb and 0x00ffffff) or (scaled shl 24)
    }
}
