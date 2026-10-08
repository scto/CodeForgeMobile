/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 */
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.repository.TermuxEnvironmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxEnvironmentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TermuxEnvironmentRepository {

    override fun isBootstrapInstalled(): Boolean = TermuxEnvironment.isBootstrapInstalled()

    override suspend fun installSdkScript(): Result<Unit> = withContext(Dispatchers.IO) {
        ScriptInstaller(context).install()
    }

    override fun sdkRootPath(): String = TermuxEnvironment.sdkRoot
}
