plugins {
    id("codeforge.android.library")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.libs.terminal_engine"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:datastore"))

    // Tar/Gzip/XZ-Entpacken der Rootfs-Archive (Alpine=.tar.gz, Debian=.tar.xz)
    implementation(libs.commons.compress)
    implementation(libs.xz)

    // Für vendorte com.termux.terminal-Klassen (@NonNull/@Nullable)
    implementation(libs.androidx.annotation)
    api(files("libs/nyamux-terminal.jar"))
}
