# Änderungen an der Build-Dokumentation

Die Datei `build_codeforge_repo.md` wurde überarbeitet, um die praktischen Erkenntnisse und Fehlerbehebungen des tatsächlichen Build-Scripts (`build_codeforge_repo.sh`) exakt widerzuspiegeln. 

Folgende wichtige Korrekturen wurden insbesondere in **Schritt 5** der Anleitung vorgenommen:

1. **RootFS Pfad-Umbennenung:** 
   Es wurde der ausdrückliche Hinweis hinzugefügt, dass das Repository `terminal-packages-codeforge` die Umbenennung des RootFS-Ordners von `com.termux` zu `com.codeforge.app` bereits selbstständig durchführt. Ein pauschales "Suchen & Ersetzen" des Strings `com.termux` im gesamten Shell-Script (`generate-bootstraps.sh`) darf **nicht** angewendet werden, da dies unweigerlich die bestehenden `cp`- und `mv`-Befehle des Scripts zerstört (was zu einem Fehler beim Kopieren eines Ordners in sich selbst führt).

2. **Korrektur des mktemp-Befehls:** 
   Die Vorgabe zur Anpassung des temporären Verzeichnisses wurde präzisiert. Es reicht aus, den String `/tmp` durch das Workspace-Verzeichnis zu ersetzen. Es dürfen **keine absoluten Template-Pfade** (z.B. mit Parameter `-p`) in den `mktemp -d` Befehl injiziert werden, da die `mktemp`-Implementierung in Termux in Kombination mit `--tmpdir` keine absoluten Pfade als Vorlage unterstützt und andernfalls abbrechen würde.
