// Modul: :core:datastore
package com.codeforge.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer für das AppSettings-Proto (settings.proto).
 * Wird von core:datastore.di.DataStoreModule als DataStore<AppSettings> bereitgestellt.
 */
object AppSettingsSerializer : Serializer<AppSettings> {

    /**
     * Proto3-Felder defaulten auf 0/false, was für einige Editor-Toggles (Sticky Scroll,
     * Magnifier, Symbol-Pair-Autocomplete) die falsche Werkseinstellung wäre — diese sollen
     * ab dem ersten Start aktiv sein. [defaultValue] wird von DataStore exakt dann verwendet,
     * wenn noch keine Datei existiert (kein Migrationsproblem, da es sich nur um den
     * In-Memory-Startwert vor dem allerersten `updateData`-Aufruf handelt).
     */
    override val defaultValue: AppSettings = AppSettings.getDefaultInstance().toBuilder()
        .setEditor(
            EditorConfig.getDefaultInstance().toBuilder()
                .setTabSize(4)
                .setUseTreeSitter(true)
                .setFontSize(14)
                .setStickyScrollEnabled(true)
                .setMagnifierEnabled(true)
                .setSymbolPairAutocompleteEnabled(true)
                .build()
        )
        .build()

    override suspend fun readFrom(input: InputStream): AppSettings {
        try {
            return AppSettings.parseFrom(input)
        } catch (exception: Exception) {
            throw CorruptionException(Res.string(R.string.datastore_konnte_appsettings_proto_nicht_lesen), exception)
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        t.writeTo(output)
    }
}
