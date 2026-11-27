/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import java.io.File

/** Build-Varianten des Projekts (aus den `assemble<Variante>`-Tasks) und die projektweit gewählte Variante. */
internal object BuildVariants {
    /** Varianten ohne Task-Liste. */
    val DEFAULT: List<String> = listOf("debug", "release")

    private const val ASSEMBLE = "assemble"
    private val TEST_SUFFIXES = listOf("AndroidTest", "UnitTest", "TestFixtures")
    private val ASSEMBLE_TASK = Regex("""(?:^|:)assemble([A-Z]\w*)$""")
    private const val STORE_FILE = ".codeforge/build-variant"
    private val VALID = Regex("""[a-z][A-Za-z0-9]{0,63}""")

    /**
     * Varianten aus den Task-Namen: `assembleDebug` → `debug`, `assembleFreeRelease` → `freeRelease`. Test-Varianten
     * und reine Typ-/Flavor-Sammeltasks bleiben drin, wenn Gradle sie anbietet; `debug`/`release` stehen vorne.
     */
    fun fromTasks(taskNames: List<String>): List<String> {
        val found = taskNames.asSequence()
            .filter { ':' !in it }
            .mapNotNull { ASSEMBLE_TASK.find(it)?.groupValues?.get(1) }
            .filter { suffix -> TEST_SUFFIXES.none { suffix.endsWith(it) } }
            .map { it.replaceFirstChar(Char::lowercaseChar) }
            .distinct()
            .toList()
        if (found.isEmpty()) return DEFAULT
        val preferred = DEFAULT.filter { it in found }
        return preferred + (found - preferred.toSet()).sorted()
    }

    /** Gradle-Task, der die Variante baut. */
    fun assembleTask(variant: String): String = ASSEMBLE + variant.replaceFirstChar(Char::uppercaseChar)

    /** Variante, die ein Task baut (`assembleFreeDebug` → `freeDebug`), sonst `null`. */
    fun variantOf(taskName: String): String? =
        taskName.split(' ').firstNotNullOfOrNull { ASSEMBLE_TASK.find(it)?.groupValues?.get(1) }
            ?.replaceFirstChar(Char::lowercaseChar)

    /** Gespeicherte Variante des Projekts oder `null`. */
    fun load(projectRoot: File): String? =
        runCatching { File(projectRoot, STORE_FILE).readText().trim() }.getOrNull()?.takeIf { VALID.matches(it) }

    /** Speichert die Variante im Projekt (`.codeforge/build-variant`). */
    fun save(projectRoot: File, variant: String) {
        if (!VALID.matches(variant)) return
        runCatching {
            val file = File(projectRoot, STORE_FILE)
            file.parentFile?.mkdirs()
            file.writeText(variant)
        }
    }
}
