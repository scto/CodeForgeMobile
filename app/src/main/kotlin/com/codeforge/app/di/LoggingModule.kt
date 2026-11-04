// Modul: :app
package com.codeforge.app.di

import android.content.Context
import android.os.Environment
import com.codeforge.core.common.log.CfmLogManager
import com.codeforge.core.common.log.CfmLogger
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Hilt-Modul für die Protokollierung. */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    /** `<Projekte>/.cfm/log` (wie `.cfm/crash`), Fallback: app-eigenes `log`-Verzeichnis. */
    @Provides
    @Singleton
    fun provideCfmLogManager(@ApplicationContext context: Context): CfmLogManager = CfmLogManager(
        primaryLogDir = File(
            Environment.getExternalStorageDirectory(),
            "${ProjectsDirectoryProvider.PROJECTS_DIR_NAME}/${CfmLogger.CFM_DIR_NAME}/${CfmLogger.LOG_DIR_NAME}"
        ),
        fallbackLogDir = context.getExternalFilesDir(CfmLogger.LOG_DIR_NAME) ?: File(context.filesDir, CfmLogger.LOG_DIR_NAME)
    )

    /** Stellt den Logger bereit. */
    @Provides
    @Singleton
    fun provideCfmLogger(manager: CfmLogManager): CfmLogger = manager
}
