// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import java.io.File

/** Ein Ort im Projekt, an dem ein Theme übernommen werden kann. */
sealed interface ThemeTarget {
    /** Modulordner relativ zum Projekt (z. B. `app`). */
    val module: String

    /** Compose: Ordner `ui/theme` mit `Theme.kt`. */
    data class Compose(
        override val module: String,
        val themeDir: File,
        val packageName: String,
        val themeFunction: String?,
    ) : ThemeTarget

    /** Views: `res`-Ordner und der App-Style aus dem Manifest. */
    data class Views(
        override val module: String,
        val resDir: File,
        val styleName: String,
        val parent: String?,
    ) : ThemeTarget
}

/** Sucht in einem Projekt nach `ui/theme/Theme.kt` (Compose) und dem App-Style aus `AndroidManifest.xml` (Views). */
object ProjectThemeScanner {
    private const val MAX_DEPTH = 12
    private val SKIP = setOf("build", ".git", ".gradle", ".idea", ".codeforge", "node_modules", ".cxx", "intermediates")

    /** Alle Übernahme-Ziele unter [root], App-Modul zuerst. */
    fun scan(root: File): List<ThemeTarget> {
        val targets = mutableListOf<ThemeTarget>()
        walk(root, 0) { file ->
            when {
                file.name == "Theme.kt" && file.parentFile?.name == "theme" -> composeTarget(root, file)?.let(targets::add)
                file.name == "AndroidManifest.xml" && file.parentFile?.name == "main" -> viewsTarget(root, file)?.let(targets::add)
            }
        }
        // App-Modul zuerst, dann alphabetisch.
        return targets.sortedWith(compareBy({ it.module != "app" }, { it.module }, { it is ThemeTarget.Views }))
    }

    private fun composeTarget(root: File, themeFile: File): ThemeTarget.Compose? {
        val text = runCatching { themeFile.readText() }.getOrNull() ?: return null
        if (!text.contains("MaterialTheme")) return null
        val pkg = Regex("""(?m)^\s*package\s+([\w.]+)""").find(text)?.groupValues?.get(1) ?: return null
        val fn = Regex("""@Composable\s+fun\s+(\w+)\s*\(""").find(text)?.groupValues?.get(1)
        return ThemeTarget.Compose(moduleOf(root, themeFile), themeFile.parentFile, pkg, fn)
    }

    private fun viewsTarget(root: File, manifest: File): ThemeTarget.Views? {
        val text = runCatching { manifest.readText() }.getOrNull() ?: return null
        val app = Regex("""<application\b[^>]*>""", RegexOption.DOT_MATCHES_ALL).find(text)?.value ?: return null
        val style = Regex("""android:theme\s*=\s*"@style/([^"]+)"""").find(app)?.groupValues?.get(1) ?: return null
        val res = File(manifest.parentFile, "res")
        val themesXml = listOf("themes.xml", "styles.xml").map { File(res, "values/$it") }.firstOrNull { it.isFile }
            ?: return null
        val parent = Regex(
            """<style\s+name\s*=\s*"${Regex.escape(style)}"[^>]*?parent\s*=\s*"([^"]*)"""",
        ).find(themesXml.readText())?.groupValues?.get(1)
        return ThemeTarget.Views(moduleOf(root, manifest), res, style, parent)
    }

    // Modul = Pfad bis vor `src/`.
    private fun moduleOf(root: File, file: File): String {
        val rel = file.relativeTo(root).invariantSeparatorsPath
        return rel.substringBefore("/src/", "").ifEmpty { "." }
    }

    private fun walk(dir: File, depth: Int, visit: (File) -> Unit) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                if (f.name !in SKIP) walk(f, depth + 1, visit)
            } else {
                visit(f)
            }
        }
    }
}
