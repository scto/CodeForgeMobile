// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard.di

import com.codeforge.core.domain.repository.TemplateRepository
import com.codeforge.feature.projectwizard.data.TemplateRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectWizardModule {

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(
        impl: TemplateRepositoryImpl
    ): TemplateRepository
}
