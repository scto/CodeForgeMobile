---
name: codeforge-plugins
description: CodeForgeMobile Plugin System. Verwaltung von ZIP-Erweiterungen und der PluginRegistry.
---
# CodeForgeMobile Plugins

## Wichtige Dateien
- `:libs:plugin-api/src/main/kotlin/com/codeforge/libs/plugin_api/PluginRepositoryImpl.kt`: Entpackt ZIP-Dateien und parst `plugin.json`.
- `:feature:plugins/src/main/kotlin/com/codeforge/feature/plugins/PluginsScreen.kt`: UI zum Importieren via SAF (Storage Access Framework).

## Architektur
- **Aktueller Stand**: Plugins sind ZIP-Archive mit einer `plugin.json` (ID, Name, Version, entryPointClass).
- **Dateisystem**: Plugins werden in `context.filesDir/plugins` gespeichert. Die Registrierung erfolgt in einer lokalen `registry.json` (Klasse `PluginRegistry`).
- **Einschränkung**: Echtes dynamisches Klassenladen (`DexClassLoader`) ist in der Basisstruktur vorbereitet, aber aktuell deaktiviert, bis das Sandbox-Sicherheitsmodell finalisiert ist.