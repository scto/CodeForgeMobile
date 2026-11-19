/**
 * Plugin: Python-Tests
 *
 * [TestProvider] für pytest: Start-Knöpfe an Testzeilen, „Tests dieser Datei ausführen“ in der Befehlspalette.
 */
package com.codeforge.plugin.pytest

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginLanguagePoints
import com.codeforge.libs.plugin_api.PluginTestItem
import com.codeforge.libs.plugin_api.RunConsole
import com.codeforge.libs.plugin_api.TestProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PythonTestsPlugin : CodeForgePlugin {
    override fun onLoad(context: PluginContext) {
        context.register(PluginLanguagePoints.TEST_PROVIDER, PytestProvider(context))
    }

    override fun onUnload() = Unit
}

private class PytestProvider(private val context: PluginContext) : TestProvider {
    override val id = "pytest"

    override fun appliesTo(filePath: String, languageId: String): Boolean = Pytest.isTestFile(File(filePath).name)

    override suspend fun discover(context: EditorContext): List<PluginTestItem> =
        Pytest.discover(context.text).map { PluginTestItem(it.id, it.id.substringAfterLast("::"), it.line) }

    override suspend fun run(filePath: String, tests: List<PluginTestItem>, console: RunConsole): Int = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val targets = if (tests.isEmpty()) listOf(q(file.name)) else tests.map { q("${file.name}::${it.id}") }
        val command = "python -m pytest -q -rA --color=no " + targets.joinToString(" ")
        val spec = context.hostServices.buildShellCommand(command, file.parent ?: console.projectRoot)
        console.println("$ $command")
        val process = ProcessBuilder(spec.command).directory(File(spec.workingDirectory)).redirectErrorStream(true)
            .also { it.environment().putAll(spec.processEnv) }.start()
        val lines = ArrayList<String>()
        try {
            process.inputStream.bufferedReader().forEachLine { lines += it; console.println(it) }
            val code = process.waitFor()
            Pytest.results(lines).forEach { (id, ok, msg) -> console.reportTest(id, ok, msg) }
            code
        } finally {
            process.destroy()
        }
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
