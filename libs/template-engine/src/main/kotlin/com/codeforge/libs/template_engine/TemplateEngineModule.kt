// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import com.codeforge.core.domain.repository.TemplateEngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TemplateEngineModule {
    @Binds
    @Singleton
    abstract fun bindTemplateEngineRepository(
        impl: TemplateEngineRepositoryImpl
    ): TemplateEngineRepository
}
