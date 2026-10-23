package com.codeforge.libs.debug_client

import com.codeforge.core.domain.repository.AndroidDebugManager
import com.codeforge.core.domain.repository.BreakpointRepository
import com.codeforge.core.domain.repository.DebugRepository
import com.codeforge.core.domain.repository.SourceToClassMapper
import com.codeforge.libs.debug_client.adb.AdbRunner
import com.codeforge.libs.debug_client.adb.AndroidDebugManagerImpl
import com.codeforge.libs.debug_client.adb.DefaultAdbRunner
import com.codeforge.libs.debug_client.mapping.SourceToClassMapperImpl
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

    @Binds
    @Singleton
    abstract fun bindSourceToClassMapper(
        impl: SourceToClassMapperImpl
    ): SourceToClassMapper

    @Binds
    @Singleton
    abstract fun bindAdbRunner(
        impl: DefaultAdbRunner
    ): AdbRunner

    @Binds
    @Singleton
    abstract fun bindAndroidDebugManager(
        impl: AndroidDebugManagerImpl
    ): AndroidDebugManager
}
