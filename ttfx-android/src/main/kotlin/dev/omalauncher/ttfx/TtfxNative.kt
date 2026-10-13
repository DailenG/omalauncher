package dev.omalauncher.ttfx

internal object TtfxNative {
    init {
        System.loadLibrary("ttfx_android")
    }

    external fun create(input: String, args: Array<String>, cols: Int, rows: Int, seed: Long, hasSeed: Boolean): Long
    external fun next(handle: Long, glyphs: IntArray, colors: IntArray): Boolean
    external fun effect(handle: Long): String
    external fun destroy(handle: Long)
    external fun effects(): Array<String>
}
