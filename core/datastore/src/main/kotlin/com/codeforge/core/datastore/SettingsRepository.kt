// Modul: :core:datastore
package com.codeforge.core.datastore

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.DebugConfig
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.datastore.proto.FileHeaderConfig
import com.codeforge.core.datastore.proto.MigrationConfig
import com.codeforge.core.datastore.proto.TerminalConfig
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.WizardConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrale Read/Write-Schnittstelle auf das AppSettings-Proto.
 * Wird von :feature:themebuilder, :feature:settings und :feature:onboarding konsumiert.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {
    /** Alle App-Einstellungen als Flow. */
    val appSettings: Flow<AppSettings> = dataStore.data

    /** Ändert die Theme-Einstellungen. */
    suspend fun updateTheme(transform: (ThemeConfig) -> ThemeConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setTheme(transform(current.theme))
                .build()
        }
    }

    /** Ändert die Editor-Einstellungen. */
    suspend fun updateEditor(transform: (EditorConfig) -> EditorConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setEditor(transform(current.editor))
                .build()
        }
    }

    /** Ändert die Debug-Einstellungen. */
    suspend fun updateDebug(transform: (DebugConfig) -> DebugConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setDebug(transform(current.debug))
                .build()
        }
    }

    /** Ändert die Git-Einstellungen. */
    suspend fun updateGit(transform: (com.codeforge.core.datastore.proto.GitConfig) -> com.codeforge.core.datastore.proto.GitConfig) {
        dataStore.updateData { current ->
            current.toBuilder().setGit(transform(current.git)).build()
        }
    }

    /** Ändert die Dateibaum-Einstellungen. */
    suspend fun updateFileTree(transform: (com.codeforge.core.datastore.proto.FileTreeConfig) -> com.codeforge.core.datastore.proto.FileTreeConfig) {
        dataStore.updateData { current ->
            current.toBuilder().setFileTree(transform(current.fileTree)).build()
        }
    }

    /** Ändert die Terminal-Einstellungen. */
    suspend fun updateTerminal(transform: (TerminalConfig) -> TerminalConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setTerminal(transform(current.terminal))
                .build()
        }
    }

    /** Ändert die Wizard-Einstellungen. */
    suspend fun updateWizard(transform: (WizardConfig) -> WizardConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setWizard(transform(current.wizard))
                .build()
        }
    }

    /** Ändert die Datei-Kopf-Einstellungen. */
    suspend fun updateFileHeader(transform: (FileHeaderConfig) -> FileHeaderConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setFileHeader(transform(current.fileHeader))
                .build()
        }
    }

    /** Merkt, ob das Onboarding abgeschlossen ist. */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setOnboardingCompleted(completed)
                .build()
        }
    }

    /** Ob der x2c-Hinweis für das Projekt abgelehnt wurde. */
    fun isX2cDismissed(projectPath: String): Flow<Boolean> = appSettings.map { settings ->
        settings.migration.x2CDismissedProjectsList.contains(projectPath)
    }

    /** Merkt, dass der x2c-Hinweis für das Projekt abgelehnt wurde. */
    suspend fun setX2cDismissed(projectPath: String, dismissed: Boolean) {
        dataStore.updateData { current ->
            val existing = current.migration.x2CDismissedProjectsList.toMutableSet()
            if (dismissed) {
                existing.add(projectPath)
            } else {
                existing.remove(projectPath)
            }
            val newMigration = current.migration.toBuilder()
                .clearX2CDismissedProjects()
                .addAllX2CDismissedProjects(existing)
                .build()
            current.toBuilder()
                .setMigration(newMigration)
                .build()
        }
    }

    /** Ob der j2k-Hinweis für das Projekt abgelehnt wurde. */
    fun isJ2kDismissed(projectPath: String): Flow<Boolean> = appSettings.map { settings ->
        settings.migration.j2KDismissedProjectsList.contains(projectPath)
    }

    /** Merkt, dass der j2k-Hinweis für das Projekt abgelehnt wurde. */
    suspend fun setJ2kDismissed(projectPath: String, dismissed: Boolean) {
        dataStore.updateData { current ->
            val existing = current.migration.j2KDismissedProjectsList.toMutableSet()
            if (dismissed) {
                existing.add(projectPath)
            } else {
                existing.remove(projectPath)
            }
            val newMigration = current.migration.toBuilder()
                .clearJ2KDismissedProjects()
                .addAllJ2KDismissedProjects(existing)
                .build()
            current.toBuilder()
                .setMigration(newMigration)
                .build()
        }
    }
}
