# CodeForge Store – Vorlage für die GitHub-Pages-Seite

Die App (Einstellungen → Store) lädt `store.json` von
`https://scto.github.io/codeforge-store/store.json` (Konstante `StoreConfig.CATALOG_URL`).

## Einrichten
1. Neues Repository `scto/codeforge-store` anlegen (oder die URL in `StoreConfig` anpassen).
2. `index.html` und `store.json` aus diesem Ordner ins Repo-Wurzelverzeichnis legen.
3. GitHub → Settings → Pages → Branch `main`, Ordner `/ (root)`.

## Format (`schema: 1`)
- `categories[]`: `id`, `title` (String oder `{ "de": …, "en": … }`). Jede Kategorie wird in der App ein eigener Tab.
- `items[]`: `id`, `category`, `name`, `summary`, `version`, `author`, `homepage`, `downloadUrl`, `sha256` (64 Hex-Zeichen, optional).
- Nur `http(s)`-Links werden übernommen; Einträge ohne `id`, `category` oder `name` werden ignoriert.
- Ein höheres `schema` als das von der App unterstützte zeigt in der App den Hinweis zum App-Update.
- Offline zeigt die App die zuletzt geladene Fassung.

Hinweis: Die App öffnet Download und Homepage im Browser. Eine direkte Installation aus dem Store ist noch nicht eingebaut.
