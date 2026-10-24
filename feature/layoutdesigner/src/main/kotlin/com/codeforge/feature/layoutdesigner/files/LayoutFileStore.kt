/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 */
package com.codeforge.feature.layoutdesigner.files

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstraktion für das Lesen und atomare Schreiben von Layout-Dateien.
 */
interface LayoutFileStore {
    suspend fun readText(path: String): String
    suspend fun writeAtomically(path: String, content: String)
}

@Singleton
class DefaultLayoutFileStore @Inject constructor() : LayoutFileStore {

    override suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) {
            throw IOException("File does not exist: $path")
        }
        file.readText()
    }

    override suspend fun writeAtomically(path: String, content: String): Unit = withContext(Dispatchers.IO) {
        val target = File(path)
        val parent = target.parentFile ?: throw IOException("Parent directory missing for $path")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Failed to create parent directory: ${parent.absolutePath}")
        }
        val tmp = File(parent, "${target.name}.tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        } finally {
            if (tmp.exists()) {
                tmp.delete()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LayoutFileStoreModule {

    @Binds
    @Singleton
    abstract fun bindLayoutFileStore(impl: DefaultLayoutFileStore): LayoutFileStore
}
