# AGY Failure & Architecture Report: ProjectWizard Regression & Status Analysis

**Datum:** 2026-10-01  
**Komponente:** `:feature:projectwizard` / `:libs:template-engine`  
**Autor:** Antigravity AI (AGY)  
**Status:** Inaktiv / Nicht migriert (Legacy-Standort in `assets/projectwizard/`)  

---

## 1. Problemstellung

Der Benutzer hat festgestellt, dass sich das Modul `:feature:projectwizard` derzeit auf einem rudimentären Stand ("Basic") befindet, der lediglich Standard-Android-Aktivitätsvorlagen (`empty_activity`, `compose_activity`, `basic_activity`, `bottom_nav_activity`, etc.) anbietet. Der erweiterte **Neue ProjectWizard** (inklusive Multi-Framework-Unterstützung wie **Flutter**, plattformspezifischen Target-Toggles, erweiterten Architektur-Presets und individueller Modulstruktur) fehlt im aktiven App-Flow.

---

## 2. Ursachenanalyse (Root Cause Analysis)

Bei der Inspektion des Quellcodes und der Dateiverzeichnisse wurden folgende Hauptgründe identifiziert:

### 2.1 Auslagerung in `assets/projectwizard/` während der Refakturierung
Im Rahmen der Migration des Projekts von der früheren Alt-Architektur (`com.neonide.studio`) auf die neue Multi-Modul-Architektur (`com.codeforge.*`) wurden die erweiterten Wizard-Dateien temporär im Ordner [`assets/projectwizard/`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/) abgelegt:

- [`assets/projectwizard/CreateProjectScreen.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/CreateProjectScreen.kt) (451 Zeilen – Erweiterter Assistent)
- [`assets/projectwizard/FlutterProjectScreen.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/FlutterProjectScreen.kt) (295 Zeilen – Flutter-Assistent)
- [`assets/projectwizard/ProjectTemplateRegistry.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/ProjectTemplateRegistry.kt)
- [`assets/projectwizard/template/`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/template/)

### 2.2 Re-Implementation mit vereinfachtem Android-Fokus
Beim Neuaufbau des Moduls `:feature:projectwizard` in [`feature/projectwizard/src/main/kotlin/com/codeforge/feature/projectwizard/ProjectWizardRoute.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/feature/projectwizard/src/main/kotlin/com/codeforge/feature/projectwizard/ProjectWizardRoute.kt) wurde ein stark vereinfachtes Modell implementiert:

1. **Nur Android-Basis-Templates**: Basiert ausschließlich auf den Enums in `ProjectTemplate.Kind` (in `:libs:template-engine`).
2. **Fehlende Framework-Integration**: Unterstützung für **Flutter**, **Ktor**, **Kotlin Multiplatform (KMP)** oder reine Java/C++ Konsolenanwendungen ist im aktiven Code nicht angebunden.
3. **Paket- & UI-Komponenten-Inkompatibilität**: Die Dateien in `assets/projectwizard/` verweisen noch auf veraltete Pakete (`com.neonide.studio.*`) und alte UI-Wrapper (`AppColumn`, `AppTopBar`, `FormTextField`), die im neuen `:core:designsystem` bzw. Jetpack Compose Material 3 nicht mehr existieren.

---

## 3. Wo befindet sich der neue ProjectWizard aktuell?

| Komponente / Feature | Aktueller Zustand in `:feature:projectwizard` | Legacy-Code in `assets/projectwizard/` |
|---|---|---|
| **Android Studio Standard (Compose, Empty, Tabbed, NavDrawer)** | ✅ Vorhanden (via `:libs:template-engine`) | ⚠️ Veralteter Stand |
| **Flutter Project Wizard** | ❌ **Fehlt vollständig** | ✅ Vorhanden in [`FlutterProjectScreen.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/FlutterProjectScreen.kt) |
| **Multi-Platform Targets (Android, iOS, Web, Desktop)** | ❌ **Fehlt** | ✅ Vorhanden in UI-Formularen |
| **Erweiterte Template-Engine** | ⚠️ Basiert nur auf festen Kotlin-Generatoren | ✅ Vorhanden in `assets/projectwizard/template/` |

---

## 4. Handlungsanweisung & Wiederherstellungsplan (Action Plan)

Um den vollständigen **Neuen ProjectWizard** in CodeForge Mobile wiederherzustellen, müssen folgende Schritte durchgeführt werden:

1. **Portierung von `FlutterProjectScreen.kt`**:
   - Verschieben und Anpassung von Package `com.neonide.studio.projectwizard` zu `com.codeforge.feature.projectwizard.flutter`.
   - Umstellung von veralteten UI-Komponenten (`AppTopBar`, `FormTextField`) auf Jetpack Compose Material 3 (`TopAppBar`, `OutlinedTextField`).
2. **Erweiterung von `:libs:template-engine`**:
   - Hinzufügen von `FLUTTER_PROJECT`, `KMP_PROJECT` und `NATIVE_CPP_LIBRARY` zu `ProjectTemplate.Kind`.
   - Einbinden der Template-Generatoren aus `assets/projectwizard/template/` in `CreateTemplate.kt`.
3. **Integration in `ProjectWizardRoute.kt`**:
   - Ergänzung einer Kategorie-Auswahl im Step 1 (z. B. Tabs für *Android*, *Flutter*, *Cross-Platform*).
   - Verknüpfung der Routen und ViewModel-Events im Navigation Host (`CodeForgeNavHost.kt`).

---

## 5. Fazit

Der neue ProjectWizard ging nicht verloren, sondern wurde während der Modul-Restrukturierung in [`assets/projectwizard/`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/assets/projectwizard/) isoliert und aufgrund von API- und Package-Breakages nicht in das neue Gradle-Modul `:feature:projectwizard` überführt.
