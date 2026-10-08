# Beispiel-Plugins

Zwei Referenz-Plugins für die CodeForge-Plugin-API (`:libs:plugin-api`):

- **`kotlin-lsp-plugin`** — installiert und startet [kotlin-language-server](https://github.com/fwcd/kotlin-language-server)
- **`java-lsp-plugin`** — installiert und startet den [Eclipse JDT Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls)
- **`lsp-plugin-common`** — geteilte Install/Uninstall-Logik, die beide Plugins per `implementation()` in ihr jeweiliges `classes.dex` mitbündeln (siehe Modul-KDoc dort, warum `implementation` statt `compileOnly`)

## Warum diese Module NICHT von `:app` abhängen

Plugins werden als ZIP-Archiv über `:feature:plugins` sideloaded (`PluginRepository.installFromFile()`),
nicht in die Host-APK einkompiliert. Eine `:app`-Abhängigkeit würde dieses Modell zerstören —
deshalb tauchen diese drei Module zwar in `settings.gradle.kts` auf (damit sie eigenständig
baubar sind), aber nirgends in `app/build.gradle.kts`.

## Bauen (auf einer Maschine mit vollständigem Android SDK — hier nicht getestet)

```bash
export ANDROID_HOME=/pfad/zum/android/sdk   # muss build-tools mit d8 enthalten
./gradlew :examples:kotlin-lsp-plugin:packagePlugin
./gradlew :examples:java-lsp-plugin:packagePlugin
```

Ergebnis: `build/plugin-package/<name>.zip`, installierbar über den "+"-Button in
`:feature:plugins` (SAF-Dateiauswahl).

**In der Umgebung, in der dieser Code entstand, konnte dieser Build nicht ausgeführt
werden** — kein Netzwerkzugriff zur Dependency-Auflösung, kein installiertes Android SDK.
Die Gradle-Tasks (`dexClasses`, `packagePlugin`) sind reale, valide Gradle-Kotlin-DSL,
aber unverifiziert.

## Lifecycle: Deaktivieren vs. Deinstallieren

`CodeForgePlugin` unterscheidet zwei Fälle:

| Aktion in der UI | Aufgerufene Methode(n) | Was passiert |
|---|---|---|
| Switch aus (deaktivieren) | `onUnload()` | Language-Server-Prozess wird gestoppt, bleibt aber in der Rootfs installiert |
| "Entfernen" (deinstallieren) | `onUninstall()` dann `onUnload()` | Language-Server wird zusätzlich per `rm -rf` aus der Rootfs entfernt |

Das installierte JDK wird bei keinem der beiden Fälle mitentfernt — es ist eine
eigenständig über den SDK Manager verwaltete Ressource (siehe unten), die von mehreren
Plugins gemeinsam genutzt werden kann.

## JDK-Verwaltung / Umgebung (aktualisiert 2026-10-07)

> Die frühere Beschreibung (Rootfs, `proot`, Alpine, `JdkInstaller` per Rootfs-Download) ist **überholt**: PRoot/Rootfs
> wurden entfernt. JDK, SDK, NDK und CMake installiert das Skript `codeforge-env` im Termux-Prefix
> (`/data/data/com.codeforge.app/files/usr`, SDK unter `$PREFIX/opt/android-sdk`); siehe `libs/terminal-engine/BOOTSTRAP.md`
> und `docs/architecture-decisions.md`. Language Server brauchen ein im Prefix installiertes JDK (`openjdk-17` im Bootstrap);
> Workspace-Pfad/Distro-Annahmen (`/root/project`, `alpine`) sind nicht mehr gültig und müssen beim Verdrahten der Plugins
> auf den Termux-Prefix angepasst werden.
