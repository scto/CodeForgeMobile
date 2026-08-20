// Modul: :feature:editor
package com.codeforge.feature.editor.di

import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.feature.editor.data.FileSystemRepositoryImpl
import com.codeforge.feature.editor.data.LspClientRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EditorModule {

    @Binds
    @Singleton
    abstract fun bindFileSystemRepository(
        impl: FileSystemRepositoryImpl
    ): FileSystemRepository

    @Binds
    @Singleton
    abstract fun bindLspClientRepository(
        impl: LspClientRepositoryImpl
    ): LspClientRepository
}
