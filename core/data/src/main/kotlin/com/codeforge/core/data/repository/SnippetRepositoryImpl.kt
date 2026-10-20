// Modul: :core:data
package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.domain.model.Snippet
import com.codeforge.core.domain.model.SnippetParser
import com.codeforge.core.domain.model.SnippetValidationResult
import com.codeforge.core.domain.model.VsCodeSnippetJsonSerializer
import com.codeforge.core.domain.repository.SnippetRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepositoryImpl(
    private val storageDir: File,
    private val builtinAssetLoader: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SnippetRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        storageDir = context.filesDir,
        builtinAssetLoader = createAssetLoader(context),
        ioDispatcher = Dispatchers.IO
    )

    private val mutex = Mutex()
    private val _snippets = MutableStateFlow<List<Snippet>>(emptyList())
    private val snippetsFile = File(storageDir, "snippets_user.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private var isInitialized = false

    private suspend fun ensureInitialized() {
        if (isInitialized) return
        mutex.withLock {
            if (isInitialized) return
            loadAll()
            isInitialized = true
        }
    }

    private suspend fun loadAll() = withContext(ioDispatcher) {
        val builtins = loadBuiltins()
        val (customs, disabledBuiltinNames) = loadUserSnippets()

        val activeBuiltins = builtins.map { b ->
            b.copy(isEnabled = !disabledBuiltinNames.contains(b.name))
        }

        _snippets.value = activeBuiltins + customs
    }

    private fun loadBuiltins(): List<Snippet> {
        val rawJson = builtinAssetLoader() ?: return FALLBACK_BUILTIN_SNIPPETS
        return VsCodeSnippetJsonSerializer.deserialize(rawJson).getOrDefault(FALLBACK_BUILTIN_SNIPPETS).map {
            it.copy(isBuiltin = true, isEnabled = true)
        }
    }

    private fun loadUserSnippets(): Pair<List<Snippet>, Set<String>> {
        if (!snippetsFile.exists()) return emptyList<Snippet>() to emptySet()
        return runCatching {
            val content = snippetsFile.readText()
            val root = json.parseToJsonElement(content).jsonObject

            val disabledSet = root["disabled_builtins"]?.jsonArray?.map {
                it.jsonPrimitive.content
            }?.toSet() ?: emptySet()

            val customsArray = root["custom_snippets"]?.jsonArray ?: JsonArray(emptyList())
            val customs = customsArray.map { elem ->
                val obj = elem.jsonObject
                Snippet(
                    id = obj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
                    prefix = obj["prefix"]?.jsonPrimitive?.content.orEmpty(),
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                    description = obj["description"]?.jsonPrimitive?.content.orEmpty(),
                    scope = obj["scope"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
                    body = obj["body"]?.jsonPrimitive?.content.orEmpty(),
                    isBuiltin = false,
                    isEnabled = obj["isEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                )
            }
            customs to disabledSet
        }.getOrDefault(emptyList<Snippet>() to emptySet())
    }

    private suspend fun persistUserSnippets() = withContext(ioDispatcher) {
        val all = _snippets.value
        val disabledBuiltins = all.filter { it.isBuiltin && !it.isEnabled }.map { it.name }
        val customs = all.filter { !it.isBuiltin }

        val root = buildJsonObject {
            put("disabled_builtins", JsonArray(disabledBuiltins.map { JsonPrimitive(it) }))
            put(
                "custom_snippets",
                JsonArray(
                    customs.map { c ->
                        buildJsonObject {
                            put("id", JsonPrimitive(c.id))
                            put("prefix", JsonPrimitive(c.prefix))
                            put("name", JsonPrimitive(c.name))
                            put("description", JsonPrimitive(c.description))
                            put("scope", JsonArray(c.scope.map { JsonPrimitive(it) }))
                            put("body", JsonPrimitive(c.body))
                            put("isEnabled", JsonPrimitive(c.isEnabled))
                        }
                    }
                )
            )
        }

        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        snippetsFile.writeText(json.encodeToString(JsonObject.serializer(), root))
    }

    override fun observeSnippets(): Flow<List<Snippet>> {
        return _snippets.asStateFlow()
    }

    override suspend fun getSnippetsForLanguage(languageId: String): List<Snippet> {
        ensureInitialized()
        val lang = languageId.lowercase()
        return _snippets.value.filter { snippet ->
            snippet.isEnabled && (snippet.scope.isEmpty() || snippet.scope.contains(lang))
        }
    }

    override suspend fun getAllSnippets(): List<Snippet> {
        ensureInitialized()
        return _snippets.value
    }

    override suspend fun saveSnippet(snippet: Snippet): Result<Unit> {
        ensureInitialized()
        return mutex.withLock {
            val validation = SnippetParser.validate(snippet.body)
            if (validation is SnippetValidationResult.Invalid) {
                return@withLock Result.failure(
                    IllegalArgumentException("Snippet-Syntaxfehler in Zeile ${validation.line}, Spalte ${validation.column}: ${validation.error}")
                )
            }

            val current = _snippets.value.toMutableList()
            val index = current.indexOfFirst { it.id == snippet.id || (!snippet.isBuiltin && it.name == snippet.name) }
            if (index >= 0) {
                current[index] = snippet.copy(isBuiltin = current[index].isBuiltin)
            } else {
                current.add(snippet.copy(isBuiltin = false))
            }
            _snippets.value = current
            persistUserSnippets()
            Result.success(Unit)
        }
    }

    override suspend fun deleteSnippet(id: String): Result<Unit> {
        ensureInitialized()
        return mutex.withLock {
            val current = _snippets.value.toMutableList()
            val existing = current.find { it.id == id }
                ?: return@withLock Result.failure(IllegalArgumentException("Snippet mit ID $id nicht gefunden"))

            if (existing.isBuiltin) {
                return@withLock Result.failure(IllegalStateException("Mitgelieferte Snippets können nicht gelöscht, sondern nur deaktiviert werden"))
            }

            current.removeAll { it.id == id }
            _snippets.value = current
            persistUserSnippets()
            Result.success(Unit)
        }
    }

    override suspend fun setSnippetEnabled(id: String, enabled: Boolean): Result<Unit> {
        ensureInitialized()
        return mutex.withLock {
            val current = _snippets.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
                ?: return@withLock Result.failure(IllegalArgumentException("Snippet mit ID $id nicht gefunden"))

            current[index] = current[index].copy(isEnabled = enabled)
            _snippets.value = current
            persistUserSnippets()
            Result.success(Unit)
        }
    }

    override suspend fun resetToDefaults(): Result<Unit> {
        ensureInitialized()
        return mutex.withLock {
            withContext(ioDispatcher) {
                if (snippetsFile.exists()) {
                    snippetsFile.delete()
                }
            }
            val builtins = loadBuiltins()
            _snippets.value = builtins
            Result.success(Unit)
        }
    }

    override suspend fun importSnippets(jsonContent: String): Result<List<Snippet>> {
        ensureInitialized()
        return mutex.withLock {
            val parseResult = VsCodeSnippetJsonSerializer.deserialize(jsonContent)
            if (parseResult.isFailure) {
                return@withLock Result.failure(parseResult.exceptionOrNull() ?: IllegalArgumentException("Ungültiges VS-Code-Snippet-JSON"))
            }

            val imported = parseResult.getOrThrow()
            if (imported.isEmpty()) {
                return@withLock Result.success(emptyList())
            }

            // Validate each snippet
            for (snippet in imported) {
                val validation = SnippetParser.validate(snippet.body)
                if (validation is SnippetValidationResult.Invalid) {
                    return@withLock Result.failure(
                        IllegalArgumentException("Snippet '${snippet.name}' ungültig (Zeile ${validation.line}, Spalte ${validation.column}): ${validation.error}")
                    )
                }
            }

            val current = _snippets.value.toMutableList()
            for (snippet in imported) {
                val idx = current.indexOfFirst { !it.isBuiltin && it.name == snippet.name }
                if (idx >= 0) {
                    current[idx] = snippet.copy(id = current[idx].id, isBuiltin = false)
                } else {
                    current.add(snippet.copy(isBuiltin = false))
                }
            }
            _snippets.value = current
            persistUserSnippets()
            Result.success(imported)
        }
    }

    override suspend fun exportSnippets(snippets: List<Snippet>?): Result<String> {
        ensureInitialized()
        val target = snippets ?: _snippets.value
        return Result.success(VsCodeSnippetJsonSerializer.serialize(target))
    }

    companion object {
        fun createAssetLoader(context: Context): () -> String? = {
            runCatching {
                context.assets.open("snippets/builtin_snippets.json").bufferedReader().use { it.readText() }
            }.getOrNull()
        }

        val FALLBACK_BUILTIN_SNIPPETS = listOf(
            Snippet(
                name = "fun",
                prefix = "fun",
                scope = setOf("kotlin"),
                description = "Function declaration",
                body = "fun \${1:name}(\${2:params}): \${3:Unit} {\n    \$0\n}",
                isBuiltin = true
            ),
            Snippet(
                name = "main",
                prefix = "main",
                scope = setOf("kotlin"),
                description = "Kotlin main entry point",
                body = "fun main(args: Array<String>) {\n    \$0\n}",
                isBuiltin = true
            ),
            Snippet(
                name = "composable",
                prefix = "composable",
                scope = setOf("kotlin"),
                description = "Jetpack Compose @Composable function",
                body = "@androidx.compose.runtime.Composable\nfun \${1:Name}(\n    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier\$0\n) {\n}",
                isBuiltin = true
            ),
            Snippet(
                name = "fori",
                prefix = "fori",
                scope = setOf("java"),
                description = "For loop with index",
                body = "for (int \${1:i} = 0; \${1:i} < \${2:max}; \${1:i}++) {\n    \$0\n}",
                isBuiltin = true
            ),
            Snippet(
                name = "linearlayout",
                prefix = "linearlayout",
                scope = setOf("xml"),
                description = "Android LinearLayout XML root",
                body = "<LinearLayout\n    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    android:layout_width=\"\${1|match_parent,wrap_content|}\"\n    android:layout_height=\"\${2|wrap_content,match_parent|}\"\n    android:orientation=\"\${3|vertical,horizontal|}\">\n    \$0\n</LinearLayout>",
                isBuiltin = true
            )
        )
    }
}
