# Architecture Decisions — Termux-Integration

Dieses Dokument hält die zentralen Architekturentscheidungen der
Termux-Integration fest (siehe `docs/sub/TERMUX-PORTING.md` für den
vollständigen technischen Status und offene Punkte).

## ADR-1: Echtes, vendortes Termux statt Eigenbau-Terminal-Engine

**Entscheidung:** Der Terminal-Backend-Code wird nicht neu geschrieben,
sondern realer Termux-Quellcode (aus `scto/AndroidIDE`, dev-Branch,
`termux/`) wird 1:1 vendort und auf den Namespace `com.codeforge`
umgeschrieben.

**Kontext:** Eine ursprünglich vorgeschlagene GPLv3-freie Reimplementierung
wurde von Thomas explizit abgelehnt. Begründung: Thomas baut ohnehin einen
eigenen nativen Bootstrap-Fork (`terminal-packages-codeforge`) und hat sich
bewusst für GPLv3 als Lizenz des Gesamtprojekts entschieden — das war von
Anfang an das Ziel, nicht ein Kompromiss.

**Konsequenz:** CodeForgeMobile ist ab Einbindung dieser Module
GPLv3-lizenzpflichtig (siehe `docs/sub/NOTICE.md`, `docs/sub/LICENSE.termux`).

## ADR-2: Interface-Stabilität — `TerminalSessionRepository` bleibt unverändert

**Entscheidung:** Die Domain-Schnittstelle `TerminalSessionRepository`
(`:core:domain`) wird nicht verändert. Nur die Implementierung
(`TerminalSessionRepositoryImpl` in `:libs:terminal-engine`) wird
ausgetauscht.

**Begründung:** Minimaler Blast-Radius. `:feature:terminal`
(Contract/ViewModel/Screen) und die Hilt-Bindings (`TerminalEngineModule.kt`)
sind komplett unberührt. Die Swap-Stelle ist chirurgisch auf eine einzige
Datei begrenzt.

## ADR-3: PRoot und Multi-Distro entfallen — Termux ist die einzige Umgebung (ersetzt die frühere Fassung)

**Entscheidung:** `DistroBootstrapRepository`, `ProotBinaryInstaller`, `ProotCommandBuilder`,
`RootfsDownloader`/`-Extractor`, `DistroCatalog`, `JdkInstaller`, `CommandlineToolsInstaller` und
`CommandlineSdkRepository` sind **entfernt**. Es gibt keine Distro-Auswahl mehr
(`default_distro` ist im Proto `reserved`).

**Begründung (Thomas):** Unter Termux gibt es kein Multi-Distro. JDK 17 und das Setup-Skript
kommen als Pflichtpakete im Bootstrap. Android-spezifische Teile (SDK, NDK, CMake) werden nicht aus
Termux-Paketen gezogen, sondern aus eigenen Builds über ein Skript installiert. Ein separater
proot-/Rootfs-Downloader ist damit überflüssig.

**Neu:** `codeforge-env` (Skript) + `TermuxScriptSdkRepository` (SDK-Manager als GUI darüber),
`TermuxEnvironment`/`ShellLaunchSpec`, `TermuxEnvironmentRepository`; Onboarding startet Termux und
führt das Setup aus. Plugin-API: `buildShellCommand(...)` statt `buildRootfsCommand(distro, …)`.
Details: `libs/terminal-engine/BOOTSTRAP.md`.

## ADR-4: Zwei parallele Integrationstiefen — schlanker Pfad aktiv, volle UI optional

**Entscheidung:** Aktiv im Dependency-Graph von `:app` ist nur der
schlanke Pfad (`TerminalSessionRepositoryImpl` instanziiert
`com.codeforge.terminal.TerminalSession` direkt, ohne `TermuxService`/
`TermuxActivity`). Die vollständige Termux-App-Schicht
(`:libs:termux-app`, `:libs:termux-view` — Multi-Session-UI, Extra-Keys-
Toolbar, Sessions-Drawer) ist vendort und kompiliert eigenständig, ist aber
aktuell nicht in `:app` verdrahtet.

**Begründung:** Thomas' Anfrage war "die ganze Struktur haben, nur
einzelne Punkte anpassen müssen" — nicht zwingend sofortige Aktivierung der
vollen Termux-UI. Diese Entscheidung hält beide Optionen offen, ohne
ungenutzten Code auszuschließen.

## ADR-5: Applikations-ID-Korrektur (`com.codeforge.app`, nicht `com.codeforge`)

**Entscheidung:** `TermuxConstants.TERMUX_PACKAGE_NAME` und alle davon
abgeleiteten Pfade (`TERMUX_PREFIX_DIR_PATH` etc.) sowie der
Bootstrap-Build-Präfix sind auf `com.codeforge.app` gesetzt.

**Begründung:** Der tatsächliche `applicationId` in `app/build.gradle.kts`
ist `com.codeforge.app`, nicht `com.codeforge` wie ursprünglich (vor
Prüfung des echten Projekts) angenommen. Diese Korrektur ist kritisch für
Thomas' eigenen `terminal-packages-codeforge`-Bootstrap-Fork — die Pakete
müssen exakt mit Präfix `/data/data/com.codeforge.app/files/usr` gebaut
werden.

## Verworfene/überholte Vorarbeit

Eine frühere, eigenständige GPLv3-freie Terminal-Engine-Reimplementierung
sowie ein fiktives, nicht auf dem echten Projekt basierendes
CodeForgeMobile-Scaffold wurden beide verworfen, nachdem Thomas klargestellt
hat, dass (a) GPLv3 explizit gewollt ist und (b) ein reales
CodeForgeMobile-Projekt bereits existiert und als Grundlage dienen muss.
Beide Male hat Thomas das per direktem Feedback korrigiert; dieses Dokument
und `docs/sub/TERMUX-PORTING.md` spiegeln ausschließlich den aktuellen,
auf dem echten Projekt basierenden Stand wider.
