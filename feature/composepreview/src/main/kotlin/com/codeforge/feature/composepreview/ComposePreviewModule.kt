/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.domain.repository.CompilerRuntimeProvider
import com.codeforge.core.domain.repository.ComposePreviewRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Compose-Vorschau. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ComposePreviewModule {
    /** Bindet den Renderer. */
    @Binds
    @Singleton
    abstract fun bindComposePreviewRenderer(impl: ComposePreviewRendererImpl): ComposePreviewRenderer

    /** Bindet den Laufzeit-Anbieter. */
    @Binds
    @Singleton
    abstract fun bindCompilerRuntimeProvider(impl: CompilerRuntimeProviderImpl): CompilerRuntimeProvider

    /** Bindet den Compiler. */
    @Binds
    @Singleton
    internal abstract fun bindPreviewCompiling(impl: KotlincProcessCompiler): PreviewCompiling

    /** Vorschau als Teil der Speicherfreigabe. */
    @Binds
    @dagger.multibindings.IntoSet
    internal abstract fun bindPreviewMemoryReleaser(impl: PreviewMemoryReleaser): com.codeforge.core.domain.runtime.MemoryReleaser
}
