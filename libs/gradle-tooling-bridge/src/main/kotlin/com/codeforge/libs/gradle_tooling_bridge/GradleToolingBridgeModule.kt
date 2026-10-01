// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import com.codeforge.core.domain.repository.GradleBuildRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GradleToolingBridgeModule {
    @Binds
    @Singleton
    abstract fun bindGradleBuildRepository(
        impl: GradleToolingBridgeRepositoryImpl
    ): GradleBuildRepository
}
