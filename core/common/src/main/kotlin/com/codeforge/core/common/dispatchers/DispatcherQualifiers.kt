/**
 * Modul: :core:common
 * @author Thomas Schmid
 */
package com.codeforge.core.common.dispatchers

import javax.inject.Qualifier

/**
 * Qualifier für per-Hilt injizierte CoroutineDispatcher, damit Tests sie durch
 * TestDispatcher ersetzen können, statt Dispatchers.IO/.Default/.Main hart im
 * Produktivcode zu referenzieren. Bisher nutzten alle Repositories im Projekt
 * Dispatchers.IO/.Default direkt — diese Qualifier sind als schrittweise Migration
 * gedacht, keine erzwungene Sofort-Umstellung aller bestehenden Klassen.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
