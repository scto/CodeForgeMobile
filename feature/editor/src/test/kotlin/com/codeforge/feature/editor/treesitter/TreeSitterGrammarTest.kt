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
}
