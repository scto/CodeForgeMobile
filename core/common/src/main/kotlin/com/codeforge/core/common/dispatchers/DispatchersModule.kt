/**
 * Modul: :core:common
 * @author Thomas Schmid
 */
package com.codeforge.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Hilt-Modul mit den Coroutine-Dispatchern. */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    /** IO-Dispatcher. */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** Default-Dispatcher. */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** Main-Dispatcher. */
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
