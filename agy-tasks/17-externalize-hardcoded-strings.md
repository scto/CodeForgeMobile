# Task 17 – Wiederkehrend: harte UI-Texte nach `:core:resources` auslagern

**Zweck:** Bei lokaler Entwicklung entstehen immer wieder neue Literale wie `Text("Abbrechen")`. Dieser Task findet sie, legt Schlüssel in
`core/resources/src/main/res/values/strings.xml` an und ersetzt die Literale im Code. Er ist **idempotent** und für den regelmäßigen Lauf gedacht
(z. B. vor jedem Commit/PR oder wöchentlich). Arbeitsverzeichnis = Projekt-Root `CodeForgeMobile/`.

Pflichtlektüre: `core/resources/README.md` (Konventionen), `docs/resources-and-strings.md` (Entscheidungen, Ausnahmen).

## Ablauf

1. **Funde sammeln** (nur lesend):
   `python3 scripts/find_hardcoded_strings.py --json > /tmp/hardcoded.json` (Bericht ohne `--json`).
   Keine Funde → Task beenden („nichts zu tun"), nichts ändern.
2. **Jeden Fund prüfen** – nur *sichtbare* Texte migrieren. **Nicht migrieren** (stattdessen mit Begründung in die Allowlist, Schritt 7):
   Log-Tags, Dateinamen/Pfade, Regex, JSON-Schlüssel, Shell-Kommandos, Git-Patch-Header, Commit-Nachrichten, Fehler-Erkennungsmuster,
   Annotations-/Klassennamen, Test-Quellen, `libs/code-tools`, `libs/template-engine`, `libs/termux-*` (eigene XML-Ressourcen).
3. **Schlüssel anlegen** in `core/resources/src/main/res/values/strings.xml`:
   * Name `<modul>_<kurzform>` in snake_case (`git_`, `editor_`, `settings_`, `modulemaker_` …); in ≥ 2 Modulen verwendet → `common_…`.
   * **Vor dem Anlegen suchen**, ob der Text schon existiert (`grep -n '>Text<' core/resources/src/main/res/values/strings.xml`) – vorhandenen Schlüssel wiederverwenden, nie Duplikate.
   * Argumente positional: `$name` / `${expr}` im Literal → `%1$s`, `%2$s`; literales `%` bei Argumenten als `%%`.
   * Android-Escapes: `\'`, `\"`, `\n`, `\@`; führende/endende Leerzeichen → Wert in Anführungszeichen. Kein XML-Markup im Wert.
   * Zahlen mit Singular/Plural → `<plurals>` und `pluralRes`/`Res.plural` statt „Datei(en)".
   * Neue Schlüssel an die passende Modul-Gruppe der Datei anhängen (nicht sortieren/umbauen, damit Diffs klein bleiben).
4. **Literal ersetzen** (Regeln aus dem README):
   * `@Composable`, direktes Argument von `Text/Icon/Image/...`: `stringRes(R.string.x, args)`.
   * ViewModel/Repository/UseCase/Notification: `Res.string(R.string.x, args)`; UI-State, der den Text erst später anzeigt: `UiText.of(R.string.x, args)`.
   * Enum-/Listen-Labels: `@StringRes val labelRes: Int` und im Composable `stringRes(item.labelRes)`.
   * Import `com.codeforge.core.resources.R` (+ `Res`/`stringRes`) ergänzen; Modul mit eigener `R` → `import com.codeforge.core.resources.R as CoreR`.
   * Hat das Modul keine Abhängigkeit: `implementation(project(":core:resources"))` in dessen `build.gradle.kts` (nur dort, kein `api`).
5. **Englisch (optional, wenn der Text in `values-en` bereits für das Modul gepflegt wird):** Eintrag in `values-en/strings.xml` ergänzen. Fehlende Übersetzungen sind kein Fehler (Fallback Deutsch).
6. **Verifizieren:**
   * `python3 scripts/find_hardcoded_strings.py --check` → Exit 0.
   * XML wohlgeformt: `python3 -c "import xml.dom.minidom as m;m.parse('core/resources/src/main/res/values/strings.xml')"`; keine doppelten `name="…"`:
     `grep -o 'name="[^"]*"' core/resources/src/main/res/values/strings.xml | sort | uniq -d` → leer.
   * Platzhalter: je Eintrag stimmen Anzahl/Index der `%n$s` mit den Aufrufargumenten überein.
   * `./gradlew :core:resources:assembleDebug` und `./gradlew assembleDebug` (bzw. die betroffenen Module) kompilieren; `./gradlew testDebugUnitTest` für betroffene Module (Tests mit `TestRes.install()`).
   * Optional: ungenutzte Schlüssel melden (nicht löschen, nur auflisten):
     `for k in $(grep -o 'name="[^"]*"' core/resources/src/main/res/values/strings.xml | cut -d'"' -f2); do grep -rqs "R.string.$k\b" --include=*.kt app feature core || echo "ungenutzt: $k"; done`
7. **Allowlist:** bewusst belassene Literale nur nach Prüfung in `scripts/hardcoded-strings.allowlist` aufnehmen
   (`python3 scripts/find_hardcoded_strings.py --update-allowlist`, danach jede neue Zeile prüfen und unpassende wieder entfernen).
8. **Doku:** `core/resources/README.md`/`docs/resources-and-strings.md` nur ändern, wenn sich eine Regel ändert. Eintrag in `CHANGELOG.md` unter `[Unreleased]` („N harte Texte nach :core:resources migriert"), Zahl der Schlüssel in `docs/resources-and-strings.md` aktualisieren.

## Grenzen / nicht tun

* Keine Texte umformulieren oder „verbessern"; der Wortlaut bleibt exakt, außer ein Escape ist nötig.
* Keine Schlüssel umbenennen oder löschen (Code anderer Module könnte sie verwenden).
* Keine Logik ändern; nur Literal → Ressource.
* Das Suchskript ist eine **Heuristik**: Es erkennt Literale in `Text(...)`, `text=`, `title=`, `label=`, `placeholder=`, `contentDescription=`,
  `supportingText=`, `message=`, `hint=`, `snack(...)`, `UiText.of(...)`, `Toast`. Texte an anderer Stelle (z. B. eigene Wrapper-Funktionen) findet es nicht –
  bei Verdacht zusätzlich: `grep -rn '"[A-ZÄÖÜ][a-zäöüß]' --include=*.kt feature app`.

## Akzeptanz

* `python3 scripts/find_hardcoded_strings.py --check` liefert Exit 0.
* Build und Unit-Tests der betroffenen Module grün; App zeigt keine `@string/…`-Platzhalter.
* Diff enthält nur: `strings.xml`(+`values-en`), die ersetzten Stellen, nötige Imports/`build.gradle.kts`-Zeilen, Allowlist, CHANGELOG.

## Optional: Pre-commit-Hook (lokal)

`printf '#!/bin/sh\npython3 scripts/find_hardcoded_strings.py --check || { echo "Harte UI-Texte gefunden: agy-tasks/17 ausführen"; exit 1; }\n' > .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit`

**Stand beim Anlegen:** Das Skript fand 8 Altfunde (`EditorScreen`, `BranchesTab` ×2, `GitCloneScreen`, `GitPanel`, `ModuleMakerScreen`, `EditorSettingsScreen`, `ThemeBuilderScreen`) – erster Lauf erledigt diese. Nicht gebaut/ausgeführt außer dem Suchskript.
