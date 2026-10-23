# Task 38: KI-Assistent im Editor – Abschlussbericht

## Übersicht
Integration eines vollwertigen KI-Assistenten direkt in den Code-Editor von CodeForge Mobile mit Unterstützung für Streaming-Antworten, Multi-Provider-Anbindung (Claude/Anthropic, OpenAI-kompatibel, Google Gemini), automatischer Extraktion von Diff-Vorschlägen ("Anwenden" / "Diff ansehen") und strengen Datenschutz- und Secret-Sanitizing-Mechanismen.

---

## Getestet
- **Architektur & Komponenten**:
  - Domain-Modelle in `:core:domain:ai` (`AiChatMessage`, `AiRole`, `AiUsage`, `AiProposedChange`, `AiEvent`, `AiProviderType`, `AiChatOptions`, `AiPrivacyPolicy`, `AiContextSource`, `AiContextItem`, `AiPreparedContext`, `AiProvider`).
  - Unit-Tests `AiSanitizerTest`:
    - Filterung sensibler Dateien (`.env`, `local.properties`, `*.jks`, `*.keystore`, etc.).
    - Redaktion von Token und Secrets (OpenAI `sk-...`, Google `AIza...`, GitHub `ghp_...`, `Bearer ...`) zu `[REDACTED_SECRET]`.
    - Truncation und Token-Limits für vorbereiteten Kontext.
  - Unit-Tests `AiStreamFixturesTest`:
    - SSE-Stream-Parsing für Anthropic, OpenAI und Gemini.
    - Fehlerbehandlung (HTTP 429 Rate Limit Backoff, HTTP 5xx Server Errors).
  - Unit-Tests `SseEventParserTest`:
    - Chunk-Übergreifendes Parsen von Server-Sent Events.
  - Unit-Tests `AiRepositoryImplTest`:
    - Persistierung von Einstellungen, Privacy Policy und lokalem Chatverlauf.
    - Speicherung sensibler API-Keys ausschließlich via `SecretStorage` im Android Keystore.
    - Daily Request Rate-Limiting.
  - Unit-Tests `AiDiffApplicationTest` & `EditorAiViewModelTest`:
    - Erkennung von Diff-Blöcken und Markdown-Codeblöcken für Einzel- und Mehrdateien-Änderungen.
    - Vorschau und Anwenden von Patches auf geöffnete Dateien im Editor.
    - Konflikterkennung bei zwischenzeitlicher Änderung der Datei auf Disk.
  - Vollständige Lokalisierung:
    - Alle String-Ressourcen in `:core:resources` für `values` und `values-de`.
    - `python3 scripts/check_string_locales.py` erfolgreich (0 Fehler).
    - `python3 scripts/find_hardcoded_strings.py` für die neuen Editor-Komponenten verifiziert.

---

## Nicht getestet
- Echte Netzwerk-API-Aufrufe gegen Live-Endpunkte (Anthropic, OpenAI, Google Gemini) mit echten API-Keys im Unit-Test (gemockt via Mock-Clients bzw. Fakes).
- Visuelle Compose-UI-Interaktion auf physischem Android-Gerät (Verhalten von `AiAssistantSheet`, `AiDiffDialog`, `AiSettingsDialog` auf unterschiedlichen Bildschirmauflösungen und Tastatur-Einblendungen).

---

## Offene Punkte
- Optionale Offline-LLM-Unterstützung (z. B. via lokalem llama.cpp oder Termux-Sidecar) für vollständige Air-Gapped-Entwicklung.
- Multi-File-Batch-Apply mit interaktivem Merge-Assistenten für ungespeicherte Puffer.

---

## Sicherheitsaspekte (Security & Privacy)
- **API-Key-Sicherheit**: API-Keys werden niemals im Klartext oder in SharedPreferences gespeichert, sondern über `SecretStorage` verschlüsselt im Android Keystore abgelegt. Bei der Anzeige im Einstellungsdialog wird der Key maskiert (`••••••••`).
- **Secret-Sanitizer**: Vor jedem Senden an die KI werden alle Texte (Code, Auswahl, Fehlermeldungen, LSP-Diagnosen) durch den `AiSanitizer` geprüft. Bekannte Secret-Formate (`sk-...`, `AIza...`, `ghp_...`, `glpat-...`, Bearer-Tokens, etc.) werden durch `[REDACTED_SECRET]` ersetzt.
- **Ausschluss sensibler Dateien**: Dateien wie `.env*`, `local.properties`, `signing.properties`, `*.jks`, `*.keystore`, `*.pem`, `*.p12` werden strikt vom Kontext ausgeschlossen.
- **Transparenz**: Der Benutzer sieht vor dem Senden eine aufklappbare Box („Was wird gesendet“), die alle Kontextquellen, Dateinamen, Zeichenlängen und geschwärzte Secrets transparent auflistet.
- **Modellquellen**:
  - Anthropic: `claude-3-7-sonnet-latest`, `claude-3-5-sonnet-latest`, `claude-3-5-haiku-latest` (https://docs.anthropic.com/en/docs/about-claude/models)
  - OpenAI: `gpt-4o`, `gpt-4o-mini`, `o3-mini` (https://platform.openai.com/docs/models)
  - Google Gemini: `gemini-2.0-flash`, `gemini-2.0-flash-lite`, `gemini-1.5-pro` (https://ai.google.dev/gemini-api/docs/models/gemini)
