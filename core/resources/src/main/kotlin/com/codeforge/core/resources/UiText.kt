// Modul: :core:resources
package com.codeforge.core.resources

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * Sprachunabhängiger Text für UI-States und ViewModels: speichert nur die Resource-ID plus
 * Argumente und wird erst bei der Anzeige aufgelöst (Composable: `text.asString()`; sonst [resolve]).
 */
@Immutable
sealed interface UiText {
    /** Bereits fertiger Text (z. B. Fehlermeldung eines Systems). */
    @Immutable
    data class Plain(val value: String) : UiText

    @Immutable
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    @Immutable
    data class Plural(@PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    /** Außerhalb von Compose auflösen (Notification, Logging, Tests). */
    fun resolve(): String = when (this) {
        is Plain -> value
        is Resource -> Res.string(id, *args.toTypedArray())
        is Plural -> Res.plural(id, quantity, *args.toTypedArray())
    }

    companion object {
        fun of(value: String): UiText = Plain(value)
        fun of(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())
        fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): UiText = Plural(id, quantity, args.toList())
    }
}
