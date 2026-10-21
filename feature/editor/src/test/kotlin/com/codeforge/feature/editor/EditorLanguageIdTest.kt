// Modul: :feature:editor
package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditorLanguageIdTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `languageIdFor maps file extensions according to Task 43 specification without regression`() {
        val testCases = mapOf(
            // XML variants
            "file.xml" to "xml",
            "schema.xsd" to "xml",
            "style.xsl" to "xml",
            "transform.xslt" to "xml",
            "vector.svg" to "xml",
            "info.plist" to "xml",

            // YAML variants
            "config.yaml" to "yaml",
            "docker-compose.yml" to "yaml",

            // TOML
            "Cargo.toml" to "toml",
            "pyproject.toml" to "toml",

            // Shell / Bash
            "script.sh" to "shellscript",
            "setup.bash" to "shellscript",
            "profile.zsh" to "shellscript",

            // Groovy-Gradle stays plaintext / no LSP server, while gradle.kts is Kotlin
            "build.gradle" to "plaintext",
            "settings.gradle" to "plaintext",
            "build.gradle.kts" to "kotlin",
            "settings.gradle.kts" to "kotlin",

            // Existing languages (no regression)
            "Main.kt" to "kotlin",
            "Script.kts" to "kotlin",
            "App.java" to "java",
            "data.json" to "json",
            "main.py" to "python",
            "header.h" to "c",
            "source.c" to "c",
            "main.cpp" to "cpp",
            "test.cc" to "cpp",
            "code.cxx" to "cpp",
            "decl.hpp" to "cpp",
            "decl.hh" to "cpp",
            "lib.rs" to "rust",
            "index.html" to "html",
            "page.htm" to "html",
            "styles.css" to "css",
            "notes.txt" to "plaintext",
            "unknown.xyz" to "plaintext"
        )

        for ((fileName, expectedLanguageId) in testCases) {
            val actual = EditorViewModel.languageIdFor("/workspace/$fileName")
            assertEquals("Failed for $fileName", expectedLanguageId, actual)
        }
    }

    @Test
    fun `languageIdFor evaluates shebang for script files without extension`() {
        val bashScript = tempFolder.newFile("run-build").apply {
            writeText("#!/bin/bash\necho building")
        }
        val shScript = tempFolder.newFile("entrypoint").apply {
            writeText("#!/bin/sh\nexec app")
        }
        val envBashScript = tempFolder.newFile("tool").apply {
            writeText("#!/usr/bin/env bash\nset -e")
        }
        val plainTextFile = tempFolder.newFile("README").apply {
            writeText("Just regular text\nno shebang")
        }

        assertEquals("shellscript", EditorViewModel.languageIdFor(bashScript.absolutePath))
        assertEquals("shellscript", EditorViewModel.languageIdFor(shScript.absolutePath))
        assertEquals("shellscript", EditorViewModel.languageIdFor(envBashScript.absolutePath))
        assertEquals("plaintext", EditorViewModel.languageIdFor(plainTextFile.absolutePath))
    }
}
