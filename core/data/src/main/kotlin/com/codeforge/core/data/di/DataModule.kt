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

/** Hilt-Modul mit `DataStore` für Einstellungen und Logger. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {
    /** Stellt den Preferences-DataStore bereit. */
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.preferencesDataStore

    /** Stellt den [CfmLogger] bereit. */
    @Provides
    @Singleton
    fun provideCfmLogger(projectsDirectoryProvider: ProjectsDirectoryProvider): CfmLogger =
        FileCfmLogger(
            logDirectoryProvider = {
                File(projectsDirectoryProvider.getProjectsRoot(), ".cfm/log")
            }
        )
}

/** Hilt-Modul, das die Repository-Implementierungen an ihre Schnittstellen bindet. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    /** Bindet: Einstellungen der Language-Server. */
    @Binds
    @Singleton
    abstract fun bindLspSettingsRepository(
        impl: com.codeforge.core.data.repository.LspSettingsRepositoryImpl
    ): com.codeforge.core.domain.repository.LspSettingsRepository

    /** Bindet: Zuletzt geöffnete Projekte. */
    @Binds
    @Singleton
    abstract fun bindRecentProjectsRepository(
        impl: RecentProjectsRepositoryImpl
    ): RecentProjectsRepository

    /** Bindet: Dateisystem-Zugriff. */
    @Binds
    @Singleton
    abstract fun bindFileSystemRepository(
        impl: FileSystemRepositoryImpl
    ): FileSystemRepository

    /** Bindet: Git. */
    @Binds
    @Singleton
    abstract fun bindGitRepository(
        impl: GitRepositoryImpl
    ): GitRepository

    /** Bindet: Git-Einstellungen. */
    @Binds
    @Singleton
    abstract fun bindGitSettingsRepository(
        impl: GitSettingsRepositoryImpl
    ): GitSettingsRepository

    /** Bindet: Compose-Quelltext-Analyse. */
    @Binds
    @Singleton
    abstract fun bindComposeSourceAnalyzer(
        impl: ComposeSourceAnalyzerImpl
    ): ComposeSourceAnalyzer

    /** Bindet: Projektimport. */
    @Binds
    @Singleton
    abstract fun bindProjectImportRepository(
        impl: ProjectImportRepositoryImpl
    ): ProjectImportRepository

    /** Bindet: Projektverzeichnis. */
    @Binds
    @Singleton
    abstract fun bindProjectsDirectoryProvider(
        impl: ProjectsDirectoryProviderImpl
    ): ProjectsDirectoryProvider

    /** Bindet: APK-Installation. */
    @Binds
    @Singleton
    abstract fun bindApkInstallerRepository(
        impl: com.codeforge.core.data.repository.ApkInstallerRepositoryImpl
    ): com.codeforge.core.domain.repository.ApkInstallerRepository

    /** Bindet: Logcat. */
    @Binds
    @Singleton
    abstract fun bindLogcatRepository(
        impl: com.codeforge.core.data.repository.LogcatRepositoryImpl
    ): com.codeforge.core.domain.repository.LogcatRepository

    /** Bindet: Prozessausführung. */
    @Binds
    @Singleton
    abstract fun bindProcessExecutor(
        impl: com.codeforge.core.data.repository.DefaultProcessExecutor
    ): com.codeforge.core.data.repository.ProcessExecutor

    /** Bindet: Code-Formatierung. */
    @Binds
    @Singleton
    abstract fun bindCodeFormatterRepository(
        impl: com.codeforge.core.data.repository.CodeFormatterRepositoryImpl
    ): com.codeforge.core.domain.repository.CodeFormatterRepository

    /** Bindet: Plugin-Werkzeuge. */
    @Binds
    @Singleton
    abstract fun bindPluginToolRunner(
        impl: com.codeforge.core.data.repository.PluginToolRunnerImpl
    ): com.codeforge.core.domain.extension.PluginToolRunner

    /** Bindet: Snippets. */
    @Binds
    @Singleton
    abstract fun bindSnippetRepository(
        impl: com.codeforge.core.data.repository.ExtensionAwareSnippetRepository
    ): com.codeforge.core.domain.repository.SnippetRepository

    /** Bindet: Geheimnisspeicher. */
    @Binds
    @Singleton
    abstract fun bindSecretStorage(
        impl: com.codeforge.core.data.security.SecretStorageImpl
    ): com.codeforge.core.domain.security.SecretStorage

    /** Bindet: KI-Anbindung. */
    @Binds
    @Singleton
    abstract fun bindAiRepository(
        impl: com.codeforge.core.data.repository.AiRepositoryImpl
    ): com.codeforge.core.domain.repository.AiRepository

    /** Bindet: Abhängigkeitsgraph. */
    @Binds
    @Singleton
    abstract fun bindDependencyGraphRepository(
        impl: com.codeforge.core.data.repository.DependencyGraphRepositoryImpl
    ): com.codeforge.core.domain.repository.DependencyGraphRepository

    /** Bindet: Projekteinstellungen. */
    @Binds
    @Singleton
    abstract fun bindProjectSettingsRepository(
        impl: com.codeforge.core.data.repository.ProjectSettingsRepositoryImpl
    ): com.codeforge.core.domain.repository.ProjectSettingsRepository

    /** Bindet: Workspace. */
    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(
        impl: WorkspaceRepositoryImpl
    ): WorkspaceRepository

    /** Bindet: Migration älterer Projekte. */
    @Binds
    @Singleton
    abstract fun bindLegacyProjectMigrator(
        impl: LegacyProjectMigratorImpl
    ): LegacyProjectMigrator
}
