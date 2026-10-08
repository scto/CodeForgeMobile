package com.codeforge.libs.template_engine

import com.codeforge.core.domain.repository.TemplateEngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TemplateEngineModule {
    @Binds
    abstract fun bindTemplateEngine(impl: TemplateEngineRepositoryImpl): TemplateEngineRepository
}
