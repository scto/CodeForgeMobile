// Modul: :core:domain
package com.codeforge.core.domain.model

/** Startbeschreibung eines Language-Servers: Befehl, Umgebungsvariablen und Server-ID (Standard: Programmname). */
data class LspServerLaunch(
    val command: List<String>,
    val env: Map<String, String> = emptyMap(),
    val serverId: String = command.firstOrNull()?.substringAfterLast('/')?.substringBefore(' ') ?: "default",
    /** Dateien, die den Arbeitsordner des Servers markieren (nächster Ordner aufwärts mit einer davon). */
    val rootMarkers: List<String> = emptyList(),
    /** Zusätzlicher Server neben dem Hauptserver der Sprache. */
    val alongside: Boolean = false
)
