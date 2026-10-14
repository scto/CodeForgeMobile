# NOTICE – TextMate Grammars, Configurations & Themes

Datum des Imports: 2026-10-14  
Lizenz aller importierten Assets: **MIT License**

Die Dateien in diesem Verzeichnis (`feature/editor/src/main/assets/textmate/`) wurden zur Bereitstellung von Syntax-Highlighting und Editor-Konfigurationen in CodeForgeMobile aus Open-Source-Projekten importiert:

---

## 1. Grammatiken & Sprachkonfigurationen

### Kotlin
* **Dateien**:
  * `grammars/kotlin.tmLanguage.json`
  * `languages/kotlin-language-configuration.json`
* **Ursprung**: [fwcd/vscode-kotlin](https://github.com/fwcd/vscode-kotlin) (Branch `main`)
* **Lizenz**: MIT License
* **Copyright**: (c) 2017 fwcd

### Java
* **Dateien**:
  * `grammars/java.tmLanguage.json`
  * `languages/java-language-configuration.json`
* **Ursprung**: [microsoft/vscode](https://github.com/microsoft/vscode) (`extensions/java`)
* **Lizenz**: MIT License
* **Copyright**: (c) Microsoft Corporation

### XML
* **Dateien**:
  * `grammars/xml.tmLanguage.json`
  * `languages/xml-language-configuration.json` (im Original `xml.language-configuration.json`)
* **Ursprung**: [microsoft/vscode](https://github.com/microsoft/vscode) (`extensions/xml`)
* **Lizenz**: MIT License
* **Copyright**: (c) Microsoft Corporation

### JSON
* **Dateien**:
  * `grammars/json.tmLanguage.json`
  * `languages/json-language-configuration.json`
* **Ursprung**: [microsoft/vscode](https://github.com/microsoft/vscode) (`extensions/json`)
* **Lizenz**: MIT License
* **Copyright**: (c) Microsoft Corporation

---

## 2. Editor-Themes

### Dark+ & Light+
* **Dateien**:
  * `themes/dark_plus.json` (vollständige, eigenständige Fassung: Basis-Regeln aus `dark_vs.json` kombiniert mit den Regeln aus `dark_plus.json`)
  * `themes/light_plus.json` (vollständige, eigenständige Fassung: Basis-Regeln aus `light_vs.json` kombiniert mit den Regeln aus `light_plus.json`)
* **Ursprung**: [microsoft/vscode](https://github.com/microsoft/vscode) (`extensions/theme-defaults/themes/`)
* **Lizenz**: MIT License
* **Copyright**: (c) Microsoft Corporation
* **Hinweis**: Die Zusammenführung der Basisregeln mit den Plus-Erweiterungen stellt sicher, dass sora-editor und Eclipse tm4e die Themes ohne unaufgelöste `include`-Referenzen direkt und autark laden können.
