package com.codeforge.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileHeaderRendererTest {

    @Test
    fun testRenderKotlinHeader() {
        val ctx = FileHeaderContext(
            filename = "User.kt",
            moduleName = ":core:domain",
            author = "Alice"
        )
        val header = FileHeaderRenderer.render("kt", ctx)
        assertNotNull(header)
        assertTrue(header!!.contains("Modul: :core:domain"))
        assertTrue(header.contains("@author Alice"))
    }

    @Test
    fun testRenderXmlHeaderPlacement() {
        val ctx = FileHeaderContext(
            filename = "strings.xml",
            moduleName = ":core:resources",
            author = "Bob"
        )
        val originalXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
        val result = FileHeaderRenderer.applyHeaderToContent(originalXml, "xml", ctx)

        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(result.contains("<!--\n  Modul: :core:resources\n  @author Bob\n-->"))
    }

    @Test
    fun testRenderShellHeaderPlacement() {
        val ctx = FileHeaderContext(
            filename = "build.sh",
            moduleName = ":scripts",
            author = "Charlie"
        )
        val originalSh = "#!/bin/bash\nset -euo pipefail\necho hello"
        val result = FileHeaderRenderer.applyHeaderToContent(originalSh, "sh", ctx)

        assertTrue(result.startsWith("#!/bin/bash\n# Modul: :scripts\n# @author Charlie"))
    }

    @Test
    fun testJsonReturnsNull() {
        val ctx = FileHeaderContext(
            filename = "data.json",
            moduleName = ":app",
            author = "Alice"
        )
        assertNull(FileHeaderRenderer.render("json", ctx))
    }

    @Test
    fun testMissingValueDropsLine() {
        val ctx = FileHeaderContext(
            filename = "Sample.kt",
            moduleName = null, // module line should be dropped
            author = "Alice"
        )
        val header = FileHeaderRenderer.render("kt", ctx)
        assertNotNull(header)
        assertFalse(header!!.contains("Modul:"))
        assertTrue(header.contains("@author Alice"))
    }

    @Test
    fun testCustomTemplate() {
        val ctx = FileHeaderContext(
            filename = "Test.rs",
            moduleName = "core",
            author = "Dev",
            year = "2026"
        )
        val template = "// Copyright {{year}} by {{author}}\n// File: {{filename}}"
        val header = FileHeaderRenderer.render("rs", ctx, customTemplate = template)
        assertEquals("// Copyright 2026 by Dev\n// File: Test.rs", header)
    }

    @Test
    fun testKotlinPackagePlacement() {
        val ctx = FileHeaderContext(
            filename = "Foo.kt",
            moduleName = ":feature:editor",
            author = "Alice"
        )
        val source = "package com.example\n\nclass Foo"
        val result = FileHeaderRenderer.applyHeaderToContent(source, "kt", ctx)
        assertTrue(result.startsWith("/**\n * Modul: :feature:editor\n * @author Alice\n */\npackage com.example"))
    }

    @Test
    fun testYamlAndTomlDefaultTemplates() {
        val ctx = FileHeaderContext(
            filename = "config.yaml",
            moduleName = "app",
            author = "Tester"
        )
        val yamlHeader = FileHeaderRenderer.render("yaml", ctx)
        assertNotNull(yamlHeader)
        assertTrue(yamlHeader!!.startsWith("# Modul: app"))

        val tomlHeader = FileHeaderRenderer.render("toml", ctx)
        assertNotNull(tomlHeader)
        assertTrue(tomlHeader!!.startsWith("# Modul: app"))
    }
}
