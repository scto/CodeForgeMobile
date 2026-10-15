# 📝 Bericht: Wiederherstellung verlorener Editor-Funktionen (Task 19)

**Stand:** 2026-10-15  
**Modul:** `:feature:editor`  
**Referenz:** `agy-tasks/19-restore-lost-editor-features.md`, Altrepo-Stand `de2caf0`

---

## 🎯 Zusammenfassung der Kandidaten

| Kandidat | Status | Begründung & Details |
|---|---|---|
| **1. `EditorSymbolBar`** | ✅ **Wiederhergestellt** | Als modernes Jetpack-Compose-Composable in `:feature:editor/ui` neu implementiert. An `EditorController.insertText` und `EditorUiEvent.InsertText` angebunden; wird bei geöffneter Tastatur (`WindowInsets.isImeVisible`) bei Textdateien über der Tastatur eingeblendet. Zweisprachig (`editor_symbol_bar`, `editor_symbol_tab`). |
| **2. `SoraEditorTextActionWindow`** | 🔄 **Ersetzt** | Die alte Fassung war ein 200-Zeilen-Subclassing-Hack gegen Sora 0.19.x. In Sora 0.23.x ist das native `EditorTextActionWindow` (Kopieren, Ausschneiden, Einfügen, Alles Auswählen) bereits intrinsisch aktiv. Ergänzend stellt `EditorToolbar` in `EditorScreen.kt` dieselben Aktionen bereit. Bewusst nicht dupliziert. |
| **3. `EditorSearchPanel`** | 🔄 **Ersetzt** | Das alte `EditorSearchPanel` aus `de2caf0` war nur ein leerer Stub. Die aktuelle `EditorSearchBar` in `:feature:editor` bietet vollwertiges Suchen & Ersetzen (Regex, Case-Sensitive, Ganzwort, Match-Counter, Ersetzen mit Regex-Gruppen `$1`). |
| **4. Altdaten & Hilfsklassen** | ✅ **Geprüft & Ersetzt** | `EditorColorDetector` → `ColorLiteralScanner` + `EditorOverlayLayer`. `ImageFilePreview` → aktiv seit Task 16 Item 1. `EditorSession` → `OpenFile` (`EditorContract.kt`). `EditorAssetRegistry` → `TextMateAssetLoader`. `SoraEditorHost` → `EditorScreen.kt`. Keine Lücken. |
| **5. Terminal-Extras** | ℹ️ **Geprüft & Vorhanden** | Extra-Keys-Leiste (`ExtraKeys*` / `SpecialButton*`) und Floating-Fenster stammten aus Nyamux. In `:libs:termux-shared` / `:libs:termux-app` existieren die originalen, stabileren Termux-Klassen `ExtraKeysView`, `ExtraKeyButton` und `TermuxTerminalExtraKeys`. |

---

## 🛠️ Technische Details der Implementierung

### 1. `EditorSymbolBar.kt`
* **Presets:** Sprachspezifische Tastenleisten für:
  * Kotlin / Java / C / C++: `{`, `}`, `(`, `)`, `;`, `=`, `->`, `=>`, `==`, `!=`, `<`, `>`, `"`, `'`, `:`, `[`, `]`, `+`, `-`, `*`, `/`, `\`, `_`, `|`, `&`, `!`, `?`, `$`, `%`
  * Python: `:`, `(`, `)`, `[`, `]`, `{`, `}`, `=`, `==`, `!=`, `"`, `'`, `#`, `+`, `-`, `*`, `/`, `%`, `>`, `<`
  * XML / HTML / SVG: `<`, `>`, `</`, `/>`, `=`, `"`, `'`, `!`, `-`, `/`, `:`, `[`, `]`, `#`
  * Bash / Shell: `$`, `{`, `}`, `(`, `)`, `[`, `]`, `|`, `>`, `<`, `&`, `;`, `"`, `'`, `=`, `!`, `#`, `\`, `/`
  * Allgemein / Fallback: `Tab`, `->`, `{`, `}`, `(`, `)`, `[`, `]`, `<`, `>`, `;`, `,`, `.`, `"`, `'`, `:`, `=`, `+`, `-`, `*`, `/`, `!`, `?`, `&`, `|`, `_`
* **Einbindung:** In `EditorScreen.kt` unterhalb von `SoraCodeEditor` positioniert. Wird dynamisch eingeblendet, wenn `WindowInsets.isImeVisible` aktiv ist und eine Textdatei bearbeitet wird.
* **Undo-fähiger Einschub:** `EditorController.insertText(text)` führt einen Batch-Edit auf dem Content-Objekt des Editors aus und positioniert den Cursor hinter den eingefügten Text.

### 2. Verifikation & Lokalisierung
* `scripts/find_hardcoded_strings.py --check` liefert Exit 0.
* `scripts/check_string_locales.py` bestätigt vollständige Synchronität (818 Strings je Sprache).
