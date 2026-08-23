---
name: codeforge-git-templates
description: CodeForgeMobile Projekt-Erstellung (Templates) und Versionsverwaltung (JGit).
---
# CodeForgeMobile Git & Templates

## Git Integration (`:feature:git`, `:core:data`)
- Nutzt reine Java-Implementierung **JGit**. Kein natives `libgit2` (es sei denn, JNI wird explizit gefordert).
- `GitRepositoryImpl` im `:core:data` Modul steuert clone, status, commit, push, pull in Coroutines (`Dispatchers.IO`).
- Clone-Fortschritt (ProgressMonitor) feuert via `Flow`.

## Template Engine (`:libs:template-engine`, `:feature:projectwizard`)
- Basiert auf **Freemarker** (`.ftl` Dateien) + einem JSON-Manifest (`manifest.json`).
- Templates werden direkt aus den App-Assets geladen (`AssetTemplateLoader`).
- Beinhaltet Platzhalter wie `${packageName}` oder `${packagePath}`. Parameter-Bedingungen (`conditionParam`) ermöglichen z. B. optionale Test-Module.
- Der Wizard iteriert über die im Descriptor festgelegten Parameter und leitet diese an die Engine weiter.
