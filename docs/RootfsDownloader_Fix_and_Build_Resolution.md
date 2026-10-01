# Behebung des RootfsDownloader-Fehlers & APK Build

## Ursache des Build-Fehlers
Beim Ausführen von `./gradlew clean assembleDebug` schlug die Kompilierung fehl:

1. **Fehlende `RootfsDownloader`-Klasse**:
   `DistroBootstrapRepositoryImpl` und `JdkInstaller` instanziierten `RootfsDownloader()`, um RootFS-Archive und JDKs herunterzuladen und den Fortschritt (`Flow<Int>`) zu emittieren. Diese Datei existierte im Modul `:libs:terminal-engine` nicht.
2. **Sichtbarkeit (Public modifier)**:
   Da auch `:feature:composepreview` die Klasse `RootfsDownloader` nutzt, musste die Sichtbarkeit von `internal` auf `public` geändert werden, um Modulübergreifend verfügbar zu sein.

## Durchgeführte Behebung
1. **Erstellung von `RootfsDownloader.kt`**:
   - Pfad: [`libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/RootfsDownloader.kt`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/libs/terminal-engine/src/main/kotlin/com/codeforge/libs/terminal_engine/RootfsDownloader.kt)
   - Implementierung mit `HttpURLConnection`, automatischer Redirect-Verfolgung (`301/302/307/308`) und Fortschritts-Stream (`Flow<Int>`).
   - `class RootfsDownloader` ist als `public` deklariert.
2. **Bereinigung**:
   - Verwaiste Datei `assets/Downloader.kt` entfernt.

## Ergebnis & Build Status
- **Build Status**: `BUILD SUCCESSFUL`
- **Generierte APK**: [`app/build/outputs/apk/debug/app-debug.apk`](file:///data/data/com.termux/files/home/storage/shared/Download/CodeForgeMobile/app/build/outputs/apk/debug/app-debug.apk) (54 MB)
