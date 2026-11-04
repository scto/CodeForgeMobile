# Beispiel-Plugins

> Kleine, buildbare Vorlagen für jede Plugin-Art: `templates/` (Anleitung: `docs/extension-authoring.md`).

Acht LSP-Referenz-Plugins im **neuen Modell** (Manifest v2 + Code für Installation), Details und Vergleich zum alten Ansatz: `docs/plugin-modelle-vergleich.md`.

| Modul | Sprache | Server | Beschaffung |
|---|---|---|---|
| `kotlin-lsp-plugin` | Kotlin | kotlin-language-server 1.3.9 | Download (Code), JDK 17 |
| `java-lsp-plugin` | Java | Eclipse JDT LS | Download (Code), JDK 17 |
| `xml-lsp-plugin` | XML | LemMinX 0.28.0 | Download (Code), JDK 17 |
| `cpp-lsp-plugin` | C/C++ | clangd | `requires`: pkg `clang` |
| `bash-lsp-plugin` | Bash/sh | bash-language-server | `requires`: nodejs + npm |
| `yaml-lsp-plugin` | YAML | yaml-language-server | `requires`: nodejs + npm |
| `toml-lsp-plugin` | TOML | Taplo | `requires`: pkg `taplo` |
| `python-lsp-plugin` | Python | Pyright | `requires`: nodejs + npm |
| `kmp-lsp-plugin` | Kotlin/Java/Swift | kmp-lsp (Rust, tree-sitter) | Download des Termux-Binaries (Release `termux-1` von `scto/kmp-lsp`, SHA-256-geprüft); keine Pakete nötig |
| `lsp-plugin-common` | – | Basisklasse `LspServerPlugin`, Installer | wird in jedes `classes.dex` gebündelt |

Start der Server: deklarativ über `contributes.lspServers` (App-Resolver, aktives Projekt als Workspace). Das Plugin stellt nur sicher, dass der Server vorhanden ist (JDK-Check, Download, Meldung) und räumt bei der Deinstallation auf.

## Bauen und Installieren

```bash
export ANDROID_HOME=/pfad/zum/android/sdk   # build-tools mit d8 + platforms/android-*/android.jar
./gradlew :examples:kotlin-lsp-plugin:packagePlugin   # ebenso java-, xml-, cpp-, bash-, yaml-, toml-, python-, kmp-lsp-plugin
```

Ergebnis je Plugin in `examples/out/<modul>/`: `<modul>.zip` (installierbar), daneben `plugin.json` und `classes.dex` (lose Kopie; `*.dex` ist git-ignoriert, das ZIP ist eingecheckt).
Installation: ZIP aufs Gerät, **Einstellungen → Plugins → „+“**, Plugin aktivieren. Fehlende Programme (`requires`) bestätigt man im Plugins-Bildschirm; die JDK-17-Plugins brauchen das JDK aus dem SDK-Manager.
Code-Plugins sind **nur per Sideload** installierbar, nicht über den Store.

## Warum diese Module NICHT von `:app` abhängen

Plugins werden als ZIP sideloaded (`PluginRepository.installFromFile()`), nicht einkompiliert. Die Module stehen in `settings.gradle.kts`, damit sie baubar sind, aber nicht in `app/build.gradle.kts`.

## Lifecycle: Deaktivieren vs. Deinstallieren

`CodeForgePlugin` unterscheidet zwei Fälle:

| Aktion in der UI | Aufgerufene Methode(n) | Was passiert |
|---|---|---|
| Switch aus (deaktivieren) | `onUnload()` | Hintergrundarbeit des Plugins endet; Server bleibt installiert, der Beitrag (`lspServers`) entfällt |
| "Entfernen" (deinstallieren) | `onUninstall()` dann `onUnload()` | Server-Verzeichnis unter `~/.local/share` wird zusätzlich entfernt (nur bei selbst installierten Servern) |

Das installierte JDK wird bei keinem der beiden Fälle mitentfernt — es ist eine
eigenständig über den SDK Manager verwaltete Ressource (siehe unten), die von mehreren
Plugins gemeinsam genutzt werden kann.

## JDK-Verwaltung / Umgebung

JDK, SDK, NDK und CMake installiert `codeforge-env` im Termux-Prefix (siehe `libs/terminal-engine/BOOTSTRAP.md`). Die JVM-basierten
Plugins (Kotlin, Java, XML) prüfen `$PREFIX/lib/jvm/java-17-openjdk/bin/java` und melden sonst, dass das JDK im SDK-Manager zu installieren ist.

## Beispiele des Plugin-Leitfadens

`examples/guide/` enthält vier Plugins anderer Arten (Farbschema, Sprachunterstützung, PT-Werkzeug, Code-Plugin), beschrieben in `docs/dev/PluginDevelopment.md`. Das Code-Plugin baut `./gradlew :examples:guide-commit-notes:packagePlugin` (Ergebnis in `examples/guide/commit-notes/build/plugin-package/`), die deklarativen baut `python3 templates/build.py examples/guide/<name>`.
