package com.codeforge.feature.editor.treesitter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TreeSitterGrammarTest {

    @Test
    fun cmakeListsRecognizedDirectlyAndInPath() {
        assertEquals(TreeSitterGrammar.CMAKE, TreeSitterGrammar.fromPath("CMakeLists.txt"))
        assertEquals(TreeSitterGrammar.CMAKE, TreeSitterGrammar.fromPath("app/src/main/cpp/CMakeLists.txt"))
        assertEquals(TreeSitterGrammar.CMAKE, TreeSitterGrammar.fromPath("C:\\project\\CMakeLists.txt"))
        assertEquals(TreeSitterGrammar.CMAKE, TreeSitterGrammar.fromPath("/root/cmakelists.txt"))
        assertEquals(TreeSitterGrammar.CMAKE, TreeSitterGrammar.fromPath("helper.cmake"))
    }

    @Test
    fun standardExtensionsRecognized() {
        assertEquals(TreeSitterGrammar.KOTLIN, TreeSitterGrammar.fromPath("Main.kt"))
        assertEquals(TreeSitterGrammar.KOTLIN, TreeSitterGrammar.fromPath("build.gradle.kts"))
        assertEquals(TreeSitterGrammar.JAVA, TreeSitterGrammar.fromPath("Service.java"))
        assertEquals(TreeSitterGrammar.JSON, TreeSitterGrammar.fromPath("data.json"))
        assertEquals(TreeSitterGrammar.XML, TreeSitterGrammar.fromPath("layout.xml"))
        assertEquals(TreeSitterGrammar.CPP, TreeSitterGrammar.fromPath("native.cpp"))
        assertEquals(TreeSitterGrammar.CPP, TreeSitterGrammar.fromPath("native.hpp"))
        assertEquals(TreeSitterGrammar.C, TreeSitterGrammar.fromPath("main.c"))
        assertEquals(TreeSitterGrammar.C, TreeSitterGrammar.fromPath("main.h"))
        assertEquals(TreeSitterGrammar.BASH, TreeSitterGrammar.fromPath("setup.sh"))
        assertEquals(TreeSitterGrammar.BASH, TreeSitterGrammar.fromPath("run.bash"))
        assertEquals(TreeSitterGrammar.TOML, TreeSitterGrammar.fromPath("libs.versions.toml"))
        assertEquals(TreeSitterGrammar.YAML, TreeSitterGrammar.fromPath("config.yaml"))
        assertEquals(TreeSitterGrammar.YAML, TreeSitterGrammar.fromPath("ci.yml"))
    }

    @Test
    fun unsupportedFilesReturnNull() {
        assertNull(TreeSitterGrammar.fromPath("gradle.properties"))
        assertNull(TreeSitterGrammar.fromPath("README.md"))
        assertNull(TreeSitterGrammar.fromPath("unknown_file"))
    }

    @Test
    fun grammarPropertiesMatchConvention() {
        assertEquals(10, TreeSitterGrammar.entries.size)
        for (grammar in TreeSitterGrammar.entries) {
            assertEquals("tree-sitter-${grammar.langName}", grammar.libraryName)
            assertEquals("treesitter/${grammar.langName}", grammar.queryAssetDir)
        }
    }

    @Test
    fun kotlinLocalsQueryExistsAndValidStructure() {
        val localsFile = resolveModuleFile("src/main/assets/treesitter/kotlin/locals.scm")
        org.junit.Assert.assertTrue(
            "locals.scm fehlt für Kotlin: ${localsFile.path}",
            localsFile.isFile && localsFile.length() > 0
        )
        val text = localsFile.readText()
        org.junit.Assert.assertTrue("locals.scm muss @local.scope enthalten", text.contains("@local.scope"))
        org.junit.Assert.assertTrue("locals.scm muss @local.scope.members enthalten", text.contains("@local.scope.members"))
        org.junit.Assert.assertTrue("locals.scm muss @local.definition enthalten", text.contains("@local.definition"))
        org.junit.Assert.assertTrue("locals.scm muss @local.reference enthalten", text.contains("@local.reference"))

        // Klammer- und Syntaxprüfung ohne native Abhängigkeit
        var parenDepth = 0
        var bracketDepth = 0
        var inString = false
        for (ch in text) {
            if (ch == '"') inString = !inString
            if (!inString) {
                if (ch == '(') parenDepth++
                if (ch == ')') parenDepth--
                if (ch == '[') bracketDepth++
                if (ch == ']') bracketDepth--
            }
        }
        org.junit.Assert.assertEquals("Runde Klammern in locals.scm müssen ausgeglichen sein", 0, parenDepth)
        org.junit.Assert.assertEquals("Eckige Klammern in locals.scm müssen ausgeglichen sein", 0, bracketDepth)
    }

    @Test
    fun queryAssetsExistForEveryGrammar() {
        for (grammar in TreeSitterGrammar.entries) {
            val assetFile = resolveModuleFile("src/main/assets/${grammar.queryAssetDir}/highlights.scm")
            org.junit.Assert.assertTrue(
                "Highlight-Query fehlt für ${grammar.langName}: ${assetFile.path}",
                assetFile.isFile && assetFile.length() > 0
            )
        }
    }

    @Test
    fun nativeLibsExistForManuallyBuiltGrammars() {
        val manualLangs = listOf("bash", "cmake", "toml", "yaml")
        val abis = listOf("arm64-v8a", "armeabi-v7a")
        for (lang in manualLangs) {
            for (abi in abis) {
                val libFile = resolveModuleFile("src/main/jniLibs/$abi/libtree-sitter-$lang.so")
                org.junit.Assert.assertTrue(
                    "Native Bibliothek fehlt für $lang ($abi): ${libFile.path}",
                    libFile.isFile && libFile.length() > 0
                )
            }
        }
    }

    private fun resolveModuleFile(relativePath: String): java.io.File {
        val candidates = listOf(
            java.io.File(relativePath),
            java.io.File("feature/editor", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: java.io.File(relativePath)
    }

    @Test
    fun isAvailableHandlesMissingRuntimeGracefully() {
        for (grammar in TreeSitterGrammar.entries) {
            // In reinem JVM-Unit-Test ist die Core-Library nicht im java.library.path gebunden.
            // isAvailable() muss sicher false liefern, ohne zu werfen.
            val available = TreeSitterLanguageSupport.isAvailable(grammar)
            org.junit.Assert.assertFalse(available)
        }
    }
}

