// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.repository.LspClientRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LspClientModule {
    @Binds
    @Singleton
    abstract fun bindLspClientRepository(
        impl: LspClientRepositoryImpl
    ): LspClientRepository
}
