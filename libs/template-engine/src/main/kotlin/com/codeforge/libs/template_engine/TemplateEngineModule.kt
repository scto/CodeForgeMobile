package com.codeforge.libs.template_engine

import com.codeforge.core.domain.repository.TemplateEngineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt-Modul der Template-Engine. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TemplateEngineModule {
    /** Bindet [TemplateEngineRepositoryImpl] als [TemplateEngineRepository]. */
    @Binds
    abstract fun bindTemplateEngine(impl: TemplateEngineRepositoryImpl): TemplateEngineRepository
}
