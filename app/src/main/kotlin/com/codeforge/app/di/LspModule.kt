// Modul: :app
package com.codeforge.app.di

import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.feature.settings.extensions.LspServerResolverImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul für die Language-Server-Auflösung. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LspModule {

    /** Bindet die Implementierung. */
    @Binds
    @Singleton
    abstract fun bindLspServerResolver(
        impl: LspServerResolverImpl
    ): LspServerResolver
}
