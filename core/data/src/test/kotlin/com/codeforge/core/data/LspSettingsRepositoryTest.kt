package com.codeforge.core.data

import com.codeforge.core.data.repository.LspSettingsRepositoryImpl
import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.domain.model.LspFeature
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LspSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun persistsAndReloads() = runBlocking {
        val file = File(tmp.root, "lsp-settings.json")
        val repo = LspSettingsRepositoryImpl(file)
        repo.update("vscode-css-language-server") { it.copy(disabledFeatures = setOf(LspFeature.FORMATTING)) }
        repo.update("jdtls") { it.copy(enabled = false, jvm = JvmOptions(xmx = "1g")) }

        val again = LspSettingsRepositoryImpl(file)
        assertFalse(again.allows("vscode-html-language-server", LspFeature.FORMATTING))
        assertTrue(again.allows("vscode-html-language-server", LspFeature.HOVER))
        assertFalse(again.of("jdtls").enabled)
        assertEquals(listOf("-Xmx1g"), again.jvmOptions("jdtls")?.toArgs())
    }

    @Test fun defaultsAndBrokenFile() = runBlocking {
        val file = File(tmp.root, "broken.json").apply { writeText("{nope") }
        val repo = LspSettingsRepositoryImpl(file)
        assertTrue(repo.of("kotlin-language-server").enabled)
        assertTrue(repo.jvmOptions("kotlin-language-server")!!.toArgs().contains("-XX:+UseSerialGC"))
        assertEquals(null, repo.jvmOptions("pyright"))
        // Zurück auf Standard entfernt den Eintrag wieder.
        repo.update("jdtls") { it.copy(enabled = false) }
        repo.update("jdtls") { it.copy(enabled = true) }
        assertTrue(repo.settings.value.isEmpty())
    }
}
