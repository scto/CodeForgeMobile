/**
 * Modul: :libs:indexing-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.indexing_impl

import com.codeforge.libs.indexing_api.ProjectIndexer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Indexierung. */
@Module
@InstallIn(SingletonComponent::class)
abstract class IndexingModule {
    /** Bindet den Projekt-Indexer. */
    @Binds
    @Singleton
    abstract fun bindProjectIndexer(impl: ProjectIndexerImpl): ProjectIndexer
}
