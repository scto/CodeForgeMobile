package com.codeforge.libs.x2c.theme

import com.codeforge.core.domain.model.ThemeDetection
import com.codeforge.core.domain.model.ThemeGenerationResult
import com.codeforge.core.domain.model.ThemeSources
import com.codeforge.core.domain.repository.ComposeThemeGenerator
import com.codeforge.core.domain.repository.GeneratedComposeFile
import com.codeforge.libs.x2c.generator.LayoutValues

class ComposeThemeGeneratorImpl : ComposeThemeGenerator {

    private class Role(val attr: String, val param: String, val const: String)

    private class Merged(val colors: Map<String, String>, val styles: Map<String, StyleDef>)

    override fun detect(sources: ThemeSources): ThemeDetection {
        val merged = merge(sources.lightValues, emptyList())
        return pickTheme(merged.styles)?.let { s ->
            val family = familyOfStyle(s, merged.styles)
            ThemeDetection(s.name, effectiveParent(s), isMaterial = family != null, family = family)
        } ?: ThemeDetection()
    }

    override fun generate(
        sources: ThemeSources,
        targetPackage: String,
        existingFiles: Set<String>,
        sourceRoot: String
    ): Result<ThemeGenerationResult> = runCatching {
        val dir = "$sourceRoot/${targetPackage.replace('.', '/')}"
        val clash = listOf("Theme.kt", "Color.kt").filter { "$dir/$it" in existingFiles }
        if (clash.isNotEmpty()) {
            return@runCatching ThemeGenerationResult(
                skippedReason = "Bereits vorhanden: ${clash.joinToString(", ")} in $targetPackage; nichts überschrieben",
                notes = listOf("Vorhandenes Theme bleibt unverändert; umgestellte Activities nutzen MaterialTheme")
            )
        }
        val light = merge(sources.lightValues, emptyList())
        val style = pickTheme(light.styles)
        val parent = style?.let { effectiveParent(it) }
        if (style == null || familyOfStyle(style, light.styles) == null) {
            return@runCatching ThemeGenerationResult(
                skippedReason = "Kein Material-Theme (Theme.Material3…/Theme.MaterialComponents…) gefunden",
                notes = listOf("Ohne Material-Theme wird nichts erzeugt")
            )
        }
        val hasNight = sources.nightValues.isNotEmpty()
        val night = if (hasNight) merge(sources.lightValues, sources.nightValues) else null

        val notes = mutableListOf<String>()
        var todos = 0
        val themeName = themeNameFor(style.name)

        fun resolveRoles(m: Merged, suffix: String): Map<Role, Pair<String, String>> {
            val out = LinkedHashMap<Role, Pair<String, String>>()
            val st = m.styles[style.name] ?: style
            for (r in ROLES) {
                val raw = lookup(st, m.styles, r.attr) ?: continue
                val expr = colorExpr(raw, m.colors)
                if (expr == null) {
                    notes += "${r.attr}=\"$raw\" ($suffix) nicht auflösbar, Material-3-Vorgabe bleibt"
                    continue
                }
                out[r] = expr to raw
            }
            return out
        }

        val lightRoles = resolveRoles(light, "hell")
        val darkRoles = night?.let { resolveRoles(it, "dunkel") }
        listOf("colorPrimaryVariant", "colorSecondaryVariant").forEach {
            if (lookup(style, light.styles, it) != null) notes += "$it hat in Material 3 kein Gegenstück und wird ignoriert"
        }

        if (lightRoles.isEmpty()) todos++
        else if (lightRoles.keys.none { it.attr == "colorPrimary" }) { notes += "colorPrimary nicht gefunden: primary bleibt Material-3-Vorgabe"; todos++ }
        if (darkRoles == null) { notes += "Kein values-night: dunkles Schema nutzt die Material-3-Vorgaben"; todos++ }

        val colorKt = buildString {
            appendLine("package $targetPackage")
            appendLine()
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine()
            appendLine("// Generiert aus res/values von CodeForge x2c, bitte prüfen.")
            fun block(title: String, roles: Map<Role, Pair<String, String>>, suffix: String) {
                if (roles.isEmpty()) return
                appendLine()
                appendLine("// $title")
                for ((r, v) in roles) appendLine("val ${r.const}$suffix = ${v.first} // ${r.attr} = ${v.second}")
            }
            block("Hell", lightRoles, "Light")
            if (darkRoles != null) block("Dunkel", darkRoles, "Dark")
        }

        val themeKt = buildString {
            val imports = sortedSetOf(
                "androidx.compose.foundation.isSystemInDarkTheme",
                "androidx.compose.material3.MaterialTheme",
                "androidx.compose.material3.darkColorScheme",
                "androidx.compose.material3.lightColorScheme",
                "androidx.compose.runtime.Composable"
            )
            appendLine("package $targetPackage")
            appendLine()
            imports.forEach { appendLine("import $it") }
            appendLine()
            fun scheme(fn: String, name: String, roles: Map<Role, Pair<String, String>>?, suffix: String) {
                if (roles == null || roles.isEmpty()) {
                    appendLine("private val $name = $fn()")
                } else {
                    appendLine("private val $name = $fn(")
                    roles.keys.forEach { appendLine("    ${it.param} = ${it.const}$suffix,") }
                    appendLine(")")
                }
                appendLine()
            }
            scheme("lightColorScheme", "LightColors", lightRoles, "Light")
            scheme("darkColorScheme", "DarkColors", darkRoles, "Dark")
            appendLine("/**")
            appendLine(" * Material-3-Theme, generiert aus dem XML-Theme `${style.name}` (Parent `$parent`) von CodeForge x2c.")
            appendLine(" * Farben stammen aus `Color.kt`; nicht zuordenbare Rollen bleiben bei den Material-3-Vorgaben.")
            appendLine(" * Schrift und Formen wurden nicht übernommen (Standard von Material 3).")
            appendLine(" *")
            appendLine(" * @param darkTheme dunkles Schema verwenden; Standard folgt dem System.")
            appendLine(" * @param content Inhalt, der das Theme erhält.")
            appendLine(" */")
            appendLine("@Composable")
            appendLine("fun $themeName(")
            appendLine("    darkTheme: Boolean = isSystemInDarkTheme(),")
            appendLine("    content: @Composable () -> Unit,")
            appendLine(") {")
            appendLine("    MaterialTheme(")
            appendLine("        colorScheme = if (darkTheme) DarkColors else LightColors,")
            appendLine("        content = content,")
            appendLine("    )")
            appendLine("}")
        }
        val themeFinal = if (todos > 0) {
            themeKt.replace("private val LightColors", "// TODO(x2c): fehlende oder nicht zuordenbare Farben ergänzen (siehe Bericht)\nprivate val LightColors")
        } else themeKt

        fun file(name: String, text: String) = GeneratedComposeFile(
            packageName = targetPackage, composableName = name.removeSuffix(".kt"),
            targetRelativePath = "$dir/$name", sourceContent = text, todoCount = 0
        )
        ThemeGenerationResult(
            files = listOf(file("Color.kt", colorKt), file("Theme.kt", themeFinal).copy(todoCount = todos)),
            themeName = themeName,
            themePackage = targetPackage,
            sourceStyle = style.name,
            notes = notes,
            todoCount = todos
        )
    }

    // ---------------------------------------------------------------- Auflösung

    private fun merge(light: List<String>, night: List<String>): Merged {
        val colors = LinkedHashMap<String, String>()
        val styles = LinkedHashMap<String, StyleDef>()
        for (xml in light + night) {
            val doc = runCatching { ValuesXmlParser.parse(xml) }.getOrNull() ?: continue
            colors.putAll(doc.colors)
            for (s in doc.styles) {
                val old = styles[s.name]
                styles[s.name] = if (old == null) s else StyleDef(s.name, s.parent ?: old.parent, old.items + s.items)
            }
        }
        return Merged(colors, styles)
    }

    private fun effectiveParent(s: StyleDef): String? = s.parent
        ?: s.name.substringBeforeLast('.', "").takeIf { it.isNotEmpty() }

    private fun familyOf(parent: String?): String? = when {
        parent == null -> null
        parent.startsWith("Theme.Material3") || parent.startsWith("Theme.MaterialComponents") ||
            parent.startsWith("Base.Theme.Material3") || parent.startsWith("Base.Theme.MaterialComponents") ||
            parent.startsWith("android:Theme.Material3") -> if (parent.contains("Material3")) "Material3" else "MaterialComponents"
        else -> null
    }

    /** Familie über die Parent-Kette im Projekt auflösen (`Theme.X` → `Base.Theme.X` → `Theme.Material3…`). */
    private fun familyOfStyle(style: StyleDef, all: Map<String, StyleDef>, depth: Int = 0): String? {
        val parent = effectiveParent(style)
        familyOf(parent)?.let { return it }
        if (depth > 10) return null
        val next = parent?.let { all[it] } ?: return null
        return familyOfStyle(next, all, depth + 1)
    }

    /** Ein Material-Theme finden; bevorzugt eines, dessen Name kein NoActionBar/Splash/Overlay trägt. */
    private fun pickTheme(styles: Map<String, StyleDef>): StyleDef? {
        val materials = styles.values.filter { familyOfStyle(it, styles) != null }
        val plain = materials.filter { s -> SKIP_NAMES.none { s.name.contains(it, ignoreCase = true) } }
        return (plain.ifEmpty { materials }).firstOrNull { !it.name.startsWith("Base.") } ?: (plain.ifEmpty { materials }).firstOrNull()
    }

    private fun lookup(style: StyleDef, all: Map<String, StyleDef>, attr: String, depth: Int = 0): String? {
        style.items[attr]?.let { return it }
        if (attr == "android:colorBackground") style.items["colorBackground"]?.let { return it }
        if (depth > 10) return null
        val parentName = style.parent ?: style.name.substringBeforeLast('.', "").takeIf { it.isNotEmpty() }
        val parent = parentName?.let { all[it] } ?: return null
        return lookup(parent, all, attr, depth + 1)
    }

    private fun colorExpr(raw: String, colors: Map<String, String>, depth: Int = 0): String? {
        val v = raw.trim()
        if (depth > 10) return null
        return when {
            v.startsWith("#") -> LayoutValues.colorHex(v)
            v.startsWith("@color/") -> colors[v.removePrefix("@color/")]?.let { colorExpr(it, colors, depth + 1) }
            v.startsWith("@android:color/") -> LayoutValues.ANDROID_COLORS[v.removePrefix("@android:color/")]
            else -> null
        }
    }

    private fun themeNameFor(styleName: String): String {
        val parts = styleName.split('.').filter { it.isNotEmpty() && it !in GENERIC_PARTS && !it.equals("Base", true) }
        val base = parts.joinToString("") { LayoutValues.pascal(it) }
        return if (base.isEmpty()) "AppTheme" else if (base.endsWith("Theme")) base else base + "Theme"
    }

    companion object {
        private val SKIP_NAMES = listOf("NoActionBar", "Splash", "Overlay", "Dialog", "Popup", "FullScreen")
        private val GENERIC_PARTS = setOf(
            "Theme", "Material3", "MaterialComponents", "DayNight", "NoActionBar", "Light", "Dark", "Bridge", "Material3Expressive"
        )
        private val ROLES = listOf(
            "colorPrimary" to "primary", "colorOnPrimary" to "onPrimary",
            "colorPrimaryContainer" to "primaryContainer", "colorOnPrimaryContainer" to "onPrimaryContainer",
            "colorPrimaryInverse" to "inversePrimary",
            "colorSecondary" to "secondary", "colorOnSecondary" to "onSecondary",
            "colorSecondaryContainer" to "secondaryContainer", "colorOnSecondaryContainer" to "onSecondaryContainer",
            "colorTertiary" to "tertiary", "colorOnTertiary" to "onTertiary",
            "colorTertiaryContainer" to "tertiaryContainer", "colorOnTertiaryContainer" to "onTertiaryContainer",
            "colorError" to "error", "colorOnError" to "onError",
            "colorErrorContainer" to "errorContainer", "colorOnErrorContainer" to "onErrorContainer",
            "android:colorBackground" to "background", "colorOnBackground" to "onBackground",
            "colorSurface" to "surface", "colorOnSurface" to "onSurface",
            "colorSurfaceVariant" to "surfaceVariant", "colorOnSurfaceVariant" to "onSurfaceVariant",
            "colorSurfaceInverse" to "inverseSurface", "colorOnSurfaceInverse" to "inverseOnSurface",
            "colorOutline" to "outline", "colorOutlineVariant" to "outlineVariant"
        ).map { (a, p) -> Role(a, p, p.replaceFirstChar { it.uppercaseChar() }) }
    }
}
