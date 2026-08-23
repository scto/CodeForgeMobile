/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.core.domain.repository.ComposePreviewRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ComposePreviewModule {
    @Binds
    @Singleton
    abstract fun bindComposePreviewRenderer(
        impl: ComposePreviewRendererImpl
    ): ComposePreviewRenderer
}
