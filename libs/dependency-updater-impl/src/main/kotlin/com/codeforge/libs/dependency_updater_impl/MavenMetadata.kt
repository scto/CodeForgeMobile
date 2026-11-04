/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import com.codeforge.libs.dependency_updater_api.LibraryCoordinate
import com.codeforge.libs.indexing_api.MavenRepository

/** Liest `maven-metadata.xml`. */
object MavenMetadata {

    private val versionsBlock = Regex("""<versions>(.*?)</versions>""", RegexOption.DOT_MATCHES_ALL)
    private val versionTag = Regex("""<version>\s*([^<\s]+)\s*</version>""")

    /** Liest die Versionen aus dem XML-Text. */
    fun parseVersions(xml: String): List<String> {
        val block = versionsBlock.find(xml)?.groupValues?.get(1) ?: return emptyList()
        return versionTag.findAll(block).map { it.groupValues[1] }.toList()
    }

    /** Adresse der Metadaten einer Koordinate im [repository]. */
    fun metadataUrl(repository: MavenRepository, coordinate: LibraryCoordinate): String =
        repository.url + coordinate.group.replace('.', '/') + "/" + coordinate.name + "/maven-metadata.xml"
}

/** Lädt die Versionslisten aus Maven-Repositories. */
interface MavenMetadataClient {
    /** Versionen von [coordinate] in [repository]; `Result.success(null)` = Artefakt dort nicht vorhanden (404). */
    suspend fun versions(
        repository: MavenRepository,
        coordinate: LibraryCoordinate,
        forceRefresh: Boolean
    ): Result<List<String>?>
}
