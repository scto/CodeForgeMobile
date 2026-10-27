# 61 – Erweiterungssystem: Geräteprüfung (Phasen 1–7)

Stand: Unit-Tests und `:app:hiltJavaCompileDebug` liefen, **nichts wurde auf einem Gerät getestet**, `:app:assembleDebug` wurde nicht ausgeführt. Siehe `docs/extensions.md`.

## Vorbereitung
1. `./gradlew :app:assembleDebug` (Beispiel-Plugins `examples/*` ebenfalls bauen; im Sandbox-Build fehlte das JDK-17-Toolchain).
2. Test-Plugins als ZIP anlegen: je eines mit Farbschema, Icon-Paket (PNG), Sprachpaket, Grammatik, Snippets, LSP-Server, Bundle, sowie ein Code-Plugin (v2) mit Befehl/Menü/Ereignis.

## Prüfpunkte
- [ ] Sideload eines deklarativen Plugins (Plugins-Screen): erscheint mit Beiträgen/Berechtigungen/„Deklarativ“; aktivieren/deaktivieren/Deinstallieren wirkt sofort.
- [ ] Farbschema: ThemeBuilder „Aus Erweiterungen“; App-UI und Editor-Farben wechseln; nach Neustart erhalten; Deaktivieren fällt auf Standard zurück.
- [ ] Icon-Paket: Dateibaum, Editor-Tabs, Suche, Git-Änderungen; Ordner offen/geschlossen; Fallback-Icon.
- [ ] Sprachpaket: Strings wechseln ohne Neustart; fehlende Schlüssel fallen auf Standard zurück; Plurals/Benachrichtigungen bleiben unverändert (bekannt).
- [ ] Grammatik: Datei ohne eingebaute Grammatik wird eingefärbt (tm4e/sora real, bisher nur kompiliert).
- [ ] Snippets: Vorschläge je Sprache, schreibgeschützt.
- [ ] LSP-Beitrag: Server startet mit `${pluginDir}`/`${home}`-Platzhaltern.
- [ ] Bundle: Sideload zeigt Mitglieder; Store installiert fehlende Mitglieder mit.
- [ ] Code-Plugin v2 (Android 14!): `classes.dex` wird read-only geladen; Befehl in der Editor-Extension-Schaltfläche; Menüeintrag im Editor-/Dateibaum-Kontextmenü; Ereignisse (Datei geöffnet/gespeichert/geschlossen, Commit, Branch); Berechtigung fehlt → `PluginPermissionException`; Befehl-Timeout 60 s.
- [ ] Store (Netz, GitHub Pages aktiv): Kategorien `theme/icons/language/grammar/snippets/lsp/bundle` zeigen „Installieren“, Kategorie `plugin` nur „Herunterladen“; falsche Prüfsumme → Abbruch; ein ZIP mit `entryPointClass` oder `.dex` wird beim Store-Install abgelehnt.
- [ ] Regression: eingebaute Themes/Sprache, Dateibaum ohne Plugins, bestehende LSP-Erweiterungskarten.

Offen/bekannt: `ExtensionsManagerTest` (rust-analyzer) schlägt in Umgebungen mit installiertem `rust-analyzer` fehl (unabhängig von diesem Thema).
