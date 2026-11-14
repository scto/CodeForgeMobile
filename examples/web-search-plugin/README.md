# Websuche (DuckDuckGo) – Plugin für den KI-Agenten

Deklaratives Plugin ohne Code in der App: zwei Agent-Werkzeuge, die ein Python-Skript (nur Standardbibliothek) starten.

| Werkzeug | Eingabe | Ergebnis |
|---|---|---|
| `web_search` | Suchbegriffe, optional `… \| n` (1–8 Treffer) | Titel, Adresse, Auszug je Treffer |
| `web_fetch` | `https://…` | Titel und lesbarer Text der Seite (≈ 12.000 Zeichen) |

* **Datenschutz:** Suchanfragen gehen an DuckDuckGo, abgerufene Adressen an die jeweilige Seite. Beide Werkzeuge sind
  `"kind": "execute"` – der Agent fragt im Standardmodus vor jedem Aufruf nach und zeigt die Anfrage. Wer das nicht will,
  stellt in `plugin.json` auf `"read"` um.
* **Schutz:** `web_fetch` ruft nur `http`/`https` zu öffentlichen Adressen ab (kein localhost, keine privaten Netze, auch nicht
  über Weiterleitungen), lädt höchstens 2 MiB und liefert nur Text.
* **Grenzen:** Die HTML-Ergebnisseite von DuckDuckGo ist keine offizielle Schnittstelle; ändert sich ihr Aufbau oder verlangt
  DuckDuckGo eine Bestätigung (zu viele Anfragen), meldet das Werkzeug das.
* **Einschalten/ausschalten:** Einstellungen → Plugins. Ein abgeschaltetes Plugin steuert keine Werkzeuge bei.

Bauen: `./gradlew :examples:packageAll` (→ `examples/out/web-search-plugin/`) oder
`python3 templates/build.py --out examples/out examples/web-search-plugin`.
Test des Parsers: `python3 scripts/test_websearch_plugin.py`.
