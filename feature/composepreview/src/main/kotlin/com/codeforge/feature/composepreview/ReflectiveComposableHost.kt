/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Fehler, der geworfen wird, wenn eine Composable-Funktion Parameter erfordert.
 */
class ParameterizedComposableException(val functionName: String, override val message: String) : IllegalArgumentException(message)

/**
 * Validiert die Signatur einer Composable-Funktion.
 *
 * Der Compose-Compiler transformiert parameterlose @Composable-Funktionen in Methoden mit der Signatur
 * `(androidx.compose.runtime.Composer, Int)`.
 * Funktionen mit Parametern besitzen zusätzliche Argumente vor dem Composer.
 */
internal object ReflectiveComposableValidator {

    fun validateParameterless(targetMethod: Method) {
        val types = targetMethod.parameterTypes
        val isParameterless = types.size == 2 &&
            types[0].name == "androidx.compose.runtime.Composer" &&
            (types[1] == java.lang.Integer.TYPE || types[1] == java.lang.Integer::class.java)

        if (!isParameterless) {
            throw ParameterizedComposableException(
                functionName = targetMethod.name,
                message = Res.string(R.string.composepreview_funktion_hat_parameter, targetMethod.name)
            )
        }
    }
}

/**
 * Einhängen einer per Reflection geladenen parameterlosen @Composable-Funktion.
 *
 * Fängt InvocationTargetException und andere Fehler ab, damit eine fehlerhafte Preview-Funktion
 * nicht den übergeordneten Prozess/die Host-App zum Absturz bringt.
 */
@Composable
@OptIn(InternalComposeApi::class)
internal fun ReflectiveComposableHost(targetMethod: Method, onError: (Throwable) -> Unit = {}) {
    try {
        ReflectiveComposableValidator.validateParameterless(targetMethod)
    } catch (e: ParameterizedComposableException) {
        onError(e)
        return
    }

    val composer = currentComposer
    try {
        // Konservativer $changed-Wert: erzwingt Rekomposition, vermeidet Skip-Optimierungen.
        targetMethod.invoke(null, composer, 1)
    } catch (e: InvocationTargetException) {
        val cause = e.targetException ?: e
        onError(cause)
    } catch (e: Throwable) {
        onError(e)
    }
}
