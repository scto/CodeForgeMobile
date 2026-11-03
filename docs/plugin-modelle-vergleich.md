# Plugin-Modelle im Vergleich: Code-Plugin (alt) vs. Manifest-Plugin (neu)

Stand: v3.x. Beispiele: `examples/` (acht LSP-Plugins), Vorlagen: `templates/`, Werkzeug-Plugins: `tool-plugins/`.

## 1. Die zwei Ansätze

| | **Alt: reines Code-Plugin** (API v1) | **Neu: Manifest-Plugin** (API v2, `contributes`) |
|---|---|---|
| Kern | Klasse `CodeForgePlugin` startet/installiert alles selbst | `plugin.json` beschreibt Beiträge (`lspServers`, `requires`, Themes …), die App führt sie aus |
| Server-Start | Plugin ruft `lspClientRepository.start(...)` | `LspServerResolver` startet den Server bei Bedarf |
| Workspace | fest verdrahtet (`$HOME/project`) | aktives Projekt der App |
| Abhängigkeiten | eigenes `wget \| tar`, eigene Fehlerbehandlung | `requires` (pkg/npm/pip) – Installation nach Bestätigung in der App |
| Berechtigungen | v1: `shell`/`lsp` implizit erlaubt | v2: explizit in `permissions`, sonst `PluginPermissionException` |
| Verteilung | nur Sideload (`classes.dex`) | deklarativ: Store + Sideload; mit Code: nur Sideload |
| Build | Gradle + d8 + Android SDK | deklarativ: nur ZIP (`templates/build.py`) |
| Prüfbarkeit | nur zur Laufzeit | Manifest und Dateien statisch validierbar |

Die migrierten Beispiele sind **Hybride**: Manifest v2 (`contributes.lspServers`, `requires`) **plus** `entryPointClass` für das, was ein Manifest nicht kann (Download von Archiven, JDK-Prüfung, Aufräumen beim Deinstallieren).

## 2. Was sich bei der Migration geändert hat

* `lsp-plugin-common`: neue Basisklasse `LspServerPlugin` + `LspServerSpec` (Prüfung → JDK → Download → Meldung → Deinstallation). Jedes Plugin ist nur noch ~10 Zeilen Spezifikation; Installer kann zusätzlich Einzeldateien (JAR) laden, nutzt `curl`, falls `wget` fehlt, und bricht bei Fehlern in Pipes ab (`pipefail`).
* Start des Servers: vom Plugincode nach `plugin.json` verlagert. Dadurch entfällt der hart codierte Workspace (`$HOME/project`) und der Konflikt „Plugin startet Server, Editor startet ebenfalls einen“.
* Erhalten: JDK-17-Pflicht mit klarer Meldung, Installation nach `~/.local/share/<server>`, JDT-LS-Launcher-Glob und `-data`-Verzeichnis, Entfernen bei Deinstallation (JDK bleibt).
* Neu: XML (LemMinX), C/C++ (clangd), Bash, YAML, TOML (Taplo), Python (Pyright). Nur XML lädt selbst herunter; die übrigen deklarieren Abhängigkeiten über `requires`.
* Ids/Versionen: `com.example.<sprache>-lsp`, Version 2.0.0, `apiVersion` 2, Berechtigung `shell`.

## 3. Vorteile / Nachteile

**Manifest-Modell (neu)**
* + Sicherheit: deklarierte Berechtigungen, App installiert Pakete nur nach Bestätigung, Store-Allowlist (`RequirementPolicy`).
* + Weniger Code, weniger Fehlerquellen (kein eigenes Prozess-/Lifecycle-Management).
* + Kein Android SDK nötig (deklarativ); Store-fähig; statisch testbar (`ExamplePluginsValidTest`, `TemplatesValidTest`).
* + Server gehört zum aktiven Projekt; mehrere Plugins kollidieren nicht beim Start.
* − Nur, was die Beitragstypen vorsehen: kein eigener Download, keine bedingte Logik, keine Ereignisreaktion (dafür ist `entryPointClass` nötig).
* − Priorität: ein in der App installierter Server (Extensions-Manager) hat Vorrang vor Plugin-Servern; je Sprache gewinnt der erste Treffer.

**Code-Modell (alt)**
* + Volle Kontrolle: beliebige Installationslogik, Versionswahl, Konfigurationsdialoge, Ereignisse, Befehle/Menüs (ab v2 mit Berechtigungen).
* + Kann Dinge tun, die keine Beitragsart abdeckt (Archive entpacken, Server patchen, Mehrfachprozesse).
* − Build-Aufwand (Kotlin → d8 → `classes.dex`), Version-Skew gegen die Host-API, Android-14-Anforderungen (read-only Dex).
* − Nur Sideload; im Store nicht zulässig; größere Angriffsfläche.
* − Doppelte Arbeit: jeder Autor baut Download, Fehlermeldungen, Workspace-Logik neu; `lspClientRepository` ist ein einzelner Client (Start/Stop kollidiert mit der App).

## 4. Diagnose: Wofür ist das ältere (Code-)Modell besser geeignet?

Das Code-Modell lohnt sich, wenn ein Plugin **Verhalten** statt **Beschreibung** liefert:

1. **Eigene Installationslogik**: Server aus Release-Archiven (Kotlin LS, JDT LS, LemMinX), Prüfsummen, Versionsauswahl, Nacharbeit (Patchen, Symlinks). → Hybrid wie hier: Manifest für den Start, Code nur für die Installation.
2. **Reaktive Plugins**: auf Datei-/Git-Ereignisse reagieren (`file-events`, `git-events`), Befehle und Menüeinträge, Formatieren beim Speichern, Projekt-Automatisierung.
3. **Plugins mit Zustand/Konfiguration**, die sich zur Laufzeit ändern (Server-Parameter je Projekt, Feature-Schalter).
4. **Brücken zu Host-Diensten** (Shell, LSP-Client) für Spezialfälle, z. B. zweiter Server parallel oder Proxy-Server.
5. **Aufräumen**: `onUninstall` für Ressourcen außerhalb des Plugin-Ordners.

Das Manifest-Modell ist die bessere Wahl für: Themes, Icons, Sprachpakete, Grammatiken, Snippets, Formatierer, Pakete von Plugins und alle Language Server, die als `pkg`/`npm`/`pip`-Paket oder fertiges Binary vorliegen (clangd, Pyright, Bash, YAML, Taplo). Faustregel: **erst deklarativ; Code nur für das, was sich nicht beschreiben lässt – und dann als Hybrid.**

## 5. Grenzen / nicht geprüft

* Gebaut und manifest-validiert in der Sandbox; **nicht auf einem Gerät** installiert oder gestartet (DexClassLoader, Download-URLs, Serverstart).
* Die Bash-Start-Skripte (Kotlin/Java/XML) nutzen `$PREFIX/lib/jvm/java-17-openjdk` (Termux-JDK-Pfad); bei abweichender JDK-Installation muss `jdkInstallPath` bzw. der Pfad angepasst werden.
* Download-URLs: Kotlin LS 1.3.9 (fest), JDT LS „latest snapshot“ (ändert sich), LemMinX 0.28.0 (HTTP 200 geprüft).
* Gleichzeitig installierte Store-Plugins gleicher Sprache (`com.codeforge.plugin.*.lsp`) und Beispiel-Plugins konkurrieren; je Sprache gewinnt der erste Treffer.
