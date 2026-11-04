// Modul: :core:domain
package com.codeforge.core.domain.model

/** Startbeschreibung eines Language-Servers: Befehl, Umgebungsvariablen und Server-ID (Standard: Programmname). */
data class LspServerLaunch(
    val command: List<String>,
    val env: Map<String, String> = emptyMap(),
    val serverId: String = command.firstOrNull()?.substringAfterLast('/')?.substringBefore(' ') ?: "default"
)
