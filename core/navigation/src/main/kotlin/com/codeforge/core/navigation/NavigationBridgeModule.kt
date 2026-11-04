/**
 * Modul: :core:navigation
 * @author Thomas Schmid
 */
package com.codeforge.core.navigation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Brücken zwischen Modulen. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationBridgeModule {
    /** Bindet die Vorschau-Brücke. */
    @Binds
    @Singleton
    abstract fun bindActiveComposablePreviewBridge(
        impl: ActiveComposablePreviewBridgeImpl
    ): ActiveComposablePreviewBridge

    /** Bindet die Brücke zum Öffnen von Dateien. */
    @Binds
    @Singleton
    abstract fun bindOpenFileRequestBridge(
        impl: OpenFileRequestBridgeImpl
    ): OpenFileRequestBridge

    /** Bindet die Datei-Synchronisierung. */
    @Binds
    @Singleton
    abstract fun bindFileSyncBridge(
        impl: FileSyncBridgeImpl
    ): FileSyncBridge

    /** Bindet die Brücke zum Starten von Tests. */
    @Binds
    @Singleton
    abstract fun bindTestRunRequestBridge(
        impl: TestRunRequestBridgeImpl
    ): TestRunRequestBridge
}
