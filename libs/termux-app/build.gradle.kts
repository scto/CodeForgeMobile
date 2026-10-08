/*
 * :libs:termux-app
 *
 * Vendorter Termux-"application"-Layer (com.termux.app → com.codeforge.app),
 * umgebaut zur reinen Library (kein eigenes <application>-Tag, kein
 * TermuxApplication als App-Einstiegspunkt — wird von :app konsumiert).
 * Quelle: scto/AndroidIDE (dev-Branch) termux/application, GPLv3 — siehe
 * docs/sub/LICENSE.termux und docs/sub/TERMUX-PORTING.md.
 *
 * WICHTIG: `TERMUX_PACKAGE_NAME` in TermuxConstants.java ist auf
 * "com.codeforge.app" gesetzt — das ist der tatsächliche `applicationId`
 * von :app (NICHT "com.codeforge"). Bootstrap-Binaries deines
 * `terminal-packages-codeforge`-Forks MÜSSEN exakt mit Präfix
 * `/data/data/com.codeforge.app/files/usr` gebaut werden. ACHTUNG: Die Standard-Pakete des
 * Plugins (AndroidIDE-Release 16.12.2023) sind auf `/data/data/com.itsaky.androidide/files/usr`
 * gebaut – für com.codeforge.app per `codeforgeBootstrapUrlTemplate` auf den eigenen Fork zeigen.
 *
 * Bootstrap-Einbettung: das Convention-Plugin `codeforge.terminal.bootstrap`
 * (build-logic/convention/TerminalBootstrapPackagesPlugin.kt) registriert den Task
 * `embedTerminalBootstrap` (hängt an `preBuild`): lädt die ABI-Zips (aarch64, arm, x86_64),
 * prüft SHA-256 und schreibt den Assembly-Blob nach `src/main/cpp/termux-bootstrap-zip.S`,
 * den `termux-bootstrap.c` (nativ einkompiliert) zur Laufzeit extrahiert (siehe
 * TermuxInstaller.java). Quelle/URL/Prüfsummen: Projekt-Properties
 * `codeforgeBootstrap*` (Details im Plugin-KDoc). Offline: `-PcodeforgeBootstrapSkip=true`.
 */

plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.terminal.bootstrap)
}

android {
    namespace = "com.codeforge.app"
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"apt-android-7\"")
        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.codeforge.app"
        manifestPlaceholders["TERMUX_APP_NAME"] = "CodeForge"

        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    lint.disable += "ProtectedPermissions"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging.jniLibs.useLegacyPackaging = true
}

dependencies {
    implementation(project(":libs:termux-shared"))
    implementation(project(":libs:termux-view"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.viewpager2)
    implementation(libs.google.material)
    implementation(libs.google.guava)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.recycler)
}
