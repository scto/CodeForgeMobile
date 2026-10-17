// Modul: :app
package com.codeforge.app.di

import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.feature.settings.extensions.LspServerResolverImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LspModule {

    @Binds
    @Singleton
    abstract fun bindLspServerResolver(
        impl: LspServerResolverImpl
    ): LspServerResolver
}
