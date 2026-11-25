# CodeForge Store – Quellen für Katalog, Statistik und Workflows

Die App (Einstellungen → Store) lädt `store.json` und `stats.json` von `https://scto.github.io/codeforge-store/`
(`StoreConfig`). Die Marktplatz-Ansicht im Web ist die Seite **Store** der Projektseite (`docs/store.md`,
`docs/javascripts/store.js`); `index.html` im Store-Repo leitet dorthin weiter.

## Veröffentlichen

```bash
./gradlew :code-plugins:packageAll                 # Code-Plugins (optional, erscheinen als „Sideload“)
python3 store-site/publish.py <checkout-von-codeforge-store>
# danach im Store-Repo committen und pushen
```

`publish.py` baut alle deklarativen Pakete (Themes, TextMate, Icons, Grammatiken, Snippets, Sprachpakete, LSP,
Formatierer, Werkzeuge, Bundles, Projektvorlagen), nimmt die gebauten Code-Plugins dazu, schreibt `packages/*.zip`
(Spiegel) und `store.json` und kopiert `site/` (Statistik-Skripte, Workflows, Bewertungsformular) ins Store-Repo.

## Ablauf im Store-Repo

| Workflow | Auslöser | Wirkung |
|---|---|---|
| `packages.yml` | Push mit geänderten Paketen | lädt neue Versionen als `<id>-<version>.zip` ins Release `packages` (dort zählt GitHub Downloads) |
| `stats.yml` | täglich 23:17 UTC (≈ 00:17 Schweiz), jede Bewertung, nach `packages.yml` | `scripts/stats.py` → `stats.json` (Downloads aller Versionen, Bewertungen, Rezensionen) |

Einreichen: Issue-Formular `submit.yml` (Label `submission`, ZIP als Anhang). Nach Prüfung kommt das Paket unter
seiner ID als Quelle ins App-Repo (passender `*/src/`-Ordner) und wird mit `publish.py` veröffentlicht.

Bewerten: Issue-Formular `rating.yml` (Paket-ID, 1–5 Sterne, Text). Je Nutzer und Paket zählt die neueste Bewertung;
Issues mit Label `spam`/`invalid` werden ignoriert. App und Website öffnen das Formular vorausgefüllt.

## Format `store.json` (`schema: 1`, nur ergänzt – ältere Apps lesen weiter `category`)

- `categories[]`: flache Hauptkategorien (`id`, `title`) wie bisher.
- `tree[]`: Kategorienbaum, flach als Pfade (`languages`, `languages/jvm`, `languages/jvm/kotlin` …) mit `title`.
- `items[]`: `id`, `category`, `categories` (Pfade), `tags`, `kind` (`declarative`/`code`), `name`, `summary`, `version`,
  `author`, `apiVersion`, `permissions`, `homepage`, `downloadUrl` (Release), `mirrorUrl` (Pages), `sha256`, `size`,
  `published`, `updated`.
- `statsUrl`, `ratingUrl`.

Kategorien und Tags: aus dem Manifest (`categories`, `tags`, ab API 4), sonst abgeleitet (`store-site/taxonomy.py`).

## Format `stats.json`

`{"schema":1,"updated":"…","totals":{"downloads":n,"ratings":n},"items":{"<id>":{"downloads":n,"rating":4.5,"ratings":2,"stars":[0,0,0,1,1],"reviews":[…]}}}`

Tests: `python3 -m unittest discover -s store-site/site/scripts -p "test_*.py"`.
