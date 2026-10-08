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

@Module
@InstallIn(SingletonComponent::class)
abstract class IndexingModule {
    @Binds
    @Singleton
    abstract fun bindProjectIndexer(impl: ProjectIndexerImpl): ProjectIndexer
}
