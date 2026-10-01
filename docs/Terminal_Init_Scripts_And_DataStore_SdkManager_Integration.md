# Terminal Init Scripts & DataStore SdkManager Integration

## 1. Übersicht der Änderungen

Gemäß Benutzeranforderung wurden die Terminal-Initialisierungsskripte (`init-ubuntu.sh`, `init-debian.sh`) und die SDK-Manager-Architektur überarbeitet:

1. **OpenJDK 17 & Paket-Installation**:
   - `openjdk-17-jdk` und `gradle` werden während des Bootstrap-Prozesses in PRoot über `apt-get` installiert.

2. **$HOME/android-sdk Ordnerstruktur & Lizenzen**:
   - Erstellung des Verzeichnisses `$HOME/android-sdk` (`/root/android-sdk`) mit allen erforderlichen Unterordnern:
     - `cmdline-tools/latest`
     - `build-tools`
     - `platform-tools`
     - `ndk`
     - `cmake`
     - `licenses` (mit vorab akzeptierten Hash-Lizenzen).

3. **Umgebungsvariablen-Exporte**:
   - Folgende Variablen werden im Header der Init-Skripte sowie in `/root/.bashrc`, `/etc/profile.d/codeforge.sh` und `/etc/environment` exportiert:
     - `ANDROID_HOME=$HOME/android-sdk`
     - `ANDROID_SDK_ROOT=$HOME/android-sdk`
     - `ANDROID_SDK_HOME=$HOME/android-sdk`
     - `ANDROID_NDK_HOME=$ANDROID_HOME/ndk`
     - `CMAKE_HOME=$ANDROID_HOME/cmake`
     - `JAVA_HOME=/usr/lib/jvm/default-java`
     - `GRADLE_HOME=/usr/share/gradle`
     - `GRADLE_USER_HOME=$HOME/.gradle`
     - `PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$ANDROID_HOME/build-tools:$ANDROID_NDK_HOME:$CMAKE_HOME:$GRADLE_HOME/bin:...`

4. **Google Commandline-Tools & SDK-Tools Installation**:
   - Download & Entpacken von Google `cmdline-tools` nach `$HOME/android-sdk/cmdline-tools/latest`.
   - Ausführung von `sdkmanager` zur automatischen Installation von:
     - `build-tools;36.0.0`
     - `platform-tools`
     - `ndk;27.0.12077973` / `ndk;30`
     - `cmake;3.22.1` / `cmake;4.1.2`

5. **App-Startup DataStore Synchronisation**:
   - In `CodeForgeApplication.kt` und `CommandlineSdkRepository.kt` wurde `syncInstalledToolsToDataStore()` integriert.
   - Beim Start der App und beim Öffnen des `SdkManagerScreen` werden die installierten Tools auf der Festplatte gescannt und im Proto-DataStore gespeichert.
   - `SdkManagerScreen` zeigt die Werkzeuge (`build-tools;36.0.0`, `platform-tools`, `ndk`, `cmake`, `jdk;17`) sofort als **installiert** inkl. Pfad an.
