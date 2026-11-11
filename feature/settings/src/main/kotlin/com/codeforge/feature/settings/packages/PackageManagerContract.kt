/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.packages

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.AptPackage
import java.util.Locale

/** Filter der Paketliste. */
enum class PackageFilter { ALL, INSTALLED, UPDATES, LARGE }

/** Zustand des Paketmanagers. */
@Immutable
data class PackageManagerState(
    val packages: List<AptPackage> = emptyList(),
    val isLoading: Boolean = false,
    val query: String = "",
    val filter: PackageFilter = PackageFilter.ALL,
    /** Laufende Aktionen: Paketname (oder [ACTION_INDEX]/[ACTION_UPGRADE]) → Fortschritt in Prozent. */
    val running: Map<String, Int> = emptyMap(),
    /** Paket, dessen Entfernen bestätigt werden soll. */
    val pendingRemove: String? = null,
) {
    /** Anzahl Pakete mit Update (für den Filter-Chip). */
    val updateCount: Int get() = packages.count { it.hasUpdate }

    companion object {
        /** Schlüssel in [running] für „Paketlisten aktualisieren“. */
        const val ACTION_INDEX = "\u0000index"
        /** Schlüssel in [running] für „Alle aktualisieren“. */
        const val ACTION_UPGRADE = "\u0000upgrade"
    }
}

/** Aktionen des Paketmanagers. */
sealed interface PackageManagerEvent {
    /** Suchtext geändert. */
    data class QueryChanged(val query: String) : PackageManagerEvent
    /** Filter gewählt. */
    data class FilterChanged(val filter: PackageFilter) : PackageManagerEvent
    /** Paket installieren oder aktualisieren. */
    data class Install(val name: String) : PackageManagerEvent
    /** Entfernen anfordern (öffnet die Bestätigung). */
    data class RemoveRequested(val name: String) : PackageManagerEvent
    /** Entfernen bestätigt. */
    data object RemoveConfirmed : PackageManagerEvent
    /** Entfernen abgebrochen. */
    data object RemoveDismissed : PackageManagerEvent
    /** Paketlisten neu laden (`apt update`) und anzeigen. */
    data object RefreshIndex : PackageManagerEvent
    /** Alle installierten Pakete aktualisieren. */
    data object UpgradeAll : PackageManagerEvent
    /** Nur die lokale Liste neu einlesen. */
    data object Reload : PackageManagerEvent
}

/** Einmalige Effekte. */
sealed interface PackageManagerEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : PackageManagerEffect
}

/** Filtert und sortiert: Suchtext (Name oder Beschreibung), dann Filter; Updates zuerst, sonst nach Name. */
fun filterPackages(packages: List<AptPackage>, query: String, filter: PackageFilter): List<AptPackage> {
    val q = query.trim().lowercase(Locale.ROOT)
    return packages.asSequence()
        .filter { q.isEmpty() || it.name.contains(q) || it.description.lowercase(Locale.ROOT).contains(q) }
        .filter {
            when (filter) {
                PackageFilter.ALL -> true
                PackageFilter.INSTALLED -> it.isInstalled
                PackageFilter.UPDATES -> it.hasUpdate
                PackageFilter.LARGE -> it.isLarge
            }
        }
        .sortedWith(compareByDescending<AptPackage> { it.hasUpdate }.thenBy { it.name })
        .toList()
}

/** Größe in KiB als lesbarer Text (`812 KiB`, `12,4 MiB`, `1,2 GiB`); 0 = unbekannt → leer. */
fun formatSizeKib(kib: Long, locale: Locale = Locale.getDefault()): String = when {
    kib <= 0L -> ""
    kib < 1024L -> "$kib KiB"
    kib < 1024L * 1024L -> String.format(locale, "%.1f MiB", kib / 1024.0)
    else -> String.format(locale, "%.1f GiB", kib / (1024.0 * 1024.0))
}
