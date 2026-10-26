# Task 56 – Compiler-Runtime: braucht die Compose-Vorschau `material-icons-extended` oder reicht `material-icons-core`?

**Ziel:** Mit Messungen entscheiden, ob das Compiler-Runtime-Bundle (Task 26) ohne `material-icons-extended-android` auskommt (nur `material-icons-core`), und die Lösung umsetzen, die die Vorschau nicht verschlechtert. Ergebnis ist eine begründete Entscheidung im Bericht, kein Bauchgefühl.

**Warum jetzt (Befund des Assistenten, selbst nachmessen):**
* Das veröffentlichte Release `compiler-runtime-2025.09.00-k2.1.21` (`scto/codeforge-tools`) hat **56 425 019 Bytes (≈ 53,8 MiB)** als ZIP; laut `manifest.json` 31 Dateien, zusammen ≈ 57 MiB entpackt. Davon ist `androidx_compose_material-material-icons-extended-android-1.7.8.jar` mit ≈ **35,7 MiB** der größte Posten (Rest ≈ 21 MiB, deckt sich mit dem Bericht von Task 26: 30 JARs, 21,80 MiB entpackt / 20,22 MiB ZIP).
* Der Build kennt den Schalter bereits: `feature/composepreview/build.gradle.kts` (`-PwithExtendedIcons`, Standard aus, nimmt dann nur `material-icons-core`); der Workflow `build-compiler-runtime.yml` hat die Eingabe `with_extended_icons` („+35.7 MiB“). Das veröffentlichte Release wurde offenbar **mit** Extended gebaut.
* Die App selbst nutzt `material-icons-extended` in fast allen Feature-Modulen (Versionen uneinheitlich: `1.7.3` fest eingetragen, in drei Modulen über den Versionskatalog). Das betrifft die **App**, nicht die Vorschau; die Vorschau führt nur **Code des Nutzerprojekts** aus.

**Gemeinsame Regeln:** Messen statt schätzen; was nicht ausgeführt wurde, steht im Bericht unter **nicht getestet**. Bericht: `docs/compiler-runtime-icons-report.md`. `agy-tasks/TASKS-STATUS.md` und `README.md` ergänzen (Commit-Hash). Das veröffentlichte Release wird **nicht** überschrieben oder gelöscht; ein neues Bundle bekommt ein eigenes Release (Tag-Schema siehe 56.4), die Umstellung der App erfolgt erst nach Zustimmung des Inhabers.

## 56.1 Messung: was liefert `core`, was `extended`?
1. Beide Artefakte der BOM-Version `2025.09.00` auflösen (`material-icons-core` und `material-icons-extended`) und die enthaltenen Icon-Klassen zählen (z. B. `unzip -l … | grep -c 'Icons/Filled/.*Kt.class'`, je Stil Filled/Outlined/Rounded/Sharp/TwoTone/AutoMirrored). Zahlen in den Bericht.
2. Gegenprobe: Liste der Icons, die in **core** enthalten sind (Add, ArrowBack, Check, Close, Delete, Edit, Home, Menu, Search, Settings … nicht aus dem Gedächtnis übernehmen, aus dem JAR auslesen).

## 56.2 Messung: Wie oft braucht ein typisches Projekt Extended?
1. Die Icons der **IDE selbst** sind kein Maß. Nimm Testprojekte, die die Vorschau tatsächlich bekommt: die Vorlagen aus `libs/template-engine` (Assets der Projektvorlagen), `examples/` im Repo und, falls vorhanden, 3 bis 5 öffentliche Compose-Beispielprojekte (Google `nowinandroid`, `compose-samples` o. Ä.; Quelle und Stand im Bericht belegen; Netzzugriff nur, wenn erlaubt).
2. Skript `scripts/icon-usage-scan.py` (Python, nur Standardbibliothek, Aufruf `python3 -I`): findet `Icons.<Stil>.<Name>` und `import androidx.compose.material.icons.*` in Kotlin-Dateien, ordnet jedes Icon anhand der Liste aus 56.1 **core** oder **extended** zu und gibt je Projekt aus: Anzahl der Icons, davon Extended, Dateien/Composables mit Extended-Bezug.
3. Ergebnis als Tabelle im Bericht. Zusätzlich: Wie viele **@Preview-Composables** mit Extended-Icon gäbe es (nur diese sind für die Vorschau relevant).

## 56.3 Messung: Verhalten der Vorschau ohne Extended
1. Auf einem Testprojekt mit mindestens einem Extended-Icon die Vorschau mit Bundle **ohne** Extended laufen lassen (Gerät, Pixel 9 Pro XL; **Inhaber führt aus**, du lieferst Anleitung und Testprojekt). Erwartung belegen: Kompilierfehler `Unresolved reference` für das Icon.
2. Prüfe, wie `ComposePreviewRendererImpl` und die Fehleranzeige damit umgehen: Ist die Meldung für den Nutzer verständlich („Icon X gehört zu material-icons-extended, nicht installiert“)? Aktuell wahrscheinlich nur der rohe Compilerfehler: festhalten.
3. Messe die Folgen von 35,7 MiB mehr: Download (WLAN/mobil), Speicher unter `filesDir/compiler-runtime`, Classpath-Größe und **Kompilierzeit** der Vorschau (Kaltstart und Folgelauf) mit und ohne Extended (3 Läufe, Median). Zahlen in den Bericht.

## 56.4 Entscheidung (anhand der Messwerte, Optionen bewerten)
* **A – nur `core` (Standard), Extended nicht anbieten.** Klein und schnell, aber Vorschau scheitert bei Extended-Icons.
* **B – `core` im Hauptbundle, Extended als getrenntes, optionales Zusatz-Release** (`compiler-runtime-icons-<bom>-k<kotlin>.zip` mit eigenem `.sha256`/`manifest.json`). Die Vorschau erkennt `Unresolved reference` auf `Icons.…` oder den Import `androidx.compose.material.icons.(filled|outlined|rounded|sharp|twotone)` und bietet **„Zusatzpaket Icons (≈ 36 MiB) laden?“** an (Dialog, Nutzer entscheidet, Fortschritt, Prüfung der Summe wie bei `CompilerRuntimeProvider`). Empfehlung, wenn 56.2 zeigt, dass Extended in vielen, aber nicht allen Projekten vorkommt.
* **C – Extended immer im Bundle** (heutiger Stand). Nur wenn die Messung zeigt, dass es nahezu jedes Projekt braucht und die Mehrkosten klein sind.
* Entscheide und begründe mit den Zahlen aus 56.2/56.3. Beschreibe die Folge für `manifest.json`, `COMPOSE_BOM_VERSION`/`KOTLIN_VERSION` in `BuildConfig` und für `CompilerRuntimeProviderTest`.

## 56.5 Umsetzung (nur die gewählte Option)
* **A:** Workflow-Standard bleibt `with_extended_icons=false`; Hinweis in der Vorschau-Fehlermeldung, wie man Extended-Icons ersetzt (Core-Alternative oder `painterResource`).
* **B:** Gradle-Task `packIconsRuntime` (nur das Extended-JAR + Manifest + ZIP + SHA-256), Workflow-Eingabe oder eigener Workflow, Provider-Erweiterung in `:feature:composepreview` (Interface in `:core:domain`, Implementierung im Feature-Modul), Dialog, Tests (Summe falsch → Abbruch ohne Teilinstallation, wie in `CompilerRuntimeProviderTest`), Classpath um das Zusatz-JAR erweitern, wenn installiert.
* **C:** nichts ändern; Zahlen und Begründung in den Bericht.
* Unabhängig von der Option: Versionsdrift prüfen und im Bericht festhalten: Die App pinnt `material-icons-extended:1.7.3`, das Runtime-Bundle enthält `1.7.8` (aus der BOM). Eine Vorschau darf nur Icons aus der gebündelten Version nutzen; melde, ob das zu Abweichungen führen kann. Vereinheitlichung der App-Abhängigkeit über den Versionskatalog ist **nicht** Teil dieses Tasks, nur Hinweis.

## 56.6 Tests und Bericht
* `scripts/icon-usage-scan.py` mit kleinem Testkorpus (Core-Icon, Extended-Icon, Import-Platzhalter, AutoMirrored).
* Bei Option B: Unit-Tests für Erkennung, Provider und Abbruch.
* Bericht mit Messtabellen (56.1 bis 56.3), Entscheidung, **getestet / nicht getestet / offene Punkte**. Der Gerätetest bleibt offen, bis der Inhaber ihn bestätigt.

## Akzeptanz
* Zahlen für Core vs. Extended (Icon-Anzahl, Größe), für Projekt-Scans und für Download/Speicher/Kompilierzeit stehen im Bericht.
* Eine begründete Entscheidung (A, B oder C) liegt vor und ist umgesetzt oder als „keine Änderung“ dokumentiert.
* Das bestehende Release wurde nicht verändert; Tests grün.

## Commit-Vorschlag
```
docs(compiler-runtime): measure material-icons-extended need and decide core vs. optional icons bundle

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Lf4ojCvX11BeECzDFXhL7X
```
