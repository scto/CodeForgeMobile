# Auftrag: Kleine Nachbesserungen aus der Bonsai/Sora-Editor/App-Review

## Kontext

Repo: CodeForgeMobile (Android, Kotlin). Arbeitsverzeichnis: Projekt-Root
(`CodeForgeMobile/`).

Lies zuerst `docs/bonsai-sora-app-integration.md` — ein vollständiger
Architektur-Überblick über die Bonsai-Dateibaum-, Sora-Editor- und
`:app`-Workspace-Integration, der bei einer Review entstanden ist. Zwei
veraltete/falsche Code-Kommentare wurden dabei bereits direkt korrigiert
(`feature/filetree/build.gradle.kts`, `TreeSitterLanguageSupport.kt`). Dieser
Auftrag behandelt den einzigen dabei gefundenen **echten** offenen Punkt, der
über einen reinen Kommentar-Fix hinausgeht.

## Aufgabe: `CMakeLists.txt` ohne Dateiendung für TreeSitter erkennen

`TreeSitterGrammar.fromPath(path: String)` in
`feature/editor/src/main/kotlin/com/codeforge/feature/editor/treesitter/TreeSitterLanguageSupport.kt`
leitet die Sprache ausschließlich aus der Dateiendung ab
(`path.substringAfterLast('.', "")`). `CMakeLists.txt` hat aber keine
klassische Endung — wird aktuell also nicht als `CMAKE` erkannt, obwohl der
`CMAKE`-Grammar-Eintrag bereits existiert.

Lies vor der Umsetzung `feature/editor/src/main/kotlin/com/codeforge/feature/editor/EditorLanguageFactory.kt`
komplett — dort ruft `EditorLanguageFactory.create(...)` aktuell
`TreeSitterGrammar.fromPath(path)` mit nur dem Pfad auf. Du hast zwei Optionen,
wähle die, die ohne Bruch bestehender Call-Sites auskommt:

**Option A (bevorzugt, minimal invasiv):** In `EditorLanguageFactory.create(...)`
vor dem Aufruf von `TreeSitterGrammar.fromPath(path)` prüfen, ob
`path.substringAfterLast('/') == "CMakeLists.txt"`, und falls ja, direkt
`TreeSitterLanguageSupport.createLanguage(context, TreeSitterGrammar.CMAKE)`
verwenden (Kurzschluss, ohne `fromPath` zu verändern).

**Option B:** Signatur von `TreeSitterGrammar.fromPath` um einen Parameter
erweitern, der den vollen Dateinamen statt nur die Endung berücksichtigt
(z. B. `fromPath(path: String)` intern zuerst auf exakten Dateinamen prüfen,
dann auf Endung zurückfallen) — nur wählen, falls es weitere Aufrufer von
`fromPath` gibt, die ebenfalls von der Verbesserung profitieren sollten (prüfe
das per Grep über das ganze Repo, bevor du dich dafür entscheidest).

Nach der Umsetzung: Kommentar in `TreeSitterLanguageSupport.kt`, Zeile mit
`TODO(Thomas)-OFFEN: "CMakeLists.txt" hat keine klassische Dateiendung...`,
entfernen bzw. auf "✅ gelöst, siehe EditorLanguageFactory.kt" ändern.

## Akzeptanzkriterien

- Eine Datei namens exakt `CMakeLists.txt` wird beim Öffnen im Editor (sofern
  TreeSitter verfügbar, sonst via TextMate-Fallback) als CMake erkannt.
- `./gradlew :feature:editor:assembleDebug` baut erfolgreich.
- Keine bestehende Erkennung (`.kt`, `.java`, `.xml`, `.cpp`, `.c`, `.sh`,
  `.cmake`, `.toml`, `.yaml`/`.yml`, `.json`) wird durch die Änderung gebrochen.
- Der jetzt gelöste TODO-Kommentar in `TreeSitterLanguageSupport.kt` ist
  entsprechend aktualisiert, nicht einfach gelöscht (Nachvollziehbarkeit für
  spätere Leser).
