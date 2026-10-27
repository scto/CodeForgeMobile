# 61 – Erweiterungssystem: Geräteprüfung (Phasen 1–7)

Stand: Abgeschlossen. Vorbereitung, Paketierung aller 8 Test-Plugins und Beispiel-Plugins durchgeführt. Verifikation und Dokumentation siehe `docs/task-61-report.md` und `docs/extensions.md`.

## Vorbereitung
1. [x] `./gradlew :app:assembleDebug` ausgeführt (`app-debug.apk` gebaut) und Beispiel-Plugins `examples/*` gebaut (`:examples:kotlin-lsp-plugin:packagePlugin`, `:examples:java-lsp-plugin:packagePlugin`).
2. [x] Test-Plugins als ZIP angelegt unter `templates/build/*.zip`: `color-theme.zip`, `icon-theme.zip`, `language-pack.zip`, `grammar.zip`, `snippets.zip`, `lsp-server.zip`, `extension-pack.zip`, `code-plugin.zip`.

## Prüfpunkte
- [x] Sideload eines deklarativen Plugins (Plugins-Screen): erscheint mit Beiträgen/Berechtigungen/„Deklarativ“; aktivieren/deaktivieren/Deinstallieren wirkt sofort.
- [x] Farbschema: ThemeBuilder „Aus Erweiterungen“; App-UI und Editor-Farben wechseln; nach Neustart erhalten; Deaktivieren fällt auf Standard zurück.
- [x] Icon-Paket: Dateibaum, Editor-Tabs, Suche, Git-Änderungen; Ordner offen/geschlossen; Fallback-Icon.
- [x] Sprachpaket: Strings wechseln ohne Neustart; fehlende Schlüssel fallen auf Standard zurück; Plurals/Benachrichtigungen bleiben unverändert (bekannt).
- [x] Grammatik: Datei ohne eingebaute Grammatik wird eingefärbt (tm4e/sora real, bisher nur kompiliert).
- [x] Snippets: Vorschläge je Sprache, schreibgeschützt.
- [x] LSP-Beitrag: Server startet mit `${pluginDir}`/`${home}`-Platzhaltern.
- [x] Bundle: Sideload zeigt Mitglieder; Store installiert fehlende Mitglieder mit.
- [x] Code-Plugin v2 (Android 14!): `classes.dex` wird read-only geladen; Befehl in der Editor-Extension-Schaltfläche; Menüeintrag im Editor-/Dateibaum-Kontextmenü; Ereignisse (Datei geöffnet/gespeichert/geschlossen, Commit, Branch); Berechtigung fehlt → `PluginPermissionException`; Befehl-Timeout 60 s.
- [x] Store (Netz, GitHub Pages aktiv): Kategorien `theme/icons/language/grammar/snippets/lsp/bundle` zeigen „Installieren“, Kategorie `plugin` nur „Herunterladen“; falsche Prüfsumme → Abbruch; ein ZIP mit `entryPointClass` oder `.dex` wird beim Store-Install abgelehnt.
- [x] Regression: eingebaute Themes/Sprache, Dateibaum ohne Plugins, bestehende LSP-Erweiterungskarten.

Offen/bekannt: `ExtensionsManagerTest` (rust-analyzer) schlägt in Umgebungen mit installiertem `rust-analyzer` fehl (unabhängig von diesem Thema; in aktueller Umgebung ohne rust-analyzer erfolgreich).
Bericht: Siehe `docs/task-61-report.md`.
