package dev.omalauncher.ttfx

internal object BlockElements {
    fun parts(codepoint: Int): FloatArray? =
        when (codepoint) {
            0x2580 -> rect(0f, 0f, 1f, 0.5f)
            in 0x2581..0x2587 -> {
                val height = (codepoint - 0x2580) / 8f
                rect(0f, 1f - height, 1f, 1f)
            }
            0x2588 -> rect(0f, 0f, 1f, 1f)
            in 0x2589..0x258f -> {
                val width = (0x2590 - codepoint) / 8f
                rect(0f, 0f, width, 1f)
            }
            0x2590 -> rect(0.5f, 0f, 1f, 1f)
            0x2591 -> rect(0f, 0f, 1f, 1f, 0.25f)
            0x2592 -> rect(0f, 0f, 1f, 1f, 0.5f)
            0x2593 -> rect(0f, 0f, 1f, 1f, 0.75f)
            0x2594 -> rect(0f, 0f, 1f, 0.125f)
            0x2595 -> rect(0.875f, 0f, 1f, 1f)
            0x2596 -> rect(0f, 0.5f, 0.5f, 1f)
            0x2597 -> rect(0.5f, 0.5f, 1f, 1f)
            0x2598 -> rect(0f, 0f, 0.5f, 0.5f)
            0x2599 -> combine(rect(0f, 0f, 0.5f, 0.5f), rect(0f, 0.5f, 0.5f, 1f), rect(0.5f, 0.5f, 1f, 1f))
            0x259a -> combine(rect(0f, 0f, 0.5f, 0.5f), rect(0.5f, 0.5f, 1f, 1f))
            0x259b -> combine(rect(0f, 0f, 0.5f, 0.5f), rect(0.5f, 0f, 1f, 0.5f), rect(0f, 0.5f, 0.5f, 1f))
            0x259c -> combine(rect(0f, 0f, 0.5f, 0.5f), rect(0.5f, 0f, 1f, 0.5f), rect(0.5f, 0.5f, 1f, 1f))
            0x259d -> rect(0.5f, 0f, 1f, 0.5f)
            0x259e -> combine(rect(0.5f, 0f, 1f, 0.5f), rect(0f, 0.5f, 0.5f, 1f))
            0x259f -> combine(rect(0.5f, 0f, 1f, 0.5f), rect(0f, 0.5f, 0.5f, 1f), rect(0.5f, 0.5f, 1f, 1f))
            else -> null
        }

    private fun rect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float = 1f,
    ) = floatArrayOf(left, top, right, bottom, alpha)

    private fun combine(vararg parts: FloatArray): FloatArray =
        FloatArray(parts.sumOf { it.size }).also { result ->
            var offset = 0
            for (part in parts) {
                part.copyInto(result, offset)
                offset += part.size
            }
        }
}
