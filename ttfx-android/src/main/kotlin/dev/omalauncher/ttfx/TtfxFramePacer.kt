package dev.omalauncher.ttfx

class TtfxFramePacer(frameRate: Int) {
    private val intervalNanos = 1_000_000_000L / frameRate.also { require(it > 0) }
    private var due = 0L

    fun shouldDraw(frameTimeNanos: Long): Boolean {
        if (due == 0L || frameTimeNanos >= due - TOLERANCE_NANOS) {
            due =
                if (due == 0L || frameTimeNanos - due > intervalNanos) {
                    frameTimeNanos + intervalNanos
                } else {
                    due + intervalNanos
                }
            return true
        }
        return false
    }

    fun reset() {
        due = 0L
    }

    private companion object {
        const val TOLERANCE_NANOS = 4_000_000L
    }
}
