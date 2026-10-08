// Modul: :core:resources
package com.codeforge.core.resources

import android.content.Context
import androidx.startup.Initializer

/** Registriert den Anwendungs-Context bei [Res] noch vor `Application.onCreate()`. */
class ResInitializer : Initializer<Res> {
    override fun create(context: Context): Res {
        Res.init(context)
        return Res
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
