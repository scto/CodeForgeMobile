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

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationBridgeModule {
    @Binds
    @Singleton
    abstract fun bindActiveComposablePreviewBridge(
        impl: ActiveComposablePreviewBridgeImpl
    ): ActiveComposablePreviewBridge
}
