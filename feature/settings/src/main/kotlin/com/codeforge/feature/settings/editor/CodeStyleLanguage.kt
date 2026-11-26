/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.editor

import com.codeforge.core.designsystem.EditorPreviewSpec
import com.codeforge.feature.settings.EditorSettingsUiEvent
import com.codeforge.feature.settings.EditorSettingsUiState

/** Sprachen mit eigenem Profil für „Formatieren beim Speichern“. */
internal enum class CodeStyleLanguage(val label: String, val sample: String) {
    KOTLIN("Kotlin", EditorPreviewSpec.SAMPLE_KOTLIN),
    JAVA("Java", EditorPreviewSpec.SAMPLE_KOTLIN),
    XML("XML", EditorPreviewSpec.SAMPLE_XML),
    JSON("JSON", EditorPreviewSpec.SAMPLE_KOTLIN);

    fun formatOnSave(state: EditorSettingsUiState): Boolean = when (this) {
        KOTLIN -> state.formatOnSaveKotlin
        JAVA -> state.formatOnSaveJava
        XML -> state.formatOnSaveXml
        JSON -> state.formatOnSaveJson
    }

    fun toggleEvent(): EditorSettingsUiEvent = when (this) {
        KOTLIN -> EditorSettingsUiEvent.FormatOnSaveKotlinToggled
        JAVA -> EditorSettingsUiEvent.FormatOnSaveJavaToggled
        XML -> EditorSettingsUiEvent.FormatOnSaveXmlToggled
        JSON -> EditorSettingsUiEvent.FormatOnSaveJsonToggled
    }
}
