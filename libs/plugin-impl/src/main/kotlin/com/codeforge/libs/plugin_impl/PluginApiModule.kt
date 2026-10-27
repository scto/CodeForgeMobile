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

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginApiModule {
    @Binds
    @Singleton
    abstract fun bindPluginRepository(
        impl: PluginRepositoryImpl
    ): PluginRepository

    @Binds
    @Singleton
    abstract fun bindContributionRepository(
        impl: PluginRepositoryImpl
    ): ContributionRepository

    @Binds
    @Singleton
    abstract fun bindExtensionThemeRepository(
        impl: ExtensionThemeRepositoryImpl
    ): ExtensionThemeRepository

    @Binds
    @Singleton
    abstract fun bindPluginUiRepository(impl: PluginHooks): PluginUiRepository

    @Binds
    @Singleton
    abstract fun bindPluginEventSink(impl: PluginHooks): PluginEventSink
}
