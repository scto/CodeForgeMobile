// Modul: :feature:editor
package com.codeforge.feature.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * sora-editor leitet Standard-Vordergrund/-Hintergrund aus dem ersten tokenColors-Eintrag OHNE `scope` ab.
 * Fehlt er, ist Text ohne eigenes Token schwarz – auf dem dunklen Theme unlesbar.
 */
class TextMateThemeDefaultsTest {

    private fun theme(name: String): String = listOf(
        File("src/main/assets/textmate/themes/$name.json"),
        File("feature/editor/src/main/assets/textmate/themes/$name.json")
    ).first { it.isFile }.readText()

    @Test
    fun `dark theme defines a global default foreground and background`() {
        val t = theme("dark_plus")
        assertTrue(Regex("\"tokenColors\"\\s*:\\s*\\[\\s*\\{\\s*\"settings\"\\s*:\\s*\\{[^}]*#D4D4D4", RegexOption.IGNORE_CASE).containsMatchIn(t))
    }

    @Test
    fun `light theme defines a global default foreground and background`() {
        val t = theme("light_plus")
        assertTrue(Regex("\"tokenColors\"\\s*:\\s*\\[\\s*\\{\\s*\"settings\"\\s*:\\s*\\{[^}]*#FFFFFF", RegexOption.IGNORE_CASE).containsMatchIn(t))
    }
}
