/**
 * Modul: :libs:command-palette-api
 *
 * Schnittstellen der Befehlspalette. Jeder Bereich (Editor, Git, Dateibaum, Build & Run, Agent/KI, Plugins …) liefert
 * seine Einträge über einen eigenen [PaletteProvider]; [CommandPalette] sammelt und filtert sie. Neue Bereiche melden
 * einen Anbieter per Hilt-Multibinding an (`@Provides @IntoSet fun …(): PaletteProvider`).
 */
package com.codeforge.libs.command_palette_api

/** Liefert die Einträge eines Bereichs für eine Lage. */
interface PaletteProvider {
    /** Eindeutiger Name (Protokoll, Tests). */
    val id: String

    /** Reihenfolge beim Sammeln (kleiner = früher); entscheidet bei doppelten Aktionen. */
    val order: Int get() = DEFAULT_ORDER

    /** Einträge in [context]; nur was dort funktioniert. Darf nicht blockieren. */
    fun items(context: PaletteContext): List<PaletteItem>

    companion object {
        const val DEFAULT_ORDER = 100
    }
}

/** Die Befehlspalette: sammelt die Einträge aller Anbieter und filtert sie. */
interface CommandPalette {
    /** Alle Einträge für [context] (ohne Dubletten; fehlerhafte Anbieter werden übersprungen). */
    fun items(context: PaletteContext): List<PaletteItem>

    /** Zu [query] passende Einträge, sortiert; leere Suche = alle nach Bereich. */
    fun filter(items: List<PaletteItem>, query: String): List<PaletteItem>

    /** Zustand für [context] mit leerer Suche. */
    fun open(context: PaletteContext): CommandPaletteState {
        val all = items(context)
        return CommandPaletteState(allItems = all, visibleItems = filter(all, ""))
    }

    /** Zustand mit neuem Suchtext. */
    fun query(state: CommandPaletteState, query: String): CommandPaletteState =
        state.copy(query = query, visibleItems = filter(state.allItems, query))

    companion object {
        /** Palette ohne Anbieter (Tests, Vorschauen). */
        val EMPTY: CommandPalette = object : CommandPalette {
            override fun items(context: PaletteContext): List<PaletteItem> = emptyList()
            override fun filter(items: List<PaletteItem>, query: String): List<PaletteItem> = items
        }
    }
}
