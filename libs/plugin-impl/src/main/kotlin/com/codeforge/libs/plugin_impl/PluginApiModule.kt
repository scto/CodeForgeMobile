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

    /** API 5: freigegebene App-Einstellungen. */
    @Binds
    @Singleton
    abstract fun bindIdeSettingsAccess(impl: com.codeforge.libs.plugin_impl.ide.IdeSettingsAccessImpl): com.codeforge.core.domain.extension.ide.IdeSettingsAccess

    /** Stellt das gemeinsame Register aller Beiträge (App und Plugins) bereit. */
    companion object {
        /** API 5: Befehlsverzeichnis der IDE. */
        @dagger.Provides
        @Singleton
        fun provideIdeCommands(): com.codeforge.core.domain.extension.ide.IdeCommandRegistry =
            com.codeforge.core.domain.extension.ide.IdeCommandRegistryImpl()

        /** API 5: Ereignisbus. */
        @dagger.Provides
        @Singleton
        fun provideIdeEventBus(): com.codeforge.core.domain.extension.ide.IdeEventBus =
            com.codeforge.core.domain.extension.ide.IdeEventBusImpl()

        /** API 5: Verbindung zum Editor. */
        @dagger.Provides
        @Singleton
        fun provideIdeEditorBridge(): com.codeforge.core.domain.extension.ide.IdeEditorBridge =
            com.codeforge.core.domain.extension.ide.IdeEditorBridgeImpl()

        /** API 5: Navigationswünsche an die App. */
        @dagger.Provides
        @Singleton
        fun provideIdeNavigator(): com.codeforge.core.domain.extension.ide.IdeNavigator =
            com.codeforge.core.domain.extension.ide.IdeNavigatorImpl()

        /** API 5: Statusleiste. */
        @dagger.Provides
        @Singleton
        fun provideIdeStatusBar(): com.codeforge.core.domain.extension.ide.IdeStatusBar =
            com.codeforge.core.domain.extension.ide.IdeStatusBarImpl()

        /** Ein Register je App-Prozess. */
        @dagger.Provides
        @Singleton
        fun provideExtensionRegistry(): com.codeforge.core.domain.extension.registry.ExtensionRegistry =
            com.codeforge.core.domain.extension.registry.ExtensionRegistryImpl()
    }
}
