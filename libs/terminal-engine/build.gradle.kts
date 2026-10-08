plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.terminal_engine"

    // HINWEIS: Der frühere externalNativeBuild-Block für die vendorte Termix-JNI-PTY-
    // Bridge (com.termux.terminal, src/main/jni/) wurde entfernt — dieser Code ist
    // gelöscht (siehe docs/sub/TERMUX-PORTING.md) und durch die Abhängigkeit auf
    // :libs:termux-emulator ersetzt, die ihre eigene native Bibliothek (libtermux.so)
    // mitbringt. Zwei Module mit derselben .so würden sonst beim APK-Packaging
    // kollidieren.
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))
    implementation(project(":core:datastore"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.annotation)

    // Termux-Vendoring: ersetzt die bisherige Termix-com.termux.terminal-Kopie in
    // diesem Modul als Backing für TerminalSessionRepositoryImpl. Siehe docs/sub/TERMUX-PORTING.md.
    implementation(project(":libs:termux-emulator"))
    implementation(project(":libs:termux-shared"))

    testImplementation(libs.junit)
}
