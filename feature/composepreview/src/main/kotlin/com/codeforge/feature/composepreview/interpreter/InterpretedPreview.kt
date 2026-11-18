/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import com.codeforge.feature.composepreview.PreviewResourceStubs

/** Eine vorbereitete, interpretierte Vorschau: geparste Zieldatei plus Symbolauflösung. */
internal class InterpretedPreview private constructor(private val interpreter: Interpreter, val functionName: String) {

    /** Rendert die Vorschau-Funktion in der laufenden Komposition. */
    @Composable
    fun Content(onError: (Throwable) -> Unit) {
        val composer = currentComposer
        try {
            interpreter.invokeRoot(functionName, composer)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    companion object {
        /**
         * Parst [source] und bereitet die Auflösung über [loader] (App + vorkompilierte Nachbarn) vor. Wirft
         * [UnsupportedKotlinException], wenn die Datei Sprachmittel enthält, die der Interpreter nicht kann.
         */
        fun prepare(
            source: String,
            functionName: String,
            loader: ClassLoader,
            classIndex: ClassIndex,
            resourceValues: Map<String, PreviewResourceStubs.Values>,
            compiledR: Boolean = false,
        ): InterpretedPreview {
            val file = try { KtParser(source).parseFile() } catch (e: UnsupportedKotlinException) {
                throw UnsupportedKotlinException("Zeile ${KtParser.lineOf(source, e.offset)}: ${e.message}", e.offset)
            }
            val fn = file.functions.firstOrNull { it.name == functionName }
                ?: throw UnsupportedKotlinException("Funktion '$functionName' nicht gefunden")
            if (fn.params.any { it.default == null }) throw UnsupportedKotlinException("'$functionName' hat Parameter ohne Standardwert")
            val reflect = KotlinReflect(loader, classIndex)
            return InterpretedPreview(Interpreter(file, reflect, PreviewResourceTable(resourceValues, loader), compiledR), functionName)
        }
    }
}
