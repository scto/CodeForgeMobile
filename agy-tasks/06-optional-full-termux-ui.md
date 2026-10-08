# Auftrag (OPTIONAL): Volle Termux-Multi-Session-UI statt schlankem `:feature:terminal`-Pfad aktivieren

## Wichtig: Nur ausführen, wenn du das wirklich willst

Dieser Auftrag ist **nicht nötig**, damit das Terminal funktioniert — der
aktuell aktive, schlanke Pfad (`TerminalSessionRepositoryImpl` →
`:libs:termux-emulator`) reicht für eine normale Shell-Session im bestehenden
`:feature:terminal`-Screen. Führe diesen Auftrag nur aus, wenn du
stattdessen/zusätzlich die vollständige Termux-App-UI willst: Multi-Session-
Drawer, Extra-Keys-Toolbar, Style-/Font-Auswahl, wie in der echten
Termux-App. Voraussetzung: Auftrag 03 (Bootstrap-Wiring) und idealerweise 04
(echter Bootstrap) sind bereits erledigt.

## Kontext

Repo: CodeForgeMobile. Arbeitsverzeichnis: Projekt-Root (`CodeForgeMobile/`).
Lies `docs/sub/TERMUX-PORTING.md`, Abschnitt "🔧 4. Optional: volle `TermuxActivity`-UI
statt `:feature:terminal`" und "🔧 5. `TermuxApplication.onCreate()`-Logik" —
das ist der vollständige Hintergrund. Lies außerdem
`docs/architecture-decisions.md`, ADR-4.

`:libs:termux-app` (enthält `TermuxActivity`, `TermuxService`,
`FileReceiverActivity`, `ReportActivity`-Referenz) und `:libs:termux-view`
sind bereits vollständig vendort, kompilieren eigenständig und haben bereits
ein korrektes `AndroidManifest.xml` (Komponenten-Namen wurden bereits
gefixt — siehe `docs/sub/TERMUX-PORTING.md`, Abschnitt "AndroidManifest.xml von
`:libs:termux-app` korrigiert"). Sie sind nur noch nicht als Dependency von
`:app` eingebunden.

## Aufgabe

1. **Dependency aufnehmen:** `implementation(project(":libs:termux-app"))` in
   `app/build.gradle.kts` ergänzen (prüfe bestehenden Stil der Dependency-
   Liste dort).

2. **Manifest-Merge sicherstellen:** Da `libs/termux-app/src/main/AndroidManifest.xml`
   bereits `TermuxActivity`, `FileReceiverActivity`, `TermuxService`, den
   `ReportActivity`-Eintrag und den `ReportActivityBroadcastReceiver` deklariert,
   sollte nach Schritt 1 automatisch per Android-Gradle-Plugin-Manifest-Merge
   alles in die finale App-Manifest übernommen werden. Prüfe nach einem Build
   das gemergte Manifest (`app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`
   nach einem `assembleDebug`-Lauf) darauf, dass keine Merge-Konflikte
   auftreten (z. B. doppelte `android:name`-Werte mit `:app`s eigenem
   Manifest).

3. **Einstiegspunkt schaffen:** Entscheide (oder biete als Konfigurationsoption
   an), wie der Nutzer zur vollen `TermuxActivity`-UI gelangt — z. B. ein
   zusätzlicher Button/Menüpunkt in `:feature:terminal`s bestehendem Screen,
   der statt (oder zusätzlich zu) der eingebetteten `TerminalSessionRepository`-
   Session einen `Intent` zu `com.codeforge.app.TermuxActivity` startet. Prüfe
   dafür den bestehenden `:feature:terminal`-Screen-Code
   (`feature/terminal/src/main/kotlin/...`) für den passenden Anknüpfungspunkt.

4. **`TermuxApplication.onCreate()`-Logik integrieren** (falls relevant — prüfe
   zuerst `libs/termux-app/src/main/java/com/codeforge/app/TermuxApplication.java`,
   ob `onCreate()` tatsächlich non-trivialen Code enthält): die dortige Logik
   manuell aus `CodeForgeApplication.onCreate()`
   (`app/src/main/kotlin/com/codeforge/app/CodeForgeApplication.kt` o. ä. —
   finde die exakte Datei im Repo) aufrufen, da `TermuxApplication` selbst
   nicht mehr als `<application android:name=...>` instanziiert wird.

5. Aktualisiere `docs/sub/TERMUX-PORTING.md`, Punkte 4 und 5, sowie
   `docs/architecture-decisions.md`, ADR-4, auf den neuen Stand (voll
   aktiviert statt "mitgebaut, nicht verdrahtet").

## Akzeptanzkriterien

- `./gradlew :app:assembleDebug` baut erfolgreich mit `:libs:termux-app` im
  Dependency-Graph.
- Gemergtes Manifest enthält `TermuxActivity`, `FileReceiverActivity`,
  `TermuxService` ohne Namenskonflikte.
- Der bestehende schlanke `:feature:terminal`-Pfad bleibt weiterhin
  funktionsfähig (diese Änderung ist additiv, kein Ersatz).
- Falls `TermuxApplication.onCreate()` echte Initialisierungslogik enthält,
  wird sie nachweislich aus `CodeForgeApplication.onCreate()` erreicht (z. B.
  per Log-Ausgabe beim App-Start verifizierbar).
