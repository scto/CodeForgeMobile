// Modul: :feature:composepreview
package com.codeforge.feature.composepreview.gradle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Legt das mitgelieferte Init-Skript der genauen Vorschau ins Projekt (nur wenn es fehlt oder veraltet ist). */
@Singleton
class GradlePreviewInitScript @Inject constructor(@ApplicationContext private val context: Context) {

    private val text: String by lazy { context.assets.open(ASSET).bufferedReader().use { it.readText() } }

    /** Schreibt das Skript nach `<root>/`[GradlePreviews.INIT_SCRIPT]; blockierend (IO-Thread). */
    fun install(root: File): File {
        val target = File(root, GradlePreviews.INIT_SCRIPT)
        if (!target.isFile || target.readText() != text) {
            target.parentFile?.mkdirs()
            target.writeText(text)
        }
        return target
    }

    private companion object {
        const val ASSET = "compose-preview/compose-preview.init.gradle.kts"
    }
}
