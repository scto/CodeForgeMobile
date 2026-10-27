# Tool-Plugins: Formatierer und Language Server

Deklarative Plugins (`com.codeforge.plugin.<name>.formatter|lsp`, Publisher `codeforge`), erzeugt von `generate.py`.

* **Formatierer** (6): Prettier, shfmt, Ruff Format, gofmt, rustfmt, Taplo Format. Bundle: `com.codeforge.plugin.all-formatters.bundle`.
* **Language Server** (12 Plugins, 14 Server): clangd, rust-analyzer, Taplo, gopls, Ruff, neocmakelsp, Dart, Bash, TypeScript/JavaScript, Pyright, HTML/CSS/JSON, YAML. Bundle: `com.codeforge.plugin.all-lsp-servers.bundle`.
* **Nicht enthalten:** Kotlin Language Server, JDT LS und LemMinX — sie brauchen heruntergeladene Dateien (ZIP/JAR) und bleiben im Extensions-Manager der App.
* **Wichtig:** Die Plugins installieren keine Programme. Die Voraussetzung (z. B. `npm install -g prettier`, `pkg install shfmt`) steht in der Plugin-Beschreibung und muss einmalig im Terminal ausgeführt werden. Ein im Extensions-Manager installierter eingebauter Server hat Vorrang vor einem Plugin-Server.
* **Bauen:** `python3 tool-plugins/generate.py [ausgabeordner]` (gibt je ZIP die SHA-256 aus).
