// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.repository.LspClientRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul des LSP-Clients. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LspClientModule {
    /** Bindet [LspClientRepositoryImpl] als `LspClientRepository`. */
    @Binds
    @Singleton
    abstract fun bindLspClientRepository(
        impl: LspClientRepositoryImpl
    ): LspClientRepository

    /** Bindet [LspRuntimeStatsProviderImpl] als `LspRuntimeStatsProvider`. */
    @Binds
    @Singleton
    abstract fun bindLspRuntimeStatsProvider(
        impl: LspRuntimeStatsProviderImpl
    ): com.codeforge.core.domain.model.LspRuntimeStatsProvider

    /** Folgeänderungen beim Umbenennen von Dateien/Ordnern (Sprachserver bzw. package/import-Ersatz). */
    @Binds
    @Singleton
    abstract fun bindRenameRefactoringService(
        impl: RenameRefactoringServiceImpl
    ): com.codeforge.core.domain.refactoring.RenameRefactoringService

    /** Sprachserver als Teil der Speicherfreigabe. */
    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindLspMemoryReleaser(impl: LspMemoryReleaser): com.codeforge.core.domain.runtime.MemoryReleaser
}
