# Nicht-interaktive Shells & `.bashrc` Umgebungs-Exports

## Ursachenanalyse

Beim Vergleich von `bashrc.txt` und `env.txt` fällt folgendes Verhalten auf:

In der Standard-Debian/Ubuntu-`.bashrc` befindet sich in Zeile 6 folgende Prüfung für interaktive Shells:

```bash
# If not running interactively, don't do anything
[ -z "$PS1" ] && return
```

### Problem
Wenn ein Befehl nicht-interaktiv im PRoot-Container ausgeführt wird (wie z. B. `bash -c "env"` oder Hintergrundaufrufe vom `sdkmanager`), ist die Umgebungsvariable `$PS1` **leer** (`""`).

Daher beendet `.bashrc` die Ausführung sofort in Zeile 6. Wenn Umgebungsvariablen wie `ANDROID_HOME`, `JAVA_HOME`, `PATH` usw. am Ende von `.bashrc` angehängt werden, werden sie von nicht-interaktiven Shells **nicht mehr erreicht**. Die `env.txt` enthielt deshalb keine dieser Variablen.

---

## Behebung

1. **Top-Prepend in `.bashrc`**:
   In [`CommandlineSdkRepository.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/CommandlineSdkRepository.kt) werden die Exporte für `ANDROID_HOME`, `JAVA_HOME`, `PATH`, `ANDROID_NDK_HOME`, `CMAKE_HOME` und `GRADLE_HOME` nun am **Anfang** der `.bashrc` vor dem `[ -z "$PS1" ] && return` Guard geschrieben.

2. **Explizite PRoot-Umgebungs-Injektion**:
   Sowohl `ProotCommandBuilder.defaultEnv()` als auch `CommandlineSdkRepository.rootfsShellCommand()` injizieren die Variablen zusätzlich explizit in den Shell-Aufruf (`export ANDROID_HOME=...; export JAVA_HOME=...;`).

Dadurch stehen alle SDK- und Java-Pfade sowohl in interaktiven Terminals als auch in allen nicht-interaktiven PRoot-Befehlsausführungen vollständig zur Verfügung.
