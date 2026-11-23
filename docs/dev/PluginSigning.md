# Plugin-Signaturen

Plugins können signiert sein. Die App prüft die Signatur beim Installieren und merkt sich den Schlüssel
(„Vertrauen beim ersten Mal“). Ein Update muss mit demselben Schlüssel signiert sein; ein signiertes Plugin lässt sich
nicht durch ein unsigniertes oder fremd signiertes ersetzen. Unsignierte Plugins bleiben installierbar.

## Signieren

```bash
python3 scripts/plugin-sign.py --new-key ~/.codeforge/plugin-key.pem    # einmalig; geheim halten und sichern
python3 scripts/plugin-sign.py <plugin-ordner|plugin.zip> ~/.codeforge/plugin-key.pem
```

Gradle-Code-Plugins (`code-plugins/`) signieren beim Packen, wenn die Umgebungsvariable gesetzt ist:

```bash
CF_PLUGIN_SIGNING_KEY=~/.codeforge/plugin-key.pem ./gradlew :code-plugins:packageAll
```

Benötigt: `python3` und `openssl` (Termux: `pkg install python openssl-tool`).

## Format

`signature.json` im Wurzelordner des Plugins:

```json
{ "algorithm": "SHA256withECDSA", "publicKey": "<X.509, Base64>", "signature": "<DER, Base64>" }
```

Signiert wird eine Liste aller Dateien außer `signature.json`, nach Pfad sortiert, je Zeile
`<sha256-hex>  <pfad>\n` (Pfade mit `/`). Jede geänderte, fehlende oder zusätzliche Datei macht die Signatur ungültig.
Prüfung: `core/domain/…/extension/PluginSignatures.kt`; Gegenprobe mit dem Skript in `PluginSignaturesTest`.

## Herkunft (`build-info.json`)

Die Paketskripte (`templates/build.py`, `tool-plugins/generate.py`, `gradle/code-plugin.gradle.kts`) legen
`build-info.json` bei: Commit, ob der Ordner geändert war (`dirty`), Datum, Werkzeug und API-Version. Der
Plugins-Bildschirm zeigt sie zusammen mit dem Fingerabdruck des Schlüssels („Signiert: AB:CD:…“).

## Grenzen

- Kein Zertifikat, keine Zertifizierungsstelle: Die Signatur belegt nur, dass Updates vom selben Schlüssel stammen
  wie die erste Installation – nicht, wer der Herausgeber ist.
- Ein verlorener Schlüssel heißt: Nutzer müssen das Plugin entfernen und neu installieren.
- Code-Plugins laufen weiterhin ohne Sandbox im App-Prozess.
