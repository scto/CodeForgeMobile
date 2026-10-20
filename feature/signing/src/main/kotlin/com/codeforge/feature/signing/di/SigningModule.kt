// Modul: :feature:signing
package com.codeforge.feature.signing.di

import com.codeforge.core.domain.repository.SigningRepository
import com.codeforge.feature.signing.SigningRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SigningModule {

    @Binds
    @Singleton
    abstract fun bindSigningRepository(
        impl: SigningRepositoryImpl
    ): SigningRepository
}
