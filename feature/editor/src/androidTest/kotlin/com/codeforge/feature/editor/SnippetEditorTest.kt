// Modul: :feature:editor
package com.codeforge.feature.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetVariableContext
import com.codeforge.core.domain.model.SnippetVariableResolver
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.snippet.SnippetController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentierter Android-Test für Snippets und Tabstopp-Navigation in sora-editor (Task 33).
 */
@RunWith(AndroidJUnit4::class)
class SnippetEditorTest {

    private lateinit var context: Context
    private lateinit var editor: CodeEditor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        editor = CodeEditor(context)
    }

    @Test
    fun soraCodeSnippetParserParsesTabstops() {
        val snippetText = "fun \${1:name}(\${2:args}): \${3:Unit} {\n    \$0\n}"
        val codeSnippet = CodeSnippetParser.parse(snippetText)
        assertNotNull(codeSnippet)
    }

    @Test
    fun snippetControllerNavigationLifecycle() {
        val controller = editor.snippetController
        assertFalse(controller.isInSnippet())

        val snippetText = "for (int \${1:i} = 0; \${1:i} < \${2:max}; \${1:i}++) {\n    \$0\n}"
        val codeSnippet = CodeSnippetParser.parse(snippetText)

        // Snippet starten
        controller.startSnippet(0, codeSnippet, "")
        assertTrue(controller.isInSnippet())

        // Nächster Tabstopp ($2)
        controller.shiftToNextTabStop()
        assertTrue(controller.isInSnippet())

        // Vorheriger Tabstopp ($1)
        controller.shiftToPreviousTabStop()
        assertTrue(controller.isInSnippet())

        // Beenden (Esc)
        controller.stopSnippet()
        assertFalse(controller.isInSnippet())
    }

    @Test
    fun simpleSnippetCompletionItemGeneratesValidItem() {
        val snippet = Snippet(
            id = "test-fun",
            prefix = "fun",
            name = "Function",
            description = "Function declaration",
            scope = setOf("kotlin"),
            body = "fun \${1:name}() {\n    \$0\n}"
        )

        val codeSnippet = CodeSnippetParser.parse(snippet.body)
        val item = SimpleSnippetCompletionItem(
            snippet.prefix,
            snippet.description,
            SnippetDescription(snippet.prefix.length, codeSnippet, true)
        )

        assertEquals("fun", item.label)
        assertEquals("Function declaration", item.desc)
    }

    @Test
    fun fileVariableResolverIntegratesWithSnippetVariableResolver() {
        val path = "/data/user/0/com.codeforge.app/files/project/src/main/kotlin/com/codeforge/sample/UserViewModel.kt"
        val resolvedClass = SnippetVariableResolver.deriveClassName(path)
        val resolvedPackage = SnippetVariableResolver.derivePackageName(path)

        assertEquals("UserViewModel", resolvedClass)
        assertEquals("com.codeforge.sample", resolvedPackage)

        val template = "package \$PACKAGE\n\nclass \$CLASSNAME"
        val resolved = SnippetVariableResolver.resolve(
            template,
            SnippetVariableContext(filePath = path)
        )

        assertEquals("package com.codeforge.sample\n\nclass UserViewModel", resolved)
    }
}
