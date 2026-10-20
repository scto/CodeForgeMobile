// Modul: :core:domain
package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Year

class SnippetVariableResolverTest {

    @Test
    fun `derivePackageName extracts kotlin package from standard src main kotlin path`() {
        val path = "/home/user/project/app/src/main/kotlin/com/codeforge/app/ui/MainScreen.kt"
        val pkg = SnippetVariableResolver.derivePackageName(path)
        assertEquals("com.codeforge.app.ui", pkg)
    }

    @Test
    fun `derivePackageName extracts java package from standard src main java path`() {
        val path = "/workspace/MyProject/src/main/java/org/example/utils/Helper.java"
        val pkg = SnippetVariableResolver.derivePackageName(path)
        assertEquals("org.example.utils", pkg)
    }

    @Test
    fun `deriveClassName extracts file name without extension`() {
        val path = "/project/src/main/kotlin/com/example/UserProfileViewModel.kt"
        val className = SnippetVariableResolver.deriveClassName(path)
        assertEquals("UserProfileViewModel", className)
    }

    @Test
    fun `resolveVariables replaces TM_FILENAME and TM_FILENAME_BASE`() {
        val template = "// File: \$TM_FILENAME\n// Base: \$TM_FILENAME_BASE"
        val ctx = SnippetVariableContext(
            filePath = "/path/to/MyComponent.kt",
            currentYear = "2026"
        )
        val result = SnippetVariableResolver.resolve(template, ctx)
        val expected = "// File: MyComponent.kt\n// Base: MyComponent"
        assertEquals(expected, result)
    }

    @Test
    fun `resolveVariables replaces PACKAGE, CLASSNAME, and CURRENT_YEAR`() {
        val template = "package \$PACKAGE\n\n// Copyright \$CURRENT_YEAR\nclass \$CLASSNAME"
        val ctx = SnippetVariableContext(
            filePath = "/src/main/kotlin/com/codeforge/domain/User.kt",
            currentYear = "2026"
        )
        val result = SnippetVariableResolver.resolve(template, ctx)
        val expected = "package com.codeforge.domain\n\n// Copyright 2026\nclass User"
        assertEquals(expected, result)
    }

    @Test
    fun `resolveVariables replaces CLIPBOARD and fallback variables`() {
        val template = "val data = \"\$CLIPBOARD\"\nval fallback = \${UNKNOWN_VAR:default_val}"
        val ctx = SnippetVariableContext(
            clipboardContent = "pasted_text"
        )
        val result = SnippetVariableResolver.resolve(template, ctx)
        val expected = "val data = \"pasted_text\"\nval fallback = default_val"
        assertEquals(expected, result)
    }
}
