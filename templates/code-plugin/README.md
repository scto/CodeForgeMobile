# Vorlage: Code-Plugin

Kotlin-Plugin (API v2) mit Befehl „Zeilen zählen“, Dateibaum-Menüeintrag und Ereignis-Listener (Datei gespeichert).

* **Dateien:** `plugin.json` (Berechtigungen `commands`, `menus`, `file-events`), `src/.../TemplatePlugin.kt`, `build.gradle.kts`
* **Bauen:** `export ANDROID_HOME=/pfad/zum/sdk` (mit build-tools/d8), dann `./gradlew :templates:code-plugin:packagePlugin` → `templates/code-plugin/build/plugin-package/template-code-plugin.zip`
* **Installieren:** nur per Sideload (Einstellungen → Plugins → „+“). Der Store lehnt Code ab.
* **Nutzen:** Dateibaum → Datei lange drücken → „Zeilen zählen“; Editor → Erweiterungs-Schaltfläche.
* **Anpassen:** `id`, `entryPointClass` und Paketnamen ändern; nur benötigte Berechtigungen deklarieren.
