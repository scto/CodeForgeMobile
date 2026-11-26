/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.extension.TerminalColorsFile
import com.codeforge.core.domain.extension.TerminalPalette
import java.io.File

/** Ein wählbares Terminal-Farbschema (eingebaut oder aus einem Plugin). */
@Immutable
data class TerminalThemeOption(
    val id: String,
    val label: String,
    val isDark: Boolean,
    val palette: TerminalPalette,
    /** Plugin, aus dem das Schema stammt, oder `null` für eingebaute. */
    val pluginId: String? = null,
)

/** Eingebaute Schemata: nur Termux-Standard und CodeForge; weitere kommen als Plugins aus dem Store. */
object TerminalThemes {
    /** Kennung von „Termux-Standard“ (keine `colors.properties`). */
    const val DEFAULT_ID = "termux-default"

    /** Kennung für eine selbst geschriebene `colors.properties`, die keinem Schema zugeordnet ist. */
    const val CUSTOM_ID = "custom"

    /** CodeForge-Farben (Indigo/Cyan wie die App). */
    val CODEFORGE = TerminalPalette(
        foreground = 0xE2E2F0,
        background = 0x12121C,
        cursor = 0x818CF8,
        ansi = listOf(
            0x1C1C2A, 0xF87171, 0x4ADE80, 0xFACC15, 0x818CF8, 0xC084FC, 0x22D3EE, 0xCBD5E1,
            0x475569, 0xFCA5A5, 0x86EFAC, 0xFDE047, 0xA5B4FC, 0xD8B4FE, 0x67E8F9, 0xF8FAFC,
        ),
    )

    /** Eingebaute Optionen; [defaultLabel]/[codeForgeLabel] sind die übersetzten Namen. */
    fun builtins(defaultLabel: String, codeForgeLabel: String) = listOf(
        TerminalThemeOption(DEFAULT_ID, defaultLabel, isDark = true, palette = TerminalColorsFile.TERMUX_DEFAULT),
        TerminalThemeOption("codeforge", codeForgeLabel, isDark = true, palette = CODEFORGE),
    )
}

/**
 * Liest und schreibt `~/.termux/colors.properties`. Ein von CodeForge geschriebenes Schema trägt seine Kennung in
 * der ersten Zeile (`# codeforge-terminal-theme: <id>`); eine fremde Datei wird vor dem Überschreiben nach
 * `~/.termux/backup/` gesichert.
 */
class TerminalColorsStore(private val file: File) {

    /** Kennung des aktiven Schemas: [TerminalThemes.DEFAULT_ID] ohne Datei, [TerminalThemes.CUSTOM_ID] bei fremder Datei. */
    fun currentId(): String {
        if (!file.isFile) return TerminalThemes.DEFAULT_ID
        val first = runCatching { file.useLines { it.firstOrNull() } }.getOrNull().orEmpty()
        return first.removePrefix(MARKER).trim().takeIf { first.startsWith(MARKER) && it.isNotEmpty() }
            ?: TerminalThemes.CUSTOM_ID
    }

    /** Aktuelle Farben (für die Vorschau einer fremden Datei), sonst `null`. */
    fun currentPalette(): TerminalPalette? = runCatching { if (file.isFile) TerminalColorsFile.parse(file.readText()) else null }.getOrNull()

    /** Setzt [option]; Termux-Standard entfernt die Datei. Liefert die Sicherung einer fremden Datei oder `null`. */
    fun apply(option: TerminalThemeOption, now: java.util.Date = java.util.Date()): Result<File?> = runCatching {
        val backup = if (currentId() == TerminalThemes.CUSTOM_ID) backupForeign(now) else null
        if (option.id == TerminalThemes.DEFAULT_ID) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            file.writeText("$MARKER ${option.id}\n" + option.palette.toProperties(listOf(option.label)))
        }
        backup
    }

    private fun backupForeign(now: java.util.Date): File {
        val dir = File(file.parentFile, "backup").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(now)
        return File(dir, "${file.name}.$stamp.bak").also { file.copyTo(it, overwrite = true) }
    }

    private companion object {
        const val MARKER = "# codeforge-terminal-theme:"
    }
}
