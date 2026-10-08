// Modul: :core:resources
package com.codeforge.core.resources

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Zentraler Zugriff auf alle Texte der App (`core/resources/src/main/res/values/strings.xml`).
 *
 * Nutzung außerhalb von Composables (ViewModel, Repository, UseCase):
 * ```
 * import com.codeforge.core.resources.R
 * import com.codeforge.core.resources.Res
 * Res.string(R.string.git_error_no_remote)
 * Res.string(R.string.git_branch_created, name)
 * ```
 * In Composables: [com.codeforge.core.resources.stringRes] bzw. [pluralRes].
 *
 * Aufgelöst wird gegen den **Anwendungs-Context** zum Zeitpunkt des Aufrufs, nicht beim Start –
 * ein Sprachwechsel wirkt also auf alle danach erzeugten Texte. Texte, die im State gehalten
 * werden und eine Sprachänderung überleben sollen, als [UiText] speichern.
 *
 * Initialisierung: automatisch über [ResInitializer] (androidx.startup). In JVM-Unit-Tests
 * ersetzt [Res.install] den Resolver (siehe `TestRes` in `:core:testing`).
 */
object Res {

    /** Austauschbare Auflösung; Standard = Android-Context. */
    interface Resolver {
        fun string(@StringRes id: Int, args: Array<out Any>): String
        fun plural(@PluralsRes id: Int, quantity: Int, args: Array<out Any>): String
    }

    @Volatile
    private var resolver: Resolver? = null

    /** Wird von [ResInitializer] aufgerufen. Idempotent. */
    fun init(context: Context) {
        if (resolver is ContextResolver) return
        resolver = ContextResolver(context.applicationContext)
    }

    /** Eigenen Resolver setzen (Tests, Previews). `null` = zurücksetzen. */
    fun install(newResolver: Resolver?) {
        resolver = newResolver
    }

    /** Text zu [id]; [args] ersetzen `%1$s`, `%2$d` … */
    fun string(@StringRes id: Int, vararg args: Any): String =
        resolver?.string(id, args) ?: "@string/$id"

    /** Mengenabhängiger Text (`<plurals>`). */
    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        resolver?.plural(id, quantity, args) ?: "@plurals/$id"

    private class ContextResolver(private val appContext: Context) : Resolver {
        override fun string(id: Int, args: Array<out Any>): String =
            if (args.isEmpty()) appContext.getString(id) else appContext.getString(id, *args)

        override fun plural(id: Int, quantity: Int, args: Array<out Any>): String =
            appContext.resources.getQuantityString(id, quantity, *args)
    }
}
