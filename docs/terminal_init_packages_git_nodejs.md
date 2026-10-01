# Dokumentation: Automatische Paket-Installation in den Terminal Init-Skripten (`git`, `nodejs`, `npm`)

Diese Dokumentation beschreibt die Erweiterung der Terminal-Init-Skripte um die automatische Bereitstellung von **git**, **nodejs** und **npm**.

---

## 1. Übersicht & Zielsetzung

Beim Starten jeder Terminal-Sitzung innerhalb der PRoot-Rootfs werden die jeweiligen Init-Skripte ausgeführt. Um sicherzustellen, dass Entwicklungs-Tools wie Versionierung (Git) und JavaScript/TypeScript-Laufzeiten (Node.js & NPM) ohne manuelle Vorarbeit bereitstehen, wurden diese Pakete in die automatische Prüf- und Installationsroutine aufgenommen.

---

## 2. Angepasste Skripte & Paketnamen

| Skript | Distribution | Paket-Manager | Erweitertes `required_packages` |
|---|---|---|---|
| `libs/terminal-engine/src/main/assets/init.sh` | Alpine Linux | `apk` | `bash gcompat glib nano curl wget tar unzip git nodejs npm openjdk17-jdk gradle` |
| `libs/terminal-engine/src/main/assets/init-ubuntu.sh` | Ubuntu | `apt` | `bash nano curl wget tar unzip git nodejs npm openjdk-17-jdk gradle` |
| `libs/terminal-engine/src/main/assets/init-debian.sh` | Debian | `apt` | `bash nano curl wget tar unzip git nodejs npm openjdk-17-jdk gradle` |

---

## 3. Funktionsweise beim Session-Start

1. **Abfrage bereits installierter Pakete**:
   - Für Alpine (`apk info -e <package>`)
   - Für Ubuntu & Debian (`dpkg -s <package>`)
2. **Automatische Nachinstallation**:
   - Falls `git`, `nodejs` oder `npm` noch nicht installiert sind, führt das Skript automatisch ein Paket-Update (`apk update` / `apt update`) durch und installiert die fehlenden Abhängigkeiten.
3. **Ausführungsort & Berechtigungen**:
   - Die Init-Skripte werden von `UpdateManager.onUpdate()` aus den Android-Assets nach `$PREFIX/local/bin/` entpackt und mit `chmod +x` versehen.
