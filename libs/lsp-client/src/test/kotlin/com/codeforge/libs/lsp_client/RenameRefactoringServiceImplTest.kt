package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspFileRename
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspTextEdit
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.refactoring.RenameRefactorSource
import com.codeforge.core.domain.repository.LspClientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenameRefactoringServiceImplTest {

    private val src = "/p/app/src/main/kotlin"
    private val files = mutableMapOf(
        "$src/com/a/feature/Home.kt" to lines("package com.a.feature", "", "class Home"),
        "$src/com/a/Main.kt" to lines("package com.a", "", "import com.a.feature.Home"),
        "/p/app/src/main/AndroidManifest.xml" to "<manifest/>"
    )
    private val rename = LspFileRename("$src/com/a/feature", "$src/com/a/home")

    private fun lines(vararg l: String) = l.joinToString(separator = "\n", postfix = "\n")

    private fun service(lsp: LspClientRepository) =
        RenameRefactoringServiceImpl(lsp, { files[it] }, { files.keys.asSequence() })

    @Test
    fun fallbackWithoutServer() = runTest {
        val plan = service(FakeLsp(supports = false)).plan(rename, "/p")!!
        assertEquals(RenameRefactorSource.PACKAGE_FALLBACK, plan.source)
        assertEquals("com.a.feature", plan.oldPackage)
        assertEquals(2, plan.fileCount)
        assertEquals(lines("package com.a.home", "", "class Home"), plan.changes.first { it.path.endsWith("Home.kt") }.newContent)
    }

    @Test
    fun serverEditsWin() = runTest {
        val edit = LspWorkspaceEdit(
            changes = mapOf(
                "file://$src/com/a/Main.kt" to listOf(LspTextEdit(LspRange(LspPosition(2, 7), LspPosition(2, 20)), "com.a.home"))
            )
        )
        val plan = service(FakeLsp(supports = true, edit = edit)).plan(rename, "/p")!!
        assertEquals(RenameRefactorSource.LANGUAGE_SERVER, plan.source)
        assertEquals(lines("package com.a", "", "import com.a.home.Home"), plan.changes.single().newContent)
    }

    @Test
    fun emptyServerResultFallsBack() = runTest {
        val plan = service(FakeLsp(supports = true, edit = null)).plan(rename, "/p")!!
        assertEquals(RenameRefactorSource.PACKAGE_FALLBACK, plan.source)
    }

    @Test
    fun nonSourceRenameHasNoPlan() = runTest {
        assertNull(service(FakeLsp(supports = false)).plan(LspFileRename("/p/docs/a", "/p/docs/b"), "/p"))
    }

    private class FakeLsp(private val supports: Boolean, private val edit: LspWorkspaceEdit? = null) : LspClientRepository {
        override val serverState: StateFlow<LspServerState> = MutableStateFlow(LspServerState.RUNNING)
        override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = emptyFlow()
        override val serverCapabilities: StateFlow<LspServerCapabilities?> = MutableStateFlow(null)
        override var applyEditHandler: (suspend (LspWorkspaceEdit) -> Boolean)? = null
        override fun supportsWillRenameFiles(): Boolean = supports
        override suspend fun willRenameFiles(renames: List<LspFileRename>): Result<LspWorkspaceEdit?> = Result.success(edit)
        override suspend fun start(serverCommand: List<String>, workspaceRootPath: String, extraProcessEnv: Map<String, String>) =
            Result.success(Unit)
        override suspend fun stop() = Unit
        override suspend fun didOpen(path: String, languageId: String, content: String) = Unit
        override suspend fun didChange(path: String, newContent: String, version: Int) = Unit
        override suspend fun didClose(path: String) = Unit
        override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> = unused()
        override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> = unused()
        override suspend fun requestFormat(path: String, content: String): Result<String> = unused()
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> = unused()
        override suspend fun requestCodeActions(path: String, range: LspRange, diagnostics: List<LspDiagnostic>): Result<List<LspCodeAction>> =
            unused()
        override suspend fun requestPrepareRename(path: String, position: LspPosition): Result<LspPrepareRenameResult?> = unused()
        override suspend fun requestRename(path: String, position: LspPosition, newName: String): Result<LspWorkspaceEdit?> = unused()
        override suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> = unused()
        override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> = unused()
        override suspend fun requestReferences(path: String, position: LspPosition, includeDeclaration: Boolean): Result<List<LspLocation>> =
            unused()
        override suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> = unused()
        private fun <T> unused(): Result<T> = Result.failure(UnsupportedOperationException())
    }
}
