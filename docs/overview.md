# CodeForgeMobile — Termux-Integration: Überblick

Kurzer Einstiegspunkt. Details siehe `docs/sub/TERMUX-PORTING.md` (
technischer Status + TODOs) und `docs/architecture-decisions.md`
(Begründungen).

## Was ist neu

Vier Gradle-Module, vendort aus `scto/AndroidIDE` (dev-Branch, `termux/`)
und auf `com.codeforge` umgeschrieben:

| Modul | Herkunft | Zweck |
|---|---|---|
| `:libs:termux-emulator` | `com.termux.terminal` | Terminal-Emulator-Kern, JNI-PTY-Bridge (`com.codeforge.terminal.*`) |
| `:libs:termux-view` | `com.termux.view` | Terminal-Render-View |
| `:libs:termux-shared` | `com.termux.shared` | Utilities, `TermuxConstants`, Theme/Shell/Environment-Helfer |
| `:libs:termux-app` | `com.termux.app` | App-Schicht: `TermuxActivity`, `TermuxService`, Bootstrap-Installer, nativer Bootstrap-Embed-Mechanismus |

## Aktiv genutzt vs. mitgebaut

- **Aktiv:** `:libs:terminal-engine`s `TerminalSessionRepositoryImpl`
  nutzt `:libs:termux-emulator` + `:libs:termux-shared` direkt, um echte
  Termux-Shells zu starten. Dieser Pfad ersetzt die vorherige
  PRoot-gestützte Terminal-Session für `:feature:terminal`.
- **Mitgebaut, nicht verdrahtet:** `:libs:termux-app` + `:libs:termux-view`
  (volle Multi-Session-UI). Kompilieren eigenständig, hängen aktuell nicht
  im Dependency-Graph von `:app`.
- **PRoot/Multi-Distro entfernt:** SDK Manager (GUI über `codeforge-env`), Plugin Runtime und
  Onboarding laufen im Termux-Prefix, siehe `libs/terminal-engine/BOOTSTRAP.md`.

## Lizenz

GPLv3 ab Einbindung dieser Module — bewusste, vom Nutzer getroffene
Entscheidung. Siehe `docs/sub/NOTICE.md`, `docs/sub/LICENSE.termux`.

## Wichtigste offene Punkte (siehe docs/sub/TERMUX-PORTING.md für Details)

0. **Zentrale Strings:** `:core:resources` (Texte in `strings.xml`, Zugriff `Res`/`stringRes`) – migriert, nicht gebaut; siehe `docs/resources-and-strings.md`.
0b. **Modul-Pflichtdateien, `:app`-Ressourcen** (Theme, Icon, Backup-Regeln): ergänzt, nicht gebaut; siehe `docs/module-files-and-app-resources.md`.
0c. **Dokumentationsstand:** aktuelle Übersicht in `/STATUS.md`, `/CHANGELOG.md`, `/README.md`, `/README_DE.md`; Version 3.0.0.
0d. **Layout-Designer** (`:feature:layoutdesigner`): visueller XML-Layout-Editor, JVM-Logik getestet, UI ungebaut; siehe `docs/layout-designer.md`.
1. ~~Bootstrap-Installation verdrahten~~ — erledigt (Onboarding → Termux → `codeforge-env setup`); Gerätetest offen.
2. Echten `terminal-packages-codeforge`-Bootstrap-Release bauen und
   Checksummen in `libs/termux-app/build.gradle.kts` eintragen.
3. ~~SDK-Sichtbarkeit~~ — entschieden: SDK in `$PREFIX/opt/android-sdk`; offen: `targetSdk`/W^X-Frage (BOOTSTRAP.md).
