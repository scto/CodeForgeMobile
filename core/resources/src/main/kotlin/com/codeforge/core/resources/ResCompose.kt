// Modul: :core:resources
package com.codeforge.core.resources

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** Composable-Variante von [Res.string] – reagiert auf Konfigurationsänderungen (Sprache, Dark Mode). */
@Composable
fun stringRes(@StringRes id: Int, vararg args: Any): String = stringResource(id, *args)

/** Composable-Variante von [Res.plural]. */
@Composable
fun pluralRes(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
    pluralStringResource(id, quantity, *args)

/** [UiText] im Composable auflösen. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, quantity, *args.toTypedArray())
}
