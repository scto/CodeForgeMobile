/**
 * Modul: :libs:dependency-updater-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.dependency_updater_impl

import android.content.Context
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Hilt-Modul des Dependency-Updaters. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DependencyUpdaterModule {

    /** Bindet das Update-Repository. */
    @Binds
    @Singleton
    abstract fun bindRepository(impl: DependencyUpdateRepositoryImpl): DependencyUpdateRepository

    /** Bindet den Maven-Metadaten-Client. */
    @Binds
    @Singleton
    abstract fun bindMetadataClient(impl: HttpMavenMetadataClient): MavenMetadataClient

    companion object {
        @Provides
        @Singleton
        fun provideDismissalStore(@ApplicationContext context: Context): DismissalStore =
            FileDismissalStore(File(context.filesDir, "dependency-updates/dismissed.tsv"))
    }
}
