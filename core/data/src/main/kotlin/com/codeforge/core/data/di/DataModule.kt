// Modul: :core:data
package com.codeforge.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.codeforge.core.data.repository.ComposeSourceAnalyzerImpl
import com.codeforge.core.data.repository.FileSystemRepositoryImpl
import com.codeforge.core.data.repository.GitRepositoryImpl
import com.codeforge.core.data.repository.GitSettingsRepositoryImpl
import com.codeforge.core.data.repository.ProjectImportRepositoryImpl
import com.codeforge.core.data.repository.ProjectsDirectoryProviderImpl
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import com.codeforge.core.data.repository.RecentProjectsRepositoryImpl
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.domain.repository.ProjectImportRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.common.log.CfmLogger
import com.codeforge.core.common.log.FileCfmLogger
import com.codeforge.core.data.repository.LegacyProjectMigratorImpl
import com.codeforge.core.data.repository.WorkspaceRepositoryImpl
import com.codeforge.core.domain.repository.LegacyProjectMigrator
import com.codeforge.core.domain.repository.WorkspaceRepository
import java.io.File
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "codeforge_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.preferencesDataStore

    @Provides
    @Singleton
    fun provideCfmLogger(projectsDirectoryProvider: ProjectsDirectoryProvider): CfmLogger =
        FileCfmLogger(
            logDirectoryProvider = {
                File(projectsDirectoryProvider.getProjectsRoot(), ".cfm/log")
            }
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindRecentProjectsRepository(
        impl: RecentProjectsRepositoryImpl
    ): RecentProjectsRepository

    @Binds
    @Singleton
    abstract fun bindFileSystemRepository(
        impl: FileSystemRepositoryImpl
    ): FileSystemRepository

    @Binds
    @Singleton
    abstract fun bindGitRepository(
        impl: GitRepositoryImpl
    ): GitRepository

    @Binds
    @Singleton
    abstract fun bindGitSettingsRepository(
        impl: GitSettingsRepositoryImpl
    ): GitSettingsRepository

    @Binds
    @Singleton
    abstract fun bindComposeSourceAnalyzer(
        impl: ComposeSourceAnalyzerImpl
    ): ComposeSourceAnalyzer

    @Binds
    @Singleton
    abstract fun bindProjectImportRepository(
        impl: ProjectImportRepositoryImpl
    ): ProjectImportRepository

    @Binds
    @Singleton
    abstract fun bindProjectsDirectoryProvider(
        impl: ProjectsDirectoryProviderImpl
    ): ProjectsDirectoryProvider

    @Binds
    @Singleton
    abstract fun bindApkInstallerRepository(
        impl: com.codeforge.core.data.repository.ApkInstallerRepositoryImpl
    ): com.codeforge.core.domain.repository.ApkInstallerRepository

    @Binds
    @Singleton
    abstract fun bindLogcatRepository(
        impl: com.codeforge.core.data.repository.LogcatRepositoryImpl
    ): com.codeforge.core.domain.repository.LogcatRepository

    @Binds
    @Singleton
    abstract fun bindProcessExecutor(
        impl: com.codeforge.core.data.repository.DefaultProcessExecutor
    ): com.codeforge.core.data.repository.ProcessExecutor

    @Binds
    @Singleton
    abstract fun bindCodeFormatterRepository(
        impl: com.codeforge.core.data.repository.CodeFormatterRepositoryImpl
    ): com.codeforge.core.domain.repository.CodeFormatterRepository

    @Binds
    @Singleton
    abstract fun bindPluginToolRunner(
        impl: com.codeforge.core.data.repository.PluginToolRunnerImpl
    ): com.codeforge.core.domain.extension.PluginToolRunner

    @Binds
    @Singleton
    abstract fun bindSnippetRepository(
        impl: com.codeforge.core.data.repository.ExtensionAwareSnippetRepository
    ): com.codeforge.core.domain.repository.SnippetRepository

    @Binds
    @Singleton
    abstract fun bindSecretStorage(
        impl: com.codeforge.core.data.security.SecretStorageImpl
    ): com.codeforge.core.domain.security.SecretStorage

    @Binds
    @Singleton
    abstract fun bindAiRepository(
        impl: com.codeforge.core.data.repository.AiRepositoryImpl
    ): com.codeforge.core.domain.repository.AiRepository

    @Binds
    @Singleton
    abstract fun bindDependencyGraphRepository(
        impl: com.codeforge.core.data.repository.DependencyGraphRepositoryImpl
    ): com.codeforge.core.domain.repository.DependencyGraphRepository

    @Binds
    @Singleton
    abstract fun bindProjectSettingsRepository(
        impl: com.codeforge.core.data.repository.ProjectSettingsRepositoryImpl
    ): com.codeforge.core.domain.repository.ProjectSettingsRepository

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(
        impl: WorkspaceRepositoryImpl
    ): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindLegacyProjectMigrator(
        impl: LegacyProjectMigratorImpl
    ): LegacyProjectMigrator
}
