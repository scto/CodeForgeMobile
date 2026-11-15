/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.ExtensionThemeRepository
import com.codeforge.core.domain.extension.PluginEventSink
import com.codeforge.core.domain.extension.PluginUiRepository
import com.codeforge.core.domain.repository.PluginRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Plugin-Implementierung: bindet die Repository-Interfaces aus `:core:domain` an ihre Implementierungen. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginApiModule {
    /** Bindet das Plugin-Repository. */
    @Binds
    @Singleton
    abstract fun bindPluginRepository(
        impl: PluginRepositoryImpl
    ): PluginRepository

    /** Bindet das Beitrags-Repository (Themes, Grammatiken, LSP-Server … aktiver Plugins). */
    @Binds
    @Singleton
    abstract fun bindContributionRepository(
        impl: MergedContributionRepository
    ): ContributionRepository

    /** Bindet die Nutzer-Werkzeuge (externe Sprachserver, Formatierer). */
    @Binds
    @Singleton
    abstract fun bindUserToolsRepository(
        impl: UserToolsRepositoryImpl
    ): com.codeforge.core.domain.extension.UserToolsRepository

    /** Bindet das Repository für Plugin-Farbschemata und Sprachpakete. */
    @Binds
    @Singleton
    abstract fun bindExtensionThemeRepository(
        impl: ExtensionThemeRepositoryImpl
    ): ExtensionThemeRepository

    /** Bindet die UI-Seite der Plugin-Hooks (Befehle, Menüeinträge, Meldungen). */
    @Binds
    @Singleton
    abstract fun bindPluginUiRepository(impl: PluginHooks): PluginUiRepository

    /** Bindet die Senke, die App-Ereignisse an Plugins weitergibt. */
    @Binds
    @Singleton
    abstract fun bindPluginEventSink(impl: PluginHooks): PluginEventSink

    /** Bindet die gespeicherten Plugin-Einstellungen. */
    @Binds
    @Singleton
    abstract fun bindPluginSettingsRepository(impl: PluginSettingsStore): com.codeforge.core.domain.extension.PluginSettingsRepository

    /** Bindet die Protokolle der Plugins. */
    @Binds
    @Singleton
    abstract fun bindPluginLogRepository(impl: PluginLogStore): com.codeforge.core.domain.extension.PluginLogRepository

    /** Stellt das gemeinsame Register aller Beiträge (App und Plugins) bereit. */
    companion object {
        /** Ein Register je App-Prozess. */
        @dagger.Provides
        @Singleton
        fun provideExtensionRegistry(): com.codeforge.core.domain.extension.registry.ExtensionRegistry =
            com.codeforge.core.domain.extension.registry.ExtensionRegistryImpl()
    }
}
