/**
 * Modul: :core:common
 * @author Thomas Schmid
 */
package com.codeforge.core.common.result

/**
 * Das Projekt nutzt durchgängig kotlin.Result<T> statt eines eigenen Wrapper-Typs
 * (konsistent mit allen bisherigen Repository-Implementierungen). Diese Datei bündelt
 * nur wiederkehrende Muster als Extension-Funktionen, statt einen konkurrierenden
 * Result-Typ einzuführen.
 */

suspend fun <T> resultOf(block: suspend () -> T): Result<T> = runCatching { block() }

inline fun <T, R> Result<T>.mapResult(transform: (T) -> R): Result<R> =
    fold(onSuccess = { Result.success(transform(it)) }, onFailure = { Result.failure(it) })

/** Kombiniert zwei Results — schlägt fehl, sobald eines der beiden fehlschlägt. */
inline fun <A, B, R> combineResults(a: Result<A>, b: Result<B>, transform: (A, B) -> R): Result<R> =
    a.fold(
        onSuccess = { valueA ->
            b.fold(
                onSuccess = { valueB -> Result.success(transform(valueA, valueB)) },
                onFailure = { Result.failure(it) }
            )
        },
        onFailure = { Result.failure(it) }
    )
