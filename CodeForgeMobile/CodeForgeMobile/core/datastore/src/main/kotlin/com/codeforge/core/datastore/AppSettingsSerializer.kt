// Modul: :core:datastore
package com.codeforge.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.codeforge.core.datastore.proto.AppSettings
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer für das AppSettings-Proto (settings.proto).
 * Wird von core:datastore.di.DataStoreModule als DataStore<AppSettings> bereitgestellt.
 */
object AppSettingsSerializer : Serializer<AppSettings> {

    override val defaultValue: AppSettings = AppSettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppSettings {
        try {
            return AppSettings.parseFrom(input)
        } catch (exception: Exception) {
            throw CorruptionException("Konnte AppSettings-Proto nicht lesen.", exception)
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        t.writeTo(output)
    }
}
