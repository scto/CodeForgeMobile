/**
 * Modul: :libs:indexing-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.ProjectIndex
import com.codeforge.libs.indexing_api.ProjectIndexer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ProjectIndexerImpl internal constructor(
    private val ioDispatcher: CoroutineDispatcher
) : ProjectIndexer {

    @Inject constructor() : this(Dispatchers.IO)

    private val states = ConcurrentHashMap<String, MutableStateFlow<ProjectIndex?>>()
    private val mutex = Mutex()

    override fun observe(rootPath: String): Flow<ProjectIndex?> = stateFor(rootPath).asStateFlow()

    override fun cached(rootPath: String): ProjectIndex? = stateFor(rootPath).value

    override suspend fun index(rootPath: String, forceRefresh: Boolean): Result<ProjectIndex> {
        val state = stateFor(rootPath)
        if (!forceRefresh) state.value?.let { return Result.success(it) }
        return mutex.withLock {
            if (!forceRefresh) state.value?.let { return@withLock Result.success(it) }
            runCatching { withContext(ioDispatcher) { ProjectIndexBuilder.build(rootPath) } }
                .onSuccess { state.value = it }
        }
    }

    override fun invalidate(rootPath: String) {
        stateFor(rootPath).value = null
    }

    private fun stateFor(rootPath: String): MutableStateFlow<ProjectIndex?> =
        states.getOrPut(File(rootPath).absoluteFile.normalize().path) { MutableStateFlow(null) }
}
