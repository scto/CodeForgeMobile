// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json

/** Eine geplante Dateiänderung; [before] `null` = Datei wird neu angelegt. */
data class FileChange(val file: File, val before: String?, val after: String) {
    val isNew: Boolean get() = before == null
    val isUnchanged: Boolean get() = before == after
}

/** Ergebnis von [ProjectThemeWriter.plan]: Änderungen und Hinweise für den Nutzer. */
data class ThemePlan(val changes: List<FileChange>, val notes: List<ThemeNote>)

/** Hinweise zur Übernahme (in der UI übersetzt). */
sealed interface ThemeNote {
    /** In `Theme.kt` fehlen `lightColorScheme(…)`/`darkColorScheme(…)`; die Schemata müssen von Hand verwendet werden. */
    data class ComposeSchemesNotFound(val themeFile: File) : ThemeNote

    /** `dynamicColor` war standardmäßig an und wurde ausgeschaltet. */
    data class DynamicColorDisabled(val themeFile: File) : ThemeNote

    /** Der App-Style aus dem Manifest steht in keiner `themes.xml`/`styles.xml`. */
    data class StyleNotFound(val styleName: String) : ThemeNote

    /** Kein Material3-Parent: nur ein Teil der Rollen wird eingetragen. */
    data class LimitedXmlFamily(val styleName: String, val family: ViewsThemeCodegen.ThemeFamily) : ThemeNote
}

/** Plant und schreibt die Übernahme eines Themes in ein Projekt (mit Sicherung und `.codeforge/theme.json`). */
class ProjectThemeWriter(private val root: File) {

    private val specFile: File get() = File(root, ".codeforge/theme.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = false }

    /** Gespeichertes Theme des Projekts, falls vorhanden. */
    fun loadSpec(): ThemeSpec? = specFile.takeIf { it.isFile }
        ?.let { runCatching { json.decodeFromString(ThemeSpec.serializer(), it.readText()) }.getOrNull() }

    /** Berechnet alle Änderungen für [targets], ohne etwas zu schreiben. */
    fun plan(spec: ThemeSpec, targets: List<ThemeTarget>): ThemePlan {
        val changes = mutableListOf<FileChange>()
        val notes = mutableListOf<ThemeNote>()
        for (t in targets) {
            when (t) {
                is ThemeTarget.Compose -> planCompose(spec, t, changes, notes)
                is ThemeTarget.Views -> planViews(spec, t, changes, notes)
            }
        }
        changes += change(specFile, json.encodeToString(ThemeSpec.serializer(), spec) + "\n")
        return ThemePlan(changes.filterNot { it.isUnchanged }, notes)
    }

    private fun planCompose(spec: ThemeSpec, t: ThemeTarget.Compose, changes: MutableList<FileChange>, notes: MutableList<ThemeNote>) {
        changes += change(File(t.themeDir, ComposeThemeCodegen.COLORS_FILE), ComposeThemeCodegen.colorsFile(t.packageName, spec))
        val themeFile = File(t.themeDir, "Theme.kt")
        val patch = ComposeThemeCodegen.patchThemeFile(themeFile.readText())
        if (!patch.schemesReplaced) notes += ThemeNote.ComposeSchemesNotFound(themeFile)
        if (patch.dynamicColorDisabled) notes += ThemeNote.DynamicColorDisabled(themeFile)
        changes += change(themeFile, patch.text)
    }

    private fun planViews(spec: ThemeSpec, t: ThemeTarget.Views, changes: MutableList<FileChange>, notes: MutableList<ThemeNote>) {
        changes += change(File(t.resDir, "values/${ViewsThemeCodegen.COLORS_FILE}"), ViewsThemeCodegen.colorsFile(spec, false))
        changes += change(File(t.resDir, "values-night/${ViewsThemeCodegen.COLORS_FILE}"), ViewsThemeCodegen.colorsFile(spec, true))
        var patchedAny = false
        val styleFiles = listOf("values", "values-night")
            .flatMap { dir -> listOf("themes.xml", "styles.xml").map { File(t.resDir, "$dir/$it") } }
            .filter { it.isFile }
        for (f in styleFiles) {
            val patch = ViewsThemeCodegen.patchThemesFile(f.readText(), t.styleName)
            if (patch.found) {
                patchedAny = true
                if (f.parentFile?.name == "values" && patch.family != ViewsThemeCodegen.ThemeFamily.MATERIAL3) {
                    notes += ThemeNote.LimitedXmlFamily(t.styleName, patch.family)
                }
                changes += change(f, patch.text)
            }
        }
        if (!patchedAny) notes += ThemeNote.StyleNotFound(t.styleName)
    }

    private fun change(file: File, after: String) = FileChange(file, file.takeIf { it.isFile }?.readText(), after)

    /**
     * Schreibt [plan]. Vorher werden alle vorhandenen Dateien nach `.codeforge/backup/theme-<Zeit>/` gesichert.
     * @return Sicherungsordner (oder `null`, wenn nichts zu sichern war).
     */
    fun apply(plan: ThemePlan, now: Date = Date()): File? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(now)
        val backupDir = File(root, ".codeforge/backup/theme-$stamp")
        var backedUp = false
        for (c in plan.changes) {
            if (c.before != null) {
                val target = File(backupDir, c.file.relativeTo(root).path)
                target.parentFile?.mkdirs()
                target.writeText(c.before)
                backedUp = true
            }
        }
        for (c in plan.changes) {
            c.file.parentFile?.mkdirs()
            c.file.writeText(c.after)
        }
        return backupDir.takeIf { backedUp }
    }

}
