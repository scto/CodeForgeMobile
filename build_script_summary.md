# Build Script Übersicht

Das Bash-Script `build_codeforge_repo.sh` wurde basierend auf den Vorgaben erstellt. Hier sind die wichtigsten Details der Umsetzung:

- **Umgebung & Klonen:** Das Script installiert alle notwendigen Abhängigkeiten, klont das angegebene Repository und aktualisiert die `.gitignore` automatisch und idempotent.
- **Sicheres GPG-Handling:** Die GPG-Passphrase wird sicher direkt aus der `~/.bashrc` ausgelesen und über sichere Pipes an GPG übergeben, ohne dass sie irgendwo geloggt oder gespeichert wird.
- **Dynamisches Patching:** Das Script extrahiert den Fingerprint des neu generierten Schlüssels und patcht `build-package.sh` sowie `properties.sh` dynamisch, um `com.termux` durch `com.codeforge.app` zu ersetzen.
- **Bootstrap RootFS Anpassung:** Ein Injektions-Script wird in `generate-bootstraps.sh` eingebunden, welches Textdateien und Symlinks im RootFS-Dateisystem anpasst, während ELF-Binärdateien bewusst ignoriert werden, um Längen-Fehler zu vermeiden. Temporäre Dateien werden ordnungsgemäß in das Projekt-Verzeichnis statt nach `/tmp` umgeleitet.
- **Validierung:** Alle generierten Archive werden anschließend streng validiert. Der Vorgang bricht sofort ab, falls noch irgendwo in ELF-Dateien der String `com.termux` gefunden wird.
- **APT & Dokumentation:** Das Script erstellt die APT-Repositories, signiert die Release-Dateien, berechnet die Checksummen und generiert abschließend die Zusammenfassung in `docs/overview_and_summary.md`.

## Ausführung

Da der Build-Prozess für alle Architekturen sehr viel Zeit und Ressourcen in Anspruch nimmt, muss die Ausführung manuell gestartet werden:

```bash
./build_codeforge_repo.sh
```
