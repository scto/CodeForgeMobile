# Task 35 – APK-Analyse und APK-Vergleich

**Datum:** 20. Oktober 2026  
**Autor:** Antigravity  
**Status:** ✅ Erledigt  
**Module:** `:core:domain`, `:feature:build`, `:feature:filetree`, `:core:resources`, `:app`

---

## 1. Ziel und Zusammenfassung

Ziel von Task 35 ist ein umfassender APK-Analyzer direkt auf dem Gerät in CodeForge Mobile. Der Nutzer kann eine APK öffnen (entweder über das Kontextmenü des Dateibaums oder direkt aus der Build-Ausgabe nach erfolgreichem Bauen) und sieht:
* Eine genaue Größenaufschlüsselung nach Bereichen (`classes*.dex`, `res/`, `resources.arsc`, `lib/<abi>/`, `assets/`, `META-INF/`, `AndroidManifest.xml`, Sonstiges) mit gestapeltem Balkendiagramm und Prozentanteilen,
* Top-N größte Dateien und ausklappbare Dateiliste mit Such- und Sortierfunktionen,
* Aufschlüsselung nativer Bibliotheken je ABI mit automatischer Erkennung und Warnung bei Multi-ABI-APKs,
* Schutz vor Zip-Slip und beschädigten Archiven,
* Metadaten (Paketname, Version, Mindest-, Ziel- und Compile-SDK, Debuggable- und AllowBackup-Warnungen),
* Deklarierte Berechtigungen mit automatischer Kennzeichnung gefährlicher (Runtime-)Berechtigungen,
* Deklarierte App-Komponenten (Aktivitäten, Dienste, Empfänger, Provider) mit Zählung der exportierten Komponenten,
* Signaturprüfung (v1–v4 Schemas, Zertifikate und SHA-1/SHA-256 Fingerabdrücke),
* Einen vollständigen APK-Vergleichsmodus: Zwei APKs wählen und Deltas pro Bereich und pro Datei (neu, entfernt, größer, kleiner, unverändert) sowie die Gesamtdifferenz in Bytes und Prozent analysieren,
* Teilen-Funktion („Als Markdown teilen“) für formatierte Berichte via Android Share Sheet.

---

## 2. Unabhängigkeit von Zusatzwerkzeugen (`aapt` und `apksigner`)

Gemäß Aufgabenstellung muss die Analyse ohne Zusatzwerkzeuge auskommen; `aapt` und `apksigner` sind optional:

| Funktion | Ohne `aapt` / ohne `apksigner` | Mit `aapt` / mit `apksigner` |
|---|---|---|
| **Größenanalyse (35.1)** | ✅ **100% funktionsfähig:** Reines Kotlin/JVM via `ZipFile` (`ApkZipAnalyzer`). Vollständige Berechnung aller komprimierten/unkomprimierten Größen, Gruppierung nach Bereichen, Top-N, Multi-ABI-Erkennung. | Verwendet identische JVM-Größenanalyse. |
| **Zip-Slip- & Korruptionsschutz** | ✅ **100% funktionsfähig:** `validateZipSlip` prüft auf relative Pfadausbrüche (`..`) und führende Slashes; `ZipException` und `IOException` werden abgefangen. | Identisch. |
| **Metadaten (35.2)** | ✅ **100% funktionsfähig:** Zwei robuste Fallback-Ebenen: (1) Android `PackageManager.getPackageArchiveInfo` auf dem Gerät, (2) `AxmlManifestParser` liest das Binär-XML `AndroidManifest.xml` direkt aus dem Archiv (auch in JVM-Unit-Tests ohne Android-Context). | `aapt dump badging` und `aapt dump permissions` werden bevorzugt genutzt, wenn `aapt` oder `aapt2` auf dem System installiert ist. |
| **Berechtigungen** | ✅ **100% funktionsfähig:** Ausgelesen via `AxmlManifestParser` / `PackageManager`; Kennzeichnung gefährlicher Berechtigungen über `AndroidDangerousPermissions`. | Ergänzt um zusätzliche Details via `AaptPermissionsParser`. |
| **Komponenten & Flags** | ✅ **100% funktionsfähig:** Aktivitäten, Dienste, Empfänger, Provider, `FLAG_DEBUGGABLE` und `FLAG_ALLOW_BACKUP` werden ermittelt. | Identisch. |
| **Signaturprüfung** | ⚠️ **Freundlicher Hinweis:** Wenn `apksigner` nicht im System/SDK gefunden wird, blendet der Tab „Signatur“ eine informative Hinweiskarte ein (*„apksigner ist nicht verfügbar. Signatur-Details können nicht geprüft werden.“*). | ✅ `apksigner verify --verbose --print-certs` wird aufgerufen und über `ApksignerOutputParser` ausgewertet (v1–v4 Schemas, Inhaber, Fingerabdrücke). |
| **APK-Vergleich (35.3)** | ✅ **100% funktionsfähig:** Reines Kotlin/JVM (`ApkComparator`). | Identisch. |
| **Markdown-Export** | ✅ **100% funktionsfähig:** Reines Kotlin (`ApkReportFormatter`). | Identisch. |

---

## 3. Architektur und Komponenten

### 3.1 Domain-Schicht (`:core:domain`)
* **Modelle (`com.codeforge.core.domain.model`)**:
  * `ApkSection`: Enum für die 8 Bereiche (DEX, RES, ARSC, LIB, ASSETS, META_INF, MANIFEST, OTHER) mit `fromPath()`-Zuweisung.
  * `ApkEntryInfo`, `ApkSectionSummary`, `ApkAbiSummary`, `ApkPermission`, `ApkComponentCounts`, `ApkDetailedMetadata`, `ApkAnalysisResult`.
  * `ApkEntryDiffType`, `ApkEntryDiff`, `ApkSectionDiff`, `ApkComparisonResult`.
  * `ApkAnalysisException` (`FileNotFound`, `CorruptArchive`, `ZipSlipDetected`).
* **Analyzer & Logik (`com.codeforge.core.domain.analyzer`)**:
  * `ApkZipAnalyzer`: Reiner Kotlin/JVM-Analyzer über `ZipFile`. Ermittelt komprimierte und unkomprimierte Größen, sortiert Top-N, gruppiert nach Bereichen und ABIs, fängt Zip-Slip und korrupte Archive ab.
  * `ApkComparator`: Berechnet Deltas zwischen zwei APKs (Größendifferenz in Bytes und %, Sektionen-Delta, Datei-Differenzen).
  * `AndroidDangerousPermissions`: Erkennt gefährliche Runtime-Berechtigungen (Kamera, Standort, Kontakte, Speicher, Mikrofon etc.).
  * `ApkReportFormatter`: Erzeugt formatierte Markdown-Tabellen und -Zusammenfassungen für Einzelanalysen und Vergleiche.
* **Parser (`com.codeforge.core.domain.parser`)**:
  * `AaptPermissionsParser`: Parst Ausgaben von `aapt dump permissions` (mit Schutzstufen und maxSdkVersion).
  * `AaptBadgingParser`: Parst Ausgaben von `aapt dump badging` (Paketname, Version, SDKs, Komponenten, Debug/Backup-Flags).
  * `AxmlManifestParser`: Reiner Kotlin-Parser für Android Binary XML (`AndroidManifest.xml`) direkt aus Byte-Arrays.
* **Repository-Schnittstelle (`com.codeforge.core.domain.repository`)**:
  * `ApkAnalyzerRepository`: `analyzeApk(apkFile)` und `compareApks(baseFile, comparedFile)`.

### 3.2 Feature-Schicht (`:feature:build`)
* `ApkAnalyzerRepositoryImpl`:
  * Führt `ApkZipAnalyzer` auf `Dispatchers.IO` aus.
  * Erkennt optional `aapt` / `aapt2` im Termux-SDK (`$ANDROID_SDK_ROOT/build-tools/*/aapt`, `$PREFIX/bin/aapt`) oder PATH.
  * Fallback auf `PackageManager.getPackageArchiveInfo` bzw. `AxmlManifestParser`.
  * Erkennt optional `apksigner` und parst Signaturen mit `ApksignerOutputParser`.
* `ApkAnalyzerContract`: MVI-State `ApkAnalyzerUiState`, Events `ApkAnalyzerUiEvent`, Effekte `ApkAnalyzerUiEffect`.
* `ApkAnalyzerViewModel`: Hilt-ViewModel zur Steuerung von Analyse, Tabs, Such-/Kategorie-/Sortierfiltern, Vergleich und Markdown-Sharing.
* `ApkAnalyzerScreen`:
  * Zusammenfassung oben (Name, Pfad, komprimierte/unkomprimierte Größe, Paketname, Version, SDK-Badges).
  * Gestapelter Größenbalken mit Farbkodierung und Prozentwerten.
  * 6 Tabs:
    1. **Übersicht:** Zusammenfassung, Balkendiagramm, ABI-Aufschlüsselung (mit Warnung bei Multi-ABI), Top-Dateien.
    2. **Dateien:** Suchfeld, Filterchips (Alle, Top 10, DEX, RES, LIB, ASSETS, META-INF, Sonstiges), Sortieroptionen, ausklappbare Dateizeilen mit Kompressionsrate.
    3. **Berechtigungen:** Filterchips (Alle vs. Gefährliche), Danger-Badges, maxSdkVersion.
    4. **Komponenten:** Statistik-Karten für Aktivitäten, Dienste, Empfänger, Provider (exportiert vs. gesamt), SDK-Versionen, Debug/Backup-Flags.
    5. **Signatur:** Validierungsstatus, V1–V4-Schemas, Zertifikate mit SHA-1/SHA-256-Kopierbutton oder Information, falls `apksigner` fehlt.
    6. **Vergleich:** Dateiwähler für zweite APK, Gesamtdifferenz-Banner (+/- Bytes und %), Differenztabelle je Bereich, Dateiliste (neu, entfernt, größer, kleiner).
  * Markdown-Teilen über Action-Icon in der TopAppBar.
* `ApkAnalyzerRoute`: Host-Composable mit Navigation und Android Share Sheet (`ACTION_SEND`).
* `ApkInstallSection`: Schaltfläche „Analysieren“ (`Icons.Default.Analytics`) neben „Installieren“ für frisch gebaute APKs.

### 3.3 Integration & Navigation (`:feature:filetree`, `:app`)
* `FileTreeScreen` / `FileTreeViewModel`:
  * Kontextmenü auf `.apk`-Dateien bietet die neue Aktion **„APK analysieren“** (`Icons.Default.Analytics`).
  * Signalisiert `FileTreeUiEffect.OpenApkAnalyzer(path)`.
* `WorkspaceDrawer`:
  * Verbindet `FileTreeRoute.onOpenApkAnalyzer` mit `Routes.apkAnalyzer(path)`.
* `CodeForgeNavHost`:
  * Route `apk_analyzer?apkPath={apkPath}&comparePath={comparePath}` registriert.

---

## 4. Getestet

* **Automatisierte JVM-Unit-Tests (`:core:domain:testDebugUnitTest`):**
  * `ApkZipAnalyzerTest`:
    * `analyzeValidApkStructure`: Analyse einer programmgesteuert erzeugten ZIP/APK mit 11 Dateien; Verifikation von Dateigrößen, Sektionen-Gruppierung (DEX, RES, LIB, ARSC, MANIFEST), Top-N-Berechnung und Multi-ABI-Erkennung (`arm64-v8a` + `armeabi-v7a`).
    * `rejectsZipSlipAttempt`: Archiv mit `../../etc/passwd` wird mit `ApkAnalysisException.ZipSlipDetected` sicher abgewiesen.
    * `rejectsLeadingSlashZipSlip`: Archiv mit `/root/evil.sh` wird sicher abgewiesen.
    * `rejectsCorruptedArchive`: Beschädigte Byte-Folgen werden mit `ApkAnalysisException.CorruptArchive` abgewiesen.
    * `rejectsNonExistentFile`: Nicht existierende Dateien werfen `ApkAnalysisException.FileNotFound`.
  * `ApkComparatorTest`:
    * `compareTwoApksDeltaCalculation`: Vergleicht Basis- und Vergleichs-APK mit modifizierten, neuen, entfernten und unveränderten Dateien. Verifikation der Gesamtdifferenz, Sektionen-Deltas und Zähler (`addedCount`, `removedCount`, `changedCount`, `unchangedCount`).
  * `AaptPermissionsParserTest`:
    * `parsePermissionsFixture`: Parst reale `aapt dump permissions`-Ausgabe aus Test-Fixture (`fixtures/aapt_dump_permissions.txt`), verifiziert gefährliche Berechtigungen (Kamera, Standort, Kontakte, Audio), maxSdkVersion und Protection-Levels.
    * `parseEmptyOutputReturnsNullPackageAndEmptyList`.
  * `AaptBadgingParserTest`:
    * `parseBadgingFixture`: Parst reale `aapt dump badging`-Ausgabe aus Test-Fixture (`fixtures/aapt_dump_badging.txt`), prüft Paketname, Versionen, SDK-Stufen, Debuggable/AllowBackup-Erkennung und exportierte Komponenten.
  * `ApkReportFormatterTest`:
    * `formatBytesOutputsHumanReadableUnits`: Formatierung in B, KB, MB, GB.
    * `formatAnalysisToMarkdownGeneratesValidMarkdown`: Markdown-Erzeugung mit Tabellen und Abschnitten.
* **Automatisierte UI-Tests (`:feature:build:testDebugUnitTest`):**
  * `ApkAnalyzerViewModelTest`:
    * `initialStateIsDefault`: Prüft Standardwerte des ViewModels.
    * `loadApkSuccessUpdatesState`: Erfolgreiches Laden einer APK.
    * `loadApkFailureSetsErrorMessage`: Abfangen von Fehlern (Zip-Slip) und korrekte Fehlermeldungen.
    * `selectTabUpdatesActiveTab`: Tab-Wechsel.
    * `filterAndSortFiles`: Suchfilter, Kategorie-Filter und Sortierung der Dateiliste.
    * `compareWithApkSuccessUpdatesComparisonResult`: Ausführen des APK-Vergleichs.
    * `shareMarkdownReportEmitsEffect`: Emittieren des Share-Effekts.
* **Automatisierte FileTree-Tests (`:feature:filetree:testDebugUnitTest`):**
  * Erfolgreich durchgelaufen.
* **Gesamt-Build (`./gradlew :app:assembleDebug`):**
  * Erfolgreich abgeschlossen (`BUILD SUCCESSFUL in 2m 14s`).
* **String-Check (`python3 scripts/find_hardcoded_strings.py`):**
  * Keine neuen hardcoded Strings; alle UI-Texte vollständig in `:core:resources` (`values/strings.xml` und `values-de/strings.xml`) externalisiert.

---

## 5. Nicht getestet

* **Manuelle UI-Interaktion auf physischem Android-Zielgerät (Pixel 9 Pro XL, Android 17):**
  * In der aktuellen Umgebung stand kein physisches Android-Gerät zur Verfügung. Die Verifikation erfolgte über Unit-Tests, Komponenten-Tests und den vollen Gradle-Build.
* **Ausführung von nativem `aapt` / `apksigner` auf Android 17:**
  * Getestet wurden die Parser (`AaptBadgingParser`, `AaptPermissionsParser`, `ApksignerOutputParser`) mit echten Fixture-Dateien sowie die Fallbacks (`AxmlManifestParser`, `PackageManager`). Die Ausführung der echten nativen Termux-Binärdateien auf dem Host-Gerät wurde nicht zur Laufzeit ausgeführt.

---

## 6. Offene Punkte / Zukünftige Erweiterungen

* Optional: DEX-Methoden- und Feldzählung (`dexlib2`), falls in einer zukünftigen Version eine 64k-Multidex-Analyse gewünscht wird.
* Optional: Integrierter Resource-Table-Viewer (Anzeige von Strings und Layout-Preview aus der APK).
