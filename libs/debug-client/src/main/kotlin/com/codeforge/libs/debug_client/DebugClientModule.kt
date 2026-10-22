package com.codeforge.libs.debug_client

import com.codeforge.core.domain.repository.BreakpointRepository
import com.codeforge.core.domain.repository.DebugRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugClientModule {

    @Binds
    @Singleton
    abstract fun bindDebugRepository(
        impl: DebugRepositoryImpl
    ): DebugRepository

    @Binds
    @Singleton
    abstract fun bindBreakpointRepository(
        impl: BreakpointRepositoryImpl
    ): BreakpointRepository
}
