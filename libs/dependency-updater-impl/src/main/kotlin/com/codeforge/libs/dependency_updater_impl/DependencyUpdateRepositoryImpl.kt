/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.dependency_updater_api.ApplyResult
import com.codeforge.libs.dependency_updater_api.CheckStatus
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.dependency_updater_api.ProjectUpdateState
import com.codeforge.libs.indexing_api.ProjectIndex
import com.codeforge.libs.indexing_api.ProjectIndexer
import com.codeforge.libs.indexing_api.RepositoryScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class DependencyUpdateRepositoryImpl internal constructor(
    private val indexer: ProjectIndexer,
    private val metadata: MavenMetadataClient,
    private val dismissals: DismissalStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) : DependencyUpdateRepository {

    @Inject constructor(
        indexer: ProjectIndexer,
        metadata: MavenMetadataClient,
        dismissals: DismissalStore
    ) : this(indexer, metadata, dismissals, CoroutineScope(SupervisorJob() + Dispatchers.IO), Dispatchers.IO)

    private val states = MutableStateFlow<Map<String, ProjectUpdateState>>(emptyMap())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lock = Mutex()

    override fun observe(rootPath: String): Flow<ProjectUpdateState> {
        val key = key(rootPath)
        return states.map { it[key] ?: ProjectUpdateState(key) }.distinctUntilChanged()
    }

    override fun onProjectOpened(rootPath: String) {
        val key = key(rootPath)
        // „Ask later“ gilt nur bis zum nächsten Öffnen → zurücksetzen; Dismiss bleibt (persistiert)
        modify(key) { it.copy(snoozedKeys = emptySet()) }
        jobs.remove(key)?.cancel()
        jobs[key] = scope.launch {
            // Datei-IO (Dismiss-Liste) nicht auf dem Aufrufer-(Main-)Thread
            val dismissed = withContext(ioDispatcher) { dismissals.dismissed(key) }
            modify(key) { it.copy(dismissedKeys = dismissed) }
            check(key, force = false)
        }
    }

    override suspend fun check(rootPath: String, force: Boolean): Result<List<DependencyUpdate>> {
        val key = key(rootPath)
        return lock.withLock {
            modify(key) { it.copy(status = CheckStatus.CHECKING, errorMessage = null) }
            try {
                val index = indexer.index(key, forceRefresh = true).getOrThrow()
                val candidates = withContext(ioDispatcher) { DependencyScanner.scan(index, ::readFile) }
                val wanted = candidates.flatMap { it.usages }.map { it.coordinate to it.scope }.distinct()

                val fetched: Map<Pair<LibraryCoordinate, RepositoryScope>, Result<List<String>?>> = coroutineScope {
                    wanted.map { (coordinate, usageScope) ->
                        async { (coordinate to usageScope) to fetchVersions(index, coordinate, usageScope, force) }
                    }.awaitAll().toMap()
                }
                if (wanted.isNotEmpty() && fetched.values.all { it.isFailure }) {
                    throw fetched.values.first().exceptionOrNull() ?: IllegalStateException(Res.string(R.string.depupdate_server_nicht_erreichbar))
                }

                val updates = UpdateCalculator.compute(candidates) { c, s -> fetched[c to s]?.getOrNull() }
                modify(key) {
                    it.copy(
                        status = CheckStatus.IDLE,
                        updates = updates,
                        dismissedKeys = dismissals.dismissed(key),
                        errorMessage = null,
                        lastCheckedMillis = System.currentTimeMillis()
                    )
                }
                Result.success(updates)
            } catch (e: CancellationException) {
                modify(key) { it.copy(status = CheckStatus.IDLE) }
                throw e
            } catch (e: Exception) {
                modify(key) { it.copy(status = CheckStatus.FAILED, errorMessage = e.message ?: e.javaClass.simpleName) }
                Result.failure(e)
            }
        }
    }

    override suspend fun dismiss(rootPath: String, update: DependencyUpdate) {
        val key = key(rootPath)
        withContext(ioDispatcher) { dismissals.add(key, update.key) }
        modify(key) { it.copy(dismissedKeys = it.dismissedKeys + update.key) }
    }

    override fun snooze(rootPath: String, update: DependencyUpdate) {
        modify(key(rootPath)) { it.copy(snoozedKeys = it.snoozedKeys + update.key) }
    }

    override suspend fun apply(rootPath: String, updates: List<DependencyUpdate>): Result<ApplyResult> {
        val key = key(rootPath)
        val result = try {
            lock.withLock {
                val index = indexer.index(key, forceRefresh = true).getOrThrow()
                withContext(ioDispatcher) { applyOnDisk(index, updates) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
        indexer.invalidate(key)
        check(key, force = false) // neu prüfen: angewendete Updates verschwinden aus dem State
        return Result.success(result)
    }

    override fun annotate(rootPath: String, filePath: String, text: String): List<FileUpdateAnnotation> {
        val state = states.value[key(rootPath)] ?: return emptyList()
        return FileAnnotator.annotate(filePath, text, state.pending)
    }

    // ---- intern -----------------------------------------------------------------------------

    private fun applyOnDisk(index: ProjectIndex, updates: List<DependencyUpdate>): ApplyResult {
        val fresh = DependencyScanner.scan(index, ::readFile)
        val editsByFile = LinkedHashMap<String, MutableList<TextEdit>>()
        val filesByUpdate = LinkedHashMap<DependencyUpdate, MutableSet<String>>()
        val failed = ArrayList<Pair<DependencyUpdate, String>>()

        for (update in updates) {
            val wanted = update.coordinates.toSet()
            val match = fresh.firstOrNull { c ->
                c.currentVersion == update.currentVersion && c.usages.map { it.coordinate }.toSet() == wanted
            }
            if (match == null) {
                failed += update to Res.string(R.string.depupdate_nicht_mehr_gefunden_datei_wurde)
                continue
            }
            for (loc in match.locations) {
                editsByFile.getOrPut(loc.filePath) { ArrayList() } +=
                    TextEdit(loc.startOffset, loc.endOffset, update.currentVersion, update.newVersion)
                filesByUpdate.getOrPut(update) { LinkedHashSet() } += loc.filePath
            }
        }

        val changed = ArrayList<String>()
        val failedFiles = HashMap<String, String>()
        for ((path, edits) in editsByFile) {
            try {
                val file = File(path)
                val newText = VersionApplier.apply(file.readText(), edits).getOrThrow()
                AtomicFiles.write(file, newText)
                changed += path
            } catch (e: Exception) {
                failedFiles[path] = e.message ?: e.javaClass.simpleName
            }
        }

        val applied = ArrayList<DependencyUpdate>()
        for ((update, files) in filesByUpdate) {
            val bad = files.firstOrNull { it in failedFiles }
            if (bad == null) applied += update else failed += update to "${File(bad).name}: ${failedFiles[bad]}"
        }
        return ApplyResult(applied, failed, changed)
    }

    private suspend fun fetchVersions(
        index: ProjectIndex,
        coordinate: LibraryCoordinate,
        usageScope: RepositoryScope,
        force: Boolean
    ): Result<List<String>?> {
        var error: Throwable? = null
        for (repo in RepositorySelector.reposFor(index, usageScope)) {
            val r = metadata.versions(repo, coordinate, force)
            val versions = r.getOrNull()
            if (versions != null) return Result.success(versions)
            r.exceptionOrNull()?.let { error = it }
        }
        return error?.let { Result.failure(it) } ?: Result.success(null)
    }

    private fun readFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    private fun modify(key: String, transform: (ProjectUpdateState) -> ProjectUpdateState) {
        states.update { it + (key to transform(it[key] ?: ProjectUpdateState(key))) }
    }

    private fun key(rootPath: String): String = File(rootPath).absoluteFile.normalize().path
}
