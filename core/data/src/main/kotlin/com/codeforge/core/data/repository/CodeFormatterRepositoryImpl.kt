package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.EditorConfigResolver
import com.codeforge.core.domain.model.FormattedCode
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.UnifiedFormatOptions
import com.codeforge.core.domain.model.UnifiedFormatSource
import com.codeforge.core.domain.repository.CodeFormatterRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.code_tools.format.CodeFormatter
import com.codeforge.libs.code_tools.format.FormatLanguage
import com.codeforge.libs.code_tools.format.FormatOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodeFormatterRepositoryImpl @Inject constructor(
    private val lspClient: LspClientRepository,
    private val processExecutor: ProcessExecutor
) : CodeFormatterRepository {

    override suspend fun formatFile(
        path: String,
        content: String,
        projectRoot: String?,
        fallbackOptions: UnifiedFormatOptions
    ): Result<FormattedCode> = withContext(Dispatchers.Default) {
        val options = resolveOptions(path, projectRoot, fallbackOptions)

        // 1. LSP-Formatierung, wenn Server läuft und Capability vorhanden
        val lspResult = tryLspFormatFile(path, content)
        if (lspResult != null) {
            return@withContext lspResult
        }

        // 2. Termux-Prozess (ktlint / google-java-format)
        val processResult = tryProcessFormat(path, content)
        if (processResult != null) {
            return@withContext processResult
        }

        // 3. Eingebauter CodeFormatter (vereinfachte Formatierung)
        val builtinResult = formatWithBuiltin(path, content, options)
        Result.success(builtinResult)
    }

    override suspend fun formatRange(
        path: String,
        content: String,
        range: LspRange,
        projectRoot: String?,
        fallbackOptions: UnifiedFormatOptions
    ): Result<FormattedCode> = withContext(Dispatchers.Default) {
        val options = resolveOptions(path, projectRoot, fallbackOptions)

        // 1. LSP-Range-Formatierung
        val lspResult = tryLspFormatRange(path, content, range)
        if (lspResult != null) {
            return@withContext lspResult
        }

        // 2. Fallback: Range-Zeilen extrahieren, formatieren und ersetzen
        val rangeFormatted = formatRangeWithBuiltin(content, range, path, options)
        Result.success(rangeFormatted)
    }

    private fun resolveOptions(
        filePath: String,
        projectRoot: String?,
        fallback: UnifiedFormatOptions
    ): UnifiedFormatOptions {
        var editorConfigFile: File? = null
        if (!projectRoot.isNullOrBlank()) {
            val rootFile = File(projectRoot, ".editorconfig")
            if (rootFile.isFile) {
                editorConfigFile = rootFile
            }
        }
        if (editorConfigFile == null) {
            var dir = File(filePath).parentFile
            while (dir != null && dir.exists()) {
                val candidate = File(dir, ".editorconfig")
                if (candidate.isFile) {
                    editorConfigFile = candidate
                    break
                }
                dir = dir.parentFile
            }
        }
        val configText = editorConfigFile?.takeIf { it.isFile }?.readText()
        return EditorConfigResolver.resolve(configText, filePath, fallback)
    }

    private suspend fun tryLspFormatFile(path: String, content: String): Result<FormattedCode>? {
        if (lspClient.serverState.value != LspServerState.RUNNING) return null
        val caps = lspClient.serverCapabilities.value ?: return null
        if (!caps.formattingProvider) return null

        val result = lspClient.requestFormat(path, content)
        return result.map { formatted ->
            FormattedCode(
                text = formatted,
                source = UnifiedFormatSource.LSP,
                sourceName = "LSP",
                isUnchanged = (formatted == content)
            )
        }
    }

    private suspend fun tryLspFormatRange(
        path: String,
        content: String,
        range: LspRange
    ): Result<FormattedCode>? {
        if (lspClient.serverState.value != LspServerState.RUNNING) return null
        val caps = lspClient.serverCapabilities.value ?: return null
        if (!caps.rangeFormattingProvider && !caps.formattingProvider) return null

        val result = if (caps.rangeFormattingProvider) {
            lspClient.requestRangeFormat(path, content, range)
        } else {
            lspClient.requestFormat(path, content)
        }

        return result.map { formatted ->
            FormattedCode(
                text = formatted,
                source = UnifiedFormatSource.LSP,
                sourceName = "LSP",
                isUnchanged = (formatted == content)
            )
        }
    }

    private suspend fun tryProcessFormat(path: String, content: String): Result<FormattedCode>? {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> formatWithKtlint(content)
            "java" -> formatWithGoogleJavaFormat(content)
            else -> null
        }
    }

    private suspend fun formatWithKtlint(content: String): Result<FormattedCode>? {
        val ktlintCandidates = listOf(
            "/data/data/com.codeforge.app/files/usr/bin/ktlint",
            "/data/data/com.termux/files/usr/bin/ktlint"
        )
        val ktlintPath = processExecutor.findExecutable("ktlint", ktlintCandidates) ?: return null

        val execResult = processExecutor.execute(
            command = listOf(ktlintPath, "--format", "-"),
            stdin = content,
            timeoutSeconds = 10
        )

        if (execResult.timedOut) {
            return Result.failure(TimeoutException(Res.string(R.string.editor_format_process_timeout, 10)))
        }

        if (execResult.exitCode == 0 && execResult.stdout.isNotEmpty()) {
            return Result.success(
                FormattedCode(
                    text = execResult.stdout,
                    source = UnifiedFormatSource.KTLINT,
                    sourceName = "ktlint",
                    isUnchanged = (execResult.stdout == content)
                )
            )
        }

        // Prüfen auf Syntaxfehler
        if (execResult.exitCode != 0 && execResult.stderr.contains("syntax", ignoreCase = true)) {
            val errorMsg = execResult.stderr.lines().firstOrNull { it.isNotBlank() } ?: "Syntax error"
            return Result.failure(IllegalStateException(Res.string(R.string.editor_format_syntax_error, errorMsg)))
        }

        return null
    }

    private suspend fun formatWithGoogleJavaFormat(content: String): Result<FormattedCode>? {
        val gjfCandidates = listOf(
            "/data/data/com.codeforge.app/files/usr/bin/google-java-format",
            "/data/data/com.termux/files/usr/bin/google-java-format"
        )
        val gjfPath = processExecutor.findExecutable("google-java-format", gjfCandidates) ?: return null

        val execResult = processExecutor.execute(
            command = listOf(gjfPath, "-"),
            stdin = content,
            timeoutSeconds = 10
        )

        if (execResult.timedOut) {
            return Result.failure(TimeoutException(Res.string(R.string.editor_format_process_timeout, 10)))
        }

        if (execResult.exitCode == 0 && execResult.stdout.isNotEmpty()) {
            return Result.success(
                FormattedCode(
                    text = execResult.stdout,
                    source = UnifiedFormatSource.GOOGLE_JAVA_FORMAT,
                    sourceName = "google-java-format",
                    isUnchanged = (execResult.stdout == content)
                )
            )
        }

        if (execResult.exitCode != 0) {
            val errorMsg = execResult.stderr.lines().firstOrNull { it.isNotBlank() } ?: "Formatting error"
            return Result.failure(IllegalStateException(Res.string(R.string.editor_format_syntax_error, errorMsg)))
        }

        return null
    }

    private fun formatWithBuiltin(
        path: String,
        content: String,
        options: UnifiedFormatOptions
    ): FormattedCode {
        val lang = CodeFormatter.languageFor(path)
        val toolOptions = FormatOptions(
            indentSize = options.indentSize,
            useTabs = options.useTabs,
            trimTrailingWhitespace = options.trimTrailingWhitespace,
            ensureFinalNewline = options.ensureFinalNewline
        )
        val result = CodeFormatter.format(content, lang, toolOptions)
        return FormattedCode(
            text = result.text,
            source = UnifiedFormatSource.BUILTIN_SIMPLIFIED,
            sourceName = Res.string(R.string.editor_format_builtin_notice),
            isUnchanged = (result.text == content),
            warning = result.warning
        )
    }

    private fun formatRangeWithBuiltin(
        content: String,
        range: LspRange,
        path: String,
        options: UnifiedFormatOptions
    ): FormattedCode {
        val lines = content.lines()
        val startLine = range.start.line.coerceIn(0, lines.lastIndex)
        val endLine = range.end.line.coerceIn(startLine, lines.lastIndex)

        val selectedSlice = lines.subList(startLine, endLine + 1).joinToString("\n")
        val lang = CodeFormatter.languageFor(path)
        val toolOptions = FormatOptions(
            indentSize = options.indentSize,
            useTabs = options.useTabs,
            trimTrailingWhitespace = options.trimTrailingWhitespace,
            ensureFinalNewline = false
        )
        val formattedSlice = CodeFormatter.format(selectedSlice, lang, toolOptions).text.trimEnd('\n')

        val newLines = lines.toMutableList()
        val formattedSliceLines = formattedSlice.lines()

        for (i in endLine downTo startLine) {
            newLines.removeAt(i)
        }
        newLines.addAll(startLine, formattedSliceLines)
        val newContent = newLines.joinToString("\n")

        return FormattedCode(
            text = newContent,
            source = UnifiedFormatSource.BUILTIN_SIMPLIFIED,
            sourceName = Res.string(R.string.editor_format_builtin_notice),
            isUnchanged = (newContent == content)
        )
    }
}
