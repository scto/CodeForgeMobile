// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul des KI-Clients: bindet [AiProvider] an die Implementierung [CompositeAiProvider]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiClientModule {

    /** Bindet [CompositeAiProvider] als [AiProvider]. */
    @Binds
    @Singleton
    abstract fun bindAiProvider(impl: CompositeAiProvider): AiProvider
}
