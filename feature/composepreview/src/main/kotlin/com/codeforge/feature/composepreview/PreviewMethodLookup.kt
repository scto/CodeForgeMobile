/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Sucht die Vorschau-Funktion in der Datei-Klasse (`…Kt`).
 *
 * `@Preview`-Funktionen sind üblicherweise `private`; `Class.methods` liefert aber nur öffentliche Methoden.
 * Deshalb zuerst die deklarierten statischen Methoden (auch private) durchsuchen und zugänglich machen.
 */
internal object PreviewMethodLookup {

    fun find(hostClass: Class<*>, functionName: String): Method? {
        val declared = hostClass.declaredMethods.firstOrNull { it.name == functionName && Modifier.isStatic(it.modifiers) }
        val method = declared ?: hostClass.methods.firstOrNull { it.name == functionName } ?: return null
        if (!Modifier.isPublic(method.modifiers)) method.isAccessible = true
        return method
    }
}
