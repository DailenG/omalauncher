package dev.omalauncher.ttfx

import android.content.Context

class TtfxAnimation private constructor(private var handle: Long, val cols: Int, val rows: Int) : AutoCloseable {
    val effect: String
        get() {
            check(handle != 0L) { "animation is closed" }
            return TtfxNative.effect(handle)
        }

    val glyphs = IntArray(Math.multiplyExact(cols, rows))
    val colors = IntArray(glyphs.size)

    fun next(): Boolean {
        check(handle != 0L) { "animation is closed" }
        return TtfxNative.next(handle, glyphs, colors)
    }

    override fun close() {
        val currentHandle = handle
        handle = 0
        if (currentHandle != 0L) TtfxNative.destroy(currentHandle)
    }

    companion object {
        val effects: List<String> = TtfxNative.effects().toList()

        fun random(
            text: String,
            cols: Int,
            rows: Int,
            frameRate: Int = 60,
            seed: Long? = null,
            exclude: Set<String> = emptySet(),
        ): TtfxAnimation {
            val args = buildList {
                addAll(listOf("--frame-rate", frameRate.toString(), "--random-effect"))
                if (exclude.isNotEmpty()) {
                    add("--exclude-effects")
                    addAll(exclude.sorted())
                }
            }
            return create(text, args, cols, rows, frameRate, seed)
        }

        fun of(
            effect: String,
            text: String,
            cols: Int,
            rows: Int,
            frameRate: Int = 60,
            seed: Long? = null,
        ): TtfxAnimation {
            require(effect in effects) { "unknown ttfx effect: $effect" }
            return create(text, listOf(effect, "--frame-rate", frameRate.toString()), cols, rows, frameRate, seed)
        }

        fun omarchyLogo(context: Context): String =
            context.assets.open("ttfx/omarchy-logo.txt").bufferedReader().use { it.readText() }

        private fun create(
            text: String,
            args: List<String>,
            cols: Int,
            rows: Int,
            frameRate: Int,
            seed: Long?,
        ): TtfxAnimation {
            require(cols in 1..65535 && rows in 1..65535) { "canvas dimensions must be between 1 and 65535" }
            require(frameRate > 0) { "frame rate must be positive" }
            Math.multiplyExact(cols, rows)
            val handle = TtfxNative.create(text, args.toTypedArray(), cols, rows, seed ?: 0L, seed != null)
            check(handle != 0L) { "native ttfx returned an empty handle" }
            return TtfxAnimation(handle, cols, rows)
        }
    }
}
