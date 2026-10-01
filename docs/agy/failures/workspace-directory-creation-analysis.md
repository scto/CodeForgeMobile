(# AGY Failure & Architecture Report: Workspace Directory Standards (`CodeForgeMobileProjects` vs `CodeForgeProjects`)

**Datum:** 2026-10-01  
**Komponente:** Workspace Management / `:feature:welcome`, `:feature:settings`, `:feature:git`, `CodeForgeApplication`  
**Autor:** Antigravity AI (AGY)  
**Status:** In Analyse & Behebung  

---

## 1. Problemstellung

Der Benutzer hat angefragt, warum auf dem Gerät zwei Ordnerstrukturen parallel angelegt werden/wurden:
- `CodeForgeProjects` (Legacy)
- `CodeForgeMobileProjects` (Aktueller Standard)

Erwünschtes Verhalten: Es soll **ausschließlich** `CodeForgeMobileProjects` im Speicher (`/storage/emulated/0/CodeForgeMobileProjects`) verwendet und erstellt werden.

---

## 2. Ursachenanalyse (Root Cause Analysis)

### 2.1 Entstehung der zwei Verzeichnisse
1. **`CodeForgeProjects` (Legacy)**:
   In einer früheren Version (NeonIDE Refactoring / frühe CodeForge-Builds) war `/storage/emulated/0/CodeForgeProjects` als Standard-Arbeitsbereich hinterlegt.
2. **`CodeForgeMobileProjects` (Neuer Name)**:
   Im Zuge der Spezifizierung für die mobile Variante wurde der Standardpfad auf `CodeForgeMobileProjects` umbenannt, um Verwechslungen mit Desktop-Entwicklungsumgebungen zu vermeiden.
3. **Inkonsistente Fallback-Pfade im Code**:
   In einigen Modulen existierten noch harte Fallback-Strings oder DataStore-Standardwerte, die auf das alte `CodeForgeProjects` verwiesen, wenn `workspace_directory` in `AppSettings` leer war.

---

## 3. Aktueller Standort aller Pfad-Referenzen

| Modul | Datei | Pfad-Referenz |
|---|---|---|
| `:app` | [`CodeForgeApplication.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/src/main/kotlin/com/codeforge/app/CodeForgeApplication.kt) | `/storage/emulated/0/CodeForgeMobileProjects` |
| `:feature:welcome` | [`WelcomeRoute.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/welcome/src/main/kotlin/com/codeforge/feature/welcome/WelcomeRoute.kt) | `Environment.getExternalStorageDirectory() / "CodeForgeMobileProjects"` |
| `:feature:settings` | [`EditorSettingsViewModel.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/settings/editor/EditorSettingsViewModel.kt) | `/storage/emulated/0/CodeForgeMobileProjects` |
| `:feature:git` | [`GitRoute.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/git/src/main/kotlin/com/codeforge/feature/git/GitRoute.kt) | `/storage/emulated/0/CodeForgeMobileProjects` |

---

## 4. Behebungsmaßnahme & Durchsetzung

1. **Vollständige Entfernung alter Pfadbezeichnungen**:
   Alle dynamischen Fallbacks in `:app`, `:feature:settings`, `:feature:welcome` und `:feature:git` wurden ausnahmslos auf `/storage/emulated/0/CodeForgeMobileProjects` vereinheitlicht.
2. **Automatische Migration bestehender Ordner**:
   Beim App-Start wird geprüft, ob noch alte Ordner unter `CodeForgeProjects` existieren, und der Nutzer wird auf `CodeForgeMobileProjects` geleitet.
3. **Keine Erstellung von `CodeForgeProjects`**:
   Es werden keine neuen Ordner namens `CodeForgeProjects` mehr durch CodeForge Mobile erstellt.

---

## 5. Zusammenfassung

Die doppelte Ordnererstellung stammte aus unvollständig migrierten Hardcoded-Strings in Altmodulen. Mit der Bereinigung ist `CodeForgeMobileProjects` der einzige offizielle Projektordner für CodeForge Mobile.
