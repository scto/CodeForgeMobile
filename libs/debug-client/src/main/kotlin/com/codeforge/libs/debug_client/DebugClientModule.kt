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

/** Hilt-Modul des Debug-Clients. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugClientModule {

    /** Bindet das Debug-Repository. */
    @Binds
    @Singleton
    abstract fun bindDebugRepository(
        impl: DebugRepositoryImpl
    ): DebugRepository

    /** Bindet das Breakpoint-Repository. */
    @Binds
    @Singleton
    abstract fun bindBreakpointRepository(
        impl: BreakpointRepositoryImpl
    ): BreakpointRepository

    /** Bindet die Zuordnung von Quelldatei zu Klasse. */
    @Binds
    @Singleton
    abstract fun bindSourceToClassMapper(
        impl: SourceToClassMapperImpl
    ): SourceToClassMapper

    /** Bindet den ADB-Runner. */
    @Binds
    @Singleton
    abstract fun bindAdbRunner(
        impl: DefaultAdbRunner
    ): AdbRunner

    /** Bindet den Android-Debug-Manager. */
    @Binds
    @Singleton
    abstract fun bindAndroidDebugManager(
        impl: AndroidDebugManagerImpl
    ): AndroidDebugManager
}
