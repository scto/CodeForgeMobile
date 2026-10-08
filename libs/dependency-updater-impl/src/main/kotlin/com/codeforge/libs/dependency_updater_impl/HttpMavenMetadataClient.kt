/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.MavenRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Lädt `maven-metadata.xml` per HTTP(S); Ergebnisse (auch 404) werden [ttlMillis] gecacht. */
@Singleton
class HttpMavenMetadataClient internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val ttlMillis: Long,
    private val clock: () -> Long
) : MavenMetadataClient {

    @Inject constructor() : this(Dispatchers.IO, 15 * 60_000L, System::currentTimeMillis)

    private class Cached(val timeMillis: Long, val versions: List<String>?)

    private val cache = ConcurrentHashMap<String, Cached>()
    private val permits = Semaphore(MAX_PARALLEL_REQUESTS)

    override suspend fun versions(
        repository: MavenRepository,
        coordinate: LibraryCoordinate,
        forceRefresh: Boolean
    ): Result<List<String>?> {
        val url = MavenMetadata.metadataUrl(repository, coordinate)
        if (!forceRefresh) {
            cache[url]?.takeIf { clock() - it.timeMillis < ttlMillis }?.let { return Result.success(it.versions) }
        }
        return permits.withPermit {
            try {
                val versions = withContext(ioDispatcher) { fetch(url) }
                cache[url] = Cached(clock(), versions)
                Result.success(versions)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** `null` bei 404, Exception bei Netz-/Serverfehlern. */
    private fun fetch(url: String): List<String>? {
        val connection = java.net.URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "CodeForgeMobile-DependencyUpdater")
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> MavenMetadata.parseVersions(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException(Res.string(R.string.depupdate_http_fuer, code, url))
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_PARALLEL_REQUESTS = 6
    }
}
