package dev.omalauncher.screensaver

import android.content.Context
import dev.omalauncher.LauncherData
import dev.omalauncher.ttfx.TtfxAnimation
import dev.omalauncher.upgrade.Managed
import dev.omalauncher.upgrade.Policy
import java.io.File

object ScreensaverText {
    const val FILE = "screensaver.txt"
    const val MAX_BYTES = 32 * 1024

    fun normalize(raw: String): String? {
        val lines =
            raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { line ->
                    buildString {
                        var column = 0
                        for (character in line) {
                            if (character == '\t') {
                                val spaces = TAB_WIDTH - column % TAB_WIDTH
                                repeat(spaces) { append(' ') }
                                column += spaces
                            } else {
                                append(character)
                                column++
                            }
                        }
                    }.trimEnd()
                }.dropWhile(String::isBlank)
                .dropLastWhile(String::isBlank)
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    fun load(context: Context): String {
        val custom =
            runCatching {
                val file = File(LauncherData.brandingDir(context), FILE)
                if (!file.isFile || file.length() > MAX_BYTES) return@runCatching null
                normalize(file.readText(Charsets.UTF_8))
            }.getOrNull()
        return custom ?: normalize(TtfxAnimation.omarchyLogo(context)).orEmpty()
    }

    fun managed(context: Context): List<Managed> =
        listOf(
            Managed(File(LauncherData.brandingDir(context), FILE), Policy.Seeded) {
                context.assets.open("ttfx/omarchy-logo.txt").use { it.readBytes() }
            },
        )

    private const val TAB_WIDTH = 4
}
