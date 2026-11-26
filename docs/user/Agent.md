# KI-Agent

Der Agent arbeitet im geöffneten Projekt mit Werkzeugen. Lesende Werkzeuge laufen sofort; Änderungen und
Ausführungen (Build, Netzwerk) gibt man im Modus „Einzeln fragen“ einzeln frei. Jede Änderung eines Zuges lässt sich
über „Rückgängig“ zurücknehmen. Mehrere Werkzeuge und das Takten der Anfragen folgen dem Vorbild von
[CodeAssist](https://github.com/tyron12233/CodeAssist) (GPL-3.0).

## Werkzeuge

| Gruppe | Werkzeuge | Freigabe |
|---|---|---|
| Dateien | `read_file`, `list_dir`, `search_text`, `create_file`, `edit_file`, `delete_path`, `move_path`, `rename_path` | Ändern: ja |
| Sprachserver | `find_symbol`, `go_to_definition`, `find_references`, `rename_symbol`, `list_quick_fixes`, `apply_quick_fix`, `organize_imports`, `format_file` | Ändern: ja |
| Projekt | `project_overview`, `project_diagnostics`, `get_diagnostics`, `list_tasks`, `run_gradle` | Build: ja |
| Abhängigkeiten | `search_dependency` (Maven Central, Google Maven), `add_dependency` (Version Catalog oder Build-Datei) | ja |
| Netz | `web_fetch`, `http_request` | ja |
| Rückfragen | `ask_user` (Antwortknöpfe oder eigene Antwort) | nein |
| Gedächtnis | `read_memory`, `write_memory` – Notizen je Projekt im App-Speicher, gehen in den System-Prompt | nein |
| Bilder | `view_image` (PNG/JPEG/GIF/WebP), `screenshot_preview` (rendert eine Compose-`@Preview`) | Vorschau: ja |
| Sonst | `todo_write`, Werkzeuge aus Plugins und MCP-Servern | je nach Werkzeug |

* `find_symbol` sucht Deklarationen ohne Sprachserver. Die übrigen Sprachserver-Werkzeuge brauchen einen laufenden
  Server für die Sprache; eine nicht im Editor geöffnete Datei meldet der Agent kurz beim Server an und wieder ab.
* `move_path`/`rename_path` verschieben nur Textdateien; Paket- und Import-Zeilen passt der Agent danach selbst an.
* Bilder gehen nur mit der jeweils nächsten Anfrage an das Modell und werden nicht im Verlauf gespeichert.

## Mehrere Schlüssel und Anfragelimit

Einstellungen → KI → „Agent: Ersatzschlüssel und Limit“ (für den aktiven Anbieter):

* **Ersatzschlüssel** (einer pro Zeile): Meldet der Anbieter für den Hauptschlüssel ein Minutenlimit (HTTP 429),
  ein erschöpftes Kontingent oder einen ungültigen Schlüssel, bevor etwas gestreamt wurde, geht dieselbe Anfrage mit
  dem nächsten Schlüssel raus. Ein Schlüssel nach 429 pausiert 60 s (oder so lange, wie der Anbieter nennt), nach
  Kontingent-Ende eine Stunde, ein abgelehnter bis zur nächsten Änderung der Schlüssel.
* **Anfragen pro Minute**: Der Agent wartet vor einer Anfrage, statt ins Limit zu laufen (Anzeige „Warte … s“).
  Leer lassen: Nach einem 429 lernt der Agent selbst ein Limit (eine Anfrage weniger als in der letzten Minute).

## OpenRouter

„Mit OpenRouter anmelden“ öffnet den Browser (OAuth mit PKCE); OpenRouter gibt einen Schlüssel des eigenen Kontos
zurück, die App trägt ihn ein. „Verbrauch abfragen“ zeigt Gratis-Anfragen heute, Budget und Ausgaben
(`GET /api/v1/key`).

## MCP

* **Anmeldung bei MCP-Servern:** Antwortet ein HTTP-Server mit 401, zeigt die Verbindungsliste „Anmeldung nötig“.
  „Anmelden“ führt die OAuth-Anmeldung nach der MCP-Spezifikation durch (Metadaten, dynamische Registrierung, PKCE)
  und speichert die Tokens im privaten App-Speicher; abgelaufene werden automatisch erneuert. Server ohne
  dynamische Registrierung: Token weiter als Header eintragen.
* **CodeForge als MCP-Server:** Verbindungen → „CodeForge als MCP-Server“. Die App bietet die Werkzeuge des Agenten
  (ohne die MCP-Werkzeuge selbst) unter `http://127.0.0.1:8765/mcp` an, nur auf dem Gerät erreichbar und nur mit dem
  Token (`Authorization: Bearer …`). „Konfiguration kopieren“ liefert eine `.mcp.json` für Claude Code, Cursor u. a.
  Dieselben Freigaben wie im Agenten gelten – Änderungen bestätigt man im Agenten-Panel; im Plan-Modus werden sie
  abgelehnt. Ressourcen: `codeforge://project/overview`, `codeforge://project/memory`.

## Geprüft / offen

* Werkzeuge, Schlüsselwechsel, Takt, MCP-Server (HTTP-Rundlauf mit Token) und das Parsen der Anmeldung sind mit
  Unit-Tests geprüft.
* **Nicht auf einem Gerät** ausprobiert: Sprachserver-Werkzeuge, Compose-Vorschau als Bild, OpenRouter- und
  MCP-Anmeldung im Browser, externe Agenten am eigenen MCP-Server.
