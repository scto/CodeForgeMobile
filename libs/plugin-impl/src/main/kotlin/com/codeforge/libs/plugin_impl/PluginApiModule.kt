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
        impl: PluginRepositoryImpl
    ): ContributionRepository

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
}
